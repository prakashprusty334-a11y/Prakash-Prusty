const crypto = require("node:crypto");

class ProviderNotConfiguredError extends Error {
  constructor(message) {
    super(message);
    this.name = "ProviderNotConfiguredError";
    this.statusCode = 503;
  }
}

class ProviderRouter {
  constructor({ config, store, fetchImpl = fetch }) {
    this.config = config;
    this.store = store;
    this.fetchImpl = fetchImpl;
    // Server-side volatile override map for admin credential updates in runtime without persisting secrets to Firestore
    this.runtimeSecrets = { ...config.providerSecrets };
  }

  setProviderCredential(secretEnvRef, secretValue) {
    if (!secretEnvRef || !secretValue) return;
    this.runtimeSecrets[secretEnvRef] = secretValue;
  }

  getCredentialForProvider(provider) {
    const ref = provider.secretEnvRef;
    if (!ref) return "";
    return this.runtimeSecrets[ref] || this.config.providerSecrets[ref] || "";
  }

  sanitizeProviderForAdmin(provider) {
    const credentialPresent = Boolean(this.getCredentialForProvider(provider));
    return {
      id: provider.id,
      name: provider.name,
      adapterType: provider.adapterType,
      baseUrl: provider.baseUrl || "",
      secretEnvRef: provider.secretEnvRef || "",
      hasServerCredential: credentialPresent,
      enabled: Boolean(provider.enabled),
      priority: Number(provider.priority || 1),
      fallbackProviderId: provider.fallbackProviderId || "",
      healthStatus: credentialPresent
        ? provider.enabled
          ? provider.healthStatus || "HEALTHY"
          : "DISABLED"
        : "UNCONFIGURED",
      totalRequests: Number(provider.totalRequests || 0),
      totalErrors: Number(provider.totalErrors || 0),
      lastError: provider.lastError || "",
      updatedAt: provider.updatedAt || null,
    };
  }

  async resolveModelAndProviders(requestedModelId, capability) {
    const models = await this.store.listDocs("ai_models");
    let model = models.find((m) => m.modelId === requestedModelId || m.id === requestedModelId);
    if (!model) {
      model = models.find((m) => m.capability === capability && m.isDefault && m.enabled);
    }
    if (!model || !model.enabled) {
      return { ok: false, status: 404, error: `Model '${requestedModelId}' is unavailable or disabled.` };
    }

    const primary = await this.store.getDoc("ai_providers", model.providerId);
    const fallbackId = model.fallbackProviderId || primary?.fallbackProviderId || "";
    const fallback = fallbackId ? await this.store.getDoc("ai_providers", fallbackId) : null;

    return { ok: true, model, primary, fallback };
  }

  async _recordProviderStats(providerId, success, errorMsg = "") {
    const current = await this.store.getDoc("ai_providers", providerId);
    if (!current) return;
    await this.store.setDoc("ai_providers", providerId, {
      ...current,
      totalRequests: Number(current.totalRequests || 0) + 1,
      totalErrors: Number(current.totalErrors || 0) + (success ? 0 : 1),
      healthStatus: success ? "HEALTHY" : "DEGRADED",
      lastError: success ? "" : String(errorMsg).slice(0, 400),
      updatedAt: new Date().toISOString(),
    });
  }

  async executeWithFallback({ capability, requestedModelId, payload }) {
    const resolved = await this.resolveModelAndProviders(requestedModelId, capability);
    if (!resolved.ok) {
      const err = new Error(resolved.error);
      err.statusCode = resolved.status;
      throw err;
    }
    const { model, primary, fallback } = resolved;
    const chain = [primary, fallback].filter((p) => p && p.enabled);

    if (chain.length === 0) {
      throw new ProviderNotConfiguredError(
        `No enabled AI provider is configured for model '${model.modelId}' (${capability}).`
      );
    }

    let lastErr = null;
    for (const provider of chain) {
      try {
        const result = await this._invokeAdapter(provider, model, capability, payload);
        await this._recordProviderStats(provider.id, true);
        return {
          ...result,
          modelId: model.modelId,
          suppliedBy: model.suppliedBy,
          providerId: provider.id,
          creditCost: model.creditCost,
        };
      } catch (err) {
        lastErr = err;
        await this._recordProviderStats(provider.id, false, err.message);
      }
    }

    throw lastErr || new ProviderNotConfiguredError("All configured providers failed.");
  }

  async _invokeAdapter(provider, model, capability, payload) {
    const apiKey = this.getCredentialForProvider(provider);
    if (!apiKey) {
      throw new ProviderNotConfiguredError(
        `Provider '${provider.name}' is missing server-side credential (${provider.secretEnvRef || "env secret"}).`
      );
    }

    switch (provider.adapterType) {
      case "GEMINI":
        return this._invokeGemini(provider, model, capability, payload, apiKey);
      case "OPENAI_COMPATIBLE":
      case "SELF_HOSTED":
        return this._invokeOpenAiCompat(provider, model, capability, payload, apiKey);
      case "IMAGE":
      case "VIDEO":
      case "SPEECH":
      case "AUDIO_SFX":
        return this._invokeExternalMediaProvider(provider, model, capability, payload, apiKey);
      default:
        throw new ProviderNotConfiguredError(`Unsupported adapter type: ${provider.adapterType}`);
    }
  }

  async _invokeGemini(provider, model, capability, payload, apiKey) {
    const base = (provider.baseUrl || "https://generativelanguage.googleapis.com").replace(/\/$/, "");
    const endpoint = `${base}/v1beta/models/${encodeURIComponent(model.modelId)}:generateContent`;

    if (capability === "CHAT") {
      const messages = Array.isArray(payload.messages) && payload.messages.length > 0
        ? payload.messages
        : [{ role: "user", content: String(payload.prompt || "") }];

      const customSystemParts = messages
        .filter((m) => m.role === "system" && m.content)
        .map((m) => String(m.content));

      const defaultSystemPrompt =
        'You are CEREVIX AI, the intelligent assistant of the CEREVIX AI platform ("One Platform. Infinite Intelligence."). ' +
        'When asked who you are or who created you, always introduce yourself as CEREVIX AI from the CEREVIX AI platform, never as a large language model trained by Google. ' +
        "Your primary default language is English, and you must always detect and reply in the same language or script the user writes in (such as English, Hindi, Hinglish, etc.) unless asked otherwise.";

      const systemText = [defaultSystemPrompt, ...customSystemParts].join("\n\n");

      const nonSystemMessages = messages.filter((m) => m.role !== "system");
      const contents = (nonSystemMessages.length > 0 ? nonSystemMessages : messages).map((m) => ({
        role: m.role === "assistant" || m.role === "model" ? "model" : "user",
        parts: [{ text: String(m.content || "") }],
      }));

      const resp = await this.fetchImpl(endpoint, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-goog-api-key": apiKey,
        },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: systemText }] },
          contents,
          generationConfig: {
            temperature: payload.temperature ?? 0.7,
            maxOutputTokens: payload.max_tokens ?? 2048,
          },
        }),
      });

      const data = await resp.json();
      if (!resp.ok) {
        const err = new Error(data?.error?.message || `Gemini API error ${resp.status}`);
        err.statusCode = resp.status;
        throw err;
      }

      const text =
        data?.candidates?.[0]?.content?.parts?.map((p) => p.text || "").join("") || "";
      const usage = data?.usageMetadata || {};
      return {
        output: {
          id: `chatcmpl_${crypto.randomBytes(8).toString("hex")}`,
          object: "chat.completion",
          model: model.modelId,
          supplied_by: model.suppliedBy,
          choices: [
            {
              index: 0,
              message: { role: "assistant", content: text },
              finish_reason: data?.candidates?.[0]?.finishReason || "STOP",
            },
          ],
          usage: {
            prompt_tokens: usage.promptTokenCount || 0,
            completion_tokens: usage.candidatesTokenCount || 0,
            total_tokens: usage.totalTokenCount || 0,
          },
        },
        tokensUsed: usage.totalTokenCount || 0,
      };
    }

    if (capability === "IMAGE") {
      const resp = await this.fetchImpl(endpoint, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "x-goog-api-key": apiKey,
        },
        body: JSON.stringify({
          contents: [{ role: "user", parts: [{ text: String(payload.prompt || "") }] }],
          generationConfig: { responseModalities: ["IMAGE", "TEXT"] },
        }),
      });
      const data = await resp.json();
      if (!resp.ok) {
        const err = new Error(data?.error?.message || `Gemini Image error ${resp.status}`);
        err.statusCode = resp.status;
        throw err;
      }
      const parts = data?.candidates?.[0]?.content?.parts || [];
      const imgPart = parts.find((p) => p.inlineData?.data);
      if (!imgPart) {
        throw new Error("Gemini image response contained no inline image payload.");
      }
      return {
        output: {
          created: Math.floor(Date.now() / 1000),
          model: model.modelId,
          supplied_by: model.suppliedBy,
          data: [
            {
              mime_type: imgPart.inlineData.mimeType || "image/png",
              b64_json: imgPart.inlineData.data,
            },
          ],
        },
        tokensUsed: 0,
      };
    }

    throw new ProviderNotConfiguredError(`Gemini adapter does not support capability '${capability}'.`);
  }

  async _invokeOpenAiCompat(provider, model, capability, payload, apiKey) {
    if (!provider.baseUrl) {
      throw new ProviderNotConfiguredError(`Provider '${provider.name}' is missing baseUrl.`);
    }
    const base = provider.baseUrl.replace(/\/$/, "");
    const targetUrl =
      capability === "IMAGE" ? `${base}/images/generations` : `${base}/chat/completions`;

    const resp = await this.fetchImpl(targetUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify({ ...payload, model: model.modelId }),
    });
    const data = await resp.json();
    if (!resp.ok) {
      const err = new Error(data?.error?.message || `Upstream provider error ${resp.status}`);
      err.statusCode = resp.status;
      throw err;
    }
    return {
      output: { ...data, supplied_by: model.suppliedBy },
      tokensUsed: data?.usage?.total_tokens || 0,
    };
  }

  async _invokeExternalMediaProvider(provider, model, capability, payload, apiKey) {
    if (!provider.baseUrl) {
      throw new ProviderNotConfiguredError(
        `Provider '${provider.name}' endpoint URL is not configured in Admin Provider Console.`
      );
    }
    const resp = await this.fetchImpl(provider.baseUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify({ model: model.modelId, capability, ...payload }),
    });
    const data = await resp.json();
    if (!resp.ok) {
      const err = new Error(data?.error?.message || `Media provider error ${resp.status}`);
      err.statusCode = resp.status;
      throw err;
    }
    return {
      output: { ...data, supplied_by: model.suppliedBy },
      tokensUsed: 0,
    };
  }
}

module.exports = { ProviderRouter, ProviderNotConfiguredError };
