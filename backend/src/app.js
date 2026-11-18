const http = require("node:http");
const crypto = require("node:crypto");
const {
  createConfig,
  DEFAULT_SUBSCRIPTION_PLANS,
  DEFAULT_CREDIT_PACKS,
  DEFAULT_PROVIDERS,
  DEFAULT_MODELS,
} = require("./config");
const { createDataStore } = require("./store/dataStore");
const { ApiKeyService } = require("./security/apiKeys");
const { FirebaseAuthVerifier } = require("./auth/firebaseAuth");
const { ProviderRouter } = require("./providers/providerAdapters");
const { RazorpayService } = require("./billing/razorpayService");
const { WalletService } = require("./wallet/walletService");

async function seedDefaultsIfEmpty(store) {
  const now = new Date().toISOString();
  const plans = await store.listDocs("subscription_plans");
  if (plans.length === 0) {
    for (const p of DEFAULT_SUBSCRIPTION_PLANS) {
      await store.setDoc("subscription_plans", p.id, { ...p, updatedAt: now });
    }
  }
  const providers = await store.listDocs("ai_providers");
  if (providers.length === 0) {
    for (const prov of DEFAULT_PROVIDERS) {
      await store.setDoc("ai_providers", prov.id, { ...prov, updatedAt: now });
    }
  }
  const models = await store.listDocs("ai_models");
  if (models.length === 0) {
    for (const m of DEFAULT_MODELS) {
      await store.setDoc("ai_models", m.id, { ...m, updatedAt: now });
    }
  }
}

function readRawBody(req, maxBytes = 2 * 1024 * 1024) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > maxBytes) {
        reject(Object.assign(new Error("Payload too large"), { statusCode: 413 }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    req.on("error", reject);
  });
}

function sendJson(res, status, data, extraHeaders = {}) {
  const body = JSON.stringify(data);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Cerevix-Api-Key",
    "Access-Control-Allow-Methods": "GET, POST, PATCH, DELETE, OPTIONS",
    "X-Content-Type-Options": "nosniff",
    ...extraHeaders,
  });
  res.end(body);
}

function sendHtml(res, status, html) {
  res.writeHead(status, {
    "Content-Type": "text/html; charset=utf-8",
    "X-Content-Type-Options": "nosniff",
  });
  res.end(html);
}

function buildCheckoutHtml({ keyId, subscriptionId, orderId, appName = "CEREVIX AI" }) {
  const modeParam = subscriptionId
    ? `subscription_id: ${JSON.stringify(subscriptionId)}`
    : `order_id: ${JSON.stringify(orderId)}`;
  return `<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${appName} Secure Checkout</title>
<script src="https://checkout.razorpay.com/v1/checkout.js"></script>
<style>body{background:#030917;color:#f1f5f9;font-family:system-ui,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;padding:16px}
.box{max-width:440px;width:100%;background:#0b132b;border:1px solid #1e3a5f;border-radius:20px;padding:24px;text-align:center}
button{background:#2563eb;color:#fff;border:0;border-radius:12px;padding:14px 20px;font-size:16px;font-weight:600;cursor:pointer;width:100%}
code{display:block;word-break:break-all;background:#030917;padding:10px;border-radius:8px;margin-top:12px;color:#38bdf8;font-size:12px}</style>
</head><body><div class="box">
<h2>${appName} Checkout</h2>
<p id="status">Tap below to open Razorpay UPI / Cards Checkout.</p>
<button id="payBtn" onclick="openPay()">Pay Securely via Razorpay</button>
<div id="result"></div>
<script>
function openPay(){
  var rzp = new Razorpay({
    key: ${JSON.stringify(keyId)},
    ${modeParam},
    name: ${JSON.stringify(appName)},
    description: "CEREVIX AI Subscription / Credits",
    theme: { color: "#2563EB" },
    handler: function(response){
      document.getElementById("status").innerText = "Payment submitted! Copy or return to CEREVIX AI app to verify.";
      document.getElementById("result").innerHTML = "<code>" + JSON.stringify(response) + "</code>";
    }
  });
  rzp.open();
}
</script></div></body></html>`;
}

function createCerevixBackend(deps = {}) {
  const config = deps.config || createConfig();
  const store = deps.store || createDataStore(config);
  const apiKeyService = deps.apiKeyService || new ApiKeyService(config.cerevixApiSecret);
  const authVerifier =
    deps.authVerifier ||
    new FirebaseAuthVerifier({
      projectId: config.firebaseProjectId,
      adminEmails: config.adminEmails,
      customTokenVerifier: deps.customTokenVerifier || null,
    });
  const providerRouter =
    deps.providerRouter ||
    new ProviderRouter({ config, store, fetchImpl: deps.fetchImpl || fetch });
  const razorpayService =
    deps.razorpayService ||
    new RazorpayService({ config, store, fetchImpl: deps.fetchImpl || fetch });
  const walletService = deps.walletService || new WalletService(store);

  const readyPromise = seedDefaultsIfEmpty(store);

  async function requireFirebaseUser(req) {
    const authHeader = req.headers.authorization || "";
    if (!authHeader.startsWith("Bearer ")) {
      const err = new Error("Missing Bearer Firebase ID token.");
      err.statusCode = 401;
      throw err;
    }
    const token = authHeader.slice(7).trim();
    return authVerifier.verifyIdToken(token);
  }

  async function requireAdminUser(req) {
    const user = await requireFirebaseUser(req);
    const ok = await authVerifier.isVerifiedAdmin(store, user);
    if (!ok) {
      const err = new Error("Owner/Admin authorization required.");
      err.statusCode = 403;
      throw err;
    }
    return user;
  }

  async function requireApiKey(req) {
    const rawKey =
      req.headers["x-cerevix-api-key"] ||
      (req.headers.authorization?.startsWith("Bearer ")
        ? req.headers.authorization.slice(7).trim()
        : "");
    const auth = await apiKeyService.authenticateKey(store, rawKey);
    if (!auth.ok) {
      const err = new Error(auth.error);
      err.statusCode = auth.status;
      throw err;
    }
    const rate = apiKeyService.checkRateLimit(auth.keyRecord.id, auth.keyRecord.rateLimitRpm);
    if (!rate.allowed) {
      const err = new Error(`Rate limit exceeded (${rate.limit} requests/min).`);
      err.statusCode = 429;
      err.headers = {
        "X-RateLimit-Limit": String(rate.limit),
        "X-RateLimit-Remaining": "0",
        "X-RateLimit-Reset": String(rate.resetSeconds),
        "Retry-After": String(rate.resetSeconds),
      };
      throw err;
    }
    return {
      keyRecord: auth.keyRecord,
      rateHeaders: {
        "X-RateLimit-Limit": String(rate.limit),
        "X-RateLimit-Remaining": String(rate.remaining),
        "X-RateLimit-Reset": String(rate.resetSeconds),
      },
    };
  }

  async function logApiRequest({ userId, keyId, endpoint, method, modelId, providerId, statusCode, latencyMs, creditsCharged, tokensUsed = 0, errorMessage = "" }) {
    const now = new Date().toISOString();
    const reqId = `req_${crypto.randomBytes(8).toString("hex")}`;
    await store.setDoc("api_requests", reqId, {
      id: reqId,
      userId,
      keyId,
      endpoint,
      method,
      modelId: modelId || "",
      providerId: providerId || "",
      statusCode,
      latencyMs,
      creditsCharged,
      tokensUsed,
      errorMessage: String(errorMessage || "").slice(0, 300),
      createdAt: now,
    });
    if (keyId) {
      const k = await store.getDoc("api_keys", keyId);
      if (k) {
        await store.updateDoc("api_keys", keyId, {
          requestCount: Number(k.requestCount || 0) + 1,
          lastUsedAt: now,
        });
      }
    }
    if (statusCode < 400 && creditsCharged > 0) {
      const useId = `use_${crypto.randomBytes(8).toString("hex")}`;
      await store.setDoc("ai_usage", useId, {
        id: useId,
        userId,
        modelId: modelId || "",
        providerId: providerId || "",
        capability: endpoint,
        creditsUsed: creditsCharged,
        tokensUsed,
        createdAt: now,
      });
    }
  }

  async function handleGatewayAiRequest({ req, res, endpoint, capability, body }) {
    const startMs = Date.now();
    const { keyRecord, rateHeaders } = await requireApiKey(req);
    const requestedModel = body.model || "";
    const resolved = await providerRouter.resolveModelAndProviders(requestedModel, capability);
    if (!resolved.ok) {
      await logApiRequest({
        userId: keyRecord.userId,
        keyId: keyRecord.id,
        endpoint,
        method: "POST",
        modelId: requestedModel,
        statusCode: resolved.status,
        latencyMs: Date.now() - startMs,
        creditsCharged: 0,
        errorMessage: resolved.error,
      });
      return sendJson(res, resolved.status, { error: resolved.error }, rateHeaders);
    }

    const cost = Number(resolved.model.creditCost || 1);
    const { newBalance } = await walletService.reserveAndDeductCredits({
      uid: keyRecord.userId,
      cost,
      toolId: `api_${capability.toLowerCase()}`,
      description: `CEREVIX API ${endpoint} (${resolved.model.modelId})`,
    });

    try {
      const result = await providerRouter.executeWithFallback({
        capability,
        requestedModelId: resolved.model.modelId,
        payload: body,
      });
      await logApiRequest({
        userId: keyRecord.userId,
        keyId: keyRecord.id,
        endpoint,
        method: "POST",
        modelId: result.modelId,
        providerId: result.providerId,
        statusCode: 200,
        latencyMs: Date.now() - startMs,
        creditsCharged: cost,
        tokensUsed: result.tokensUsed || 0,
      });
      return sendJson(
        res,
        200,
        {
          ...result.output,
          cerevix_billing: {
            credits_charged: cost,
            credits_remaining: newBalance,
            provider_id: result.providerId,
          },
        },
        rateHeaders
      );
    } catch (err) {
      // Automatic refund on failed AI generation
      await walletService.refundCredits({
        uid: keyRecord.userId,
        amount: cost,
        toolId: `refund_${capability.toLowerCase()}`,
        description: `Refund for failed ${endpoint}: ${err.message}`,
      });
      const status = err.statusCode || 502;
      await logApiRequest({
        userId: keyRecord.userId,
        keyId: keyRecord.id,
        endpoint,
        method: "POST",
        modelId: resolved.model.modelId,
        providerId: resolved.primary?.id || "",
        statusCode: status,
        latencyMs: Date.now() - startMs,
        creditsCharged: 0,
        errorMessage: err.message,
      });
      return sendJson(
        res,
        status,
        { error: err.message, credits_refunded: cost },
        rateHeaders
      );
    }
  }

  const server = http.createServer(async (req, res) => {
    await readyPromise;

    if (req.method === "OPTIONS") {
      res.writeHead(204, {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Cerevix-Api-Key",
        "Access-Control-Allow-Methods": "GET, POST, PATCH, DELETE, OPTIONS",
      });
      return res.end();
    }

    const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
    const pathname = url.pathname;

    try {
      // --- HEALTH & DOCS ---
      if (req.method === "GET" && pathname === "/health") {
        const providers = await store.listDocs("ai_providers");
        return sendJson(res, 200, {
          status: "ok",
          service: "cerevix-ai-gateway",
          environment: config.nodeEnv,
          razorpayConfigured: razorpayService.isConfigured(),
          webhookSecretConfigured: razorpayService.isWebhookConfigured(),
          providersActive: providers.filter((p) => p.enabled).length,
          timestamp: new Date().toISOString(),
        });
      }

      if (req.method === "GET" && pathname === "/v1/docs") {
        const models = await store.listDocs("ai_models");
        return sendJson(res, 200, {
          baseUrl: config.apiBaseUrl,
          authentication: "Authorization: Bearer crvx_live_... or X-Cerevix-Api-Key",
          endpoints: [
            "POST /v1/chat/completions",
            "POST /v1/images/generations",
            "POST /v1/videos/generations",
            "POST /v1/audio/speech",
            "POST /v1/audio/sfx",
            "GET /v1/models",
            "GET /v1/usage",
            "GET /v1/credits",
            "GET /v1/jobs/{id}",
          ],
          models: models.filter((m) => m.enabled),
        });
      }

      // --- EXTERNAL DEVELOPER GATEWAY API (/v1/* authenticated by CEREVIX API KEY) ---
      if (req.method === "GET" && pathname === "/v1/models") {
        const { rateHeaders } = await requireApiKey(req);
        const models = (await store.listDocs("ai_models")).filter((m) => m.enabled);
        return sendJson(res, 200, { object: "list", data: models }, rateHeaders);
      }

      if (req.method === "GET" && pathname === "/v1/credits") {
        const { keyRecord, rateHeaders } = await requireApiKey(req);
        const wallet = await walletService.ensureWallet(keyRecord.userId);
        return sendJson(res, 200, { wallet }, rateHeaders);
      }

      if (req.method === "GET" && pathname === "/v1/usage") {
        const { keyRecord, rateHeaders } = await requireApiKey(req);
        const requests = await store.queryDocs("api_requests", (r) => r.userId === keyRecord.userId);
        return sendJson(
          res,
          200,
          {
            totalRequests: requests.length,
            totalCreditsCharged: requests.reduce((sum, r) => sum + Number(r.creditsCharged || 0), 0),
            requests: requests.slice(-50).reverse(),
          },
          rateHeaders
        );
      }

      if (req.method === "GET" && pathname.startsWith("/v1/jobs/")) {
        const { keyRecord, rateHeaders } = await requireApiKey(req);
        const jobId = decodeURIComponent(pathname.slice("/v1/jobs/".length));
        const job = await store.getDoc("generation_jobs", jobId);
        if (!job || job.userId !== keyRecord.userId) {
          return sendJson(res, 404, { error: "Generation job not found" }, rateHeaders);
        }
        return sendJson(res, 200, { job }, rateHeaders);
      }

      if (req.method === "POST" && pathname === "/v1/chat/completions") {
        const body = JSON.parse((await readRawBody(req)) || "{}");
        return await handleGatewayAiRequest({
          req,
          res,
          endpoint: "/v1/chat/completions",
          capability: "CHAT",
          body,
        });
      }

      if (req.method === "POST" && pathname === "/v1/images/generations") {
        const body = JSON.parse((await readRawBody(req)) || "{}");
        return await handleGatewayAiRequest({
          req,
          res,
          endpoint: "/v1/images/generations",
          capability: "IMAGE",
          body,
        });
      }

      if (req.method === "POST" && pathname === "/v1/audio/speech") {
        const body = JSON.parse((await readRawBody(req)) || "{}");
        return await handleGatewayAiRequest({
          req,
          res,
          endpoint: "/v1/audio/speech",
          capability: "SPEECH",
          body,
        });
      }

      if (req.method === "POST" && pathname === "/v1/audio/sfx") {
        const body = JSON.parse((await readRawBody(req)) || "{}");
        return await handleGatewayAiRequest({
          req,
          res,
          endpoint: "/v1/audio/sfx",
          capability: "SFX",
          body,
        });
      }

      if (req.method === "POST" && pathname === "/v1/videos/generations") {
        const body = JSON.parse((await readRawBody(req)) || "{}");
        const { keyRecord, rateHeaders } = await requireApiKey(req);
        const jobId = `job_${crypto.randomBytes(10).toString("hex")}`;
        const now = new Date().toISOString();
        const resolved = await providerRouter.resolveModelAndProviders(
          body.model || "cerevix-video-v1",
          "VIDEO"
        );
        const cost = Number(resolved.model?.creditCost || 25);
        await walletService.reserveAndDeductCredits({
          uid: keyRecord.userId,
          cost,
          toolId: "api_video_generation",
          description: `Video generation job ${jobId}`,
        });
        try {
          const result = await providerRouter.executeWithFallback({
            capability: "VIDEO",
            requestedModelId: body.model || "cerevix-video-v1",
            payload: body,
          });
          const job = {
            id: jobId,
            userId: keyRecord.userId,
            jobType: "VIDEO",
            modelId: result.modelId,
            providerId: result.providerId,
            status: "COMPLETED",
            prompt: String(body.prompt || "").slice(0, 500),
            resultUrl: result.output?.url || "",
            errorMessage: "",
            creditsReserved: cost,
            creditsRefunded: false,
            createdAt: now,
            updatedAt: now,
          };
          await store.setDoc("generation_jobs", jobId, job);
          return sendJson(res, 202, { job, output: result.output }, rateHeaders);
        } catch (err) {
          await walletService.refundCredits({
            uid: keyRecord.userId,
            amount: cost,
            toolId: "refund_video",
            description: `Refund for failed video job ${jobId}: ${err.message}`,
          });
          await store.setDoc("generation_jobs", jobId, {
            id: jobId,
            userId: keyRecord.userId,
            jobType: "VIDEO",
            modelId: body.model || "cerevix-video-v1",
            providerId: resolved.primary?.id || "video_external",
            status: "FAILED",
            prompt: String(body.prompt || "").slice(0, 500),
            resultUrl: "",
            errorMessage: err.message,
            creditsReserved: cost,
            creditsRefunded: true,
            createdAt: now,
            updatedAt: now,
          });
          return sendJson(
            res,
            err.statusCode || 503,
            { error: err.message, jobId, credits_refunded: cost },
            rateHeaders
          );
        }
      }

      // --- DEVELOPER PORTAL ROUTES (Authenticated by Firebase ID Token) ---
      if (req.method === "POST" && pathname === "/v1/developer/register") {
        const user = await requireFirebaseUser(req);
        const body = JSON.parse((await readRawBody(req)) || "{}");
        const existing = await store.getDoc("developers", user.uid);
        if (existing) {
          return sendJson(res, 200, { developer: existing });
        }
        const now = new Date().toISOString();
        const wallet = await walletService.ensureWallet(user.uid);
        const dev = {
          id: user.uid,
          userId: user.uid,
          email: user.email,
          organizationName: String(body.organizationName || user.name || "Independent Developer").slice(0, 100),
          developerPlanId: "FREE_DEVELOPER",
          status: "ACTIVE",
          rateLimitRpm: 10,
          monthlyApiQuota: 1000,
          apiCreditsBalance: wallet.balance,
          totalApiRequests: 0,
          createdAt: now,
          updatedAt: now,
        };
        await store.setDoc("developers", user.uid, dev);
        return sendJson(res, 201, { developer: dev });
      }

      if (req.method === "GET" && pathname === "/v1/developer/profile") {
        const user = await requireFirebaseUser(req);
        const dev = await store.getDoc("developers", user.uid);
        const wallet = await walletService.ensureWallet(user.uid);
        return sendJson(res, 200, { developer: dev, wallet });
      }

      if (req.method === "GET" && pathname === "/v1/developer/keys") {
        const user = await requireFirebaseUser(req);
        const keys = await store.queryDocs("api_keys", (k) => k.userId === user.uid);
        return sendJson(res, 200, {
          keys: keys.map((k) => apiKeyService.sanitizeKeyRecord(k)),
        });
      }

      if (req.method === "POST" && pathname === "/v1/developer/keys") {
        const user = await requireFirebaseUser(req);
        const body = JSON.parse((await readRawBody(req)) || "{}");
        let dev = await store.getDoc("developers", user.uid);
        if (!dev) {
          const now = new Date().toISOString();
          const wallet = await walletService.ensureWallet(user.uid);
          dev = {
            id: user.uid,
            userId: user.uid,
            email: user.email,
            organizationName: user.name || "Developer Workspace",
            developerPlanId: "FREE_DEVELOPER",
            status: "ACTIVE",
            rateLimitRpm: 10,
            monthlyApiQuota: 1000,
            apiCreditsBalance: wallet.balance,
            totalApiRequests: 0,
            createdAt: now,
            updatedAt: now,
          };
          await store.setDoc("developers", user.uid, dev);
        }
        const created = await apiKeyService.createKey(store, {
          userId: user.uid,
          developerId: dev.id,
          name: body.name || "Production Key",
          scopes: Array.isArray(body.scopes) ? body.scopes : ["chat", "images", "audio", "video"],
          rateLimitRpm: dev.rateLimitRpm || 10,
        });
        return sendJson(res, 201, created);
      }

      if (req.method === "POST" && /^\/v1\/developer\/keys\/[^/]+\/revoke$/.test(pathname)) {
        const user = await requireFirebaseUser(req);
        const keyId = decodeURIComponent(pathname.split("/")[4]);
        const result = await apiKeyService.revokeKey(store, { keyId, requesterUid: user.uid });
        return sendJson(res, result.ok ? 200 : result.status, result.ok ? result : { error: result.error });
      }

      if (req.method === "POST" && /^\/v1\/developer\/keys\/[^/]+\/regenerate$/.test(pathname)) {
        const user = await requireFirebaseUser(req);
        const keyId = decodeURIComponent(pathname.split("/")[4]);
        const result = await apiKeyService.regenerateKey(store, { keyId, requesterUid: user.uid });
        if (result.ok === false) {
          return sendJson(res, result.status, { error: result.error });
        }
        return sendJson(res, 200, result);
      }

      if (req.method === "GET" && pathname === "/v1/developer/analytics") {
        const user = await requireFirebaseUser(req);
        const reqs = await store.queryDocs("api_requests", (r) => r.userId === user.uid);
        const keys = await store.queryDocs("api_keys", (k) => k.userId === user.uid);
        const errors = reqs.filter((r) => r.statusCode >= 400).length;
        const avgLatencyMs =
          reqs.length > 0 ? Math.round(reqs.reduce((s, r) => s + Number(r.latencyMs || 0), 0) / reqs.length) : 0;
        return sendJson(res, 200, {
          totalRequests: reqs.length,
          errorCount: errors,
          activeKeysCount: keys.filter((k) => k.status === "ACTIVE").length,
          avgLatencyMs,
          totalCreditsUsed: reqs.reduce((s, r) => s + Number(r.creditsCharged || 0), 0),
          recentRequests: reqs.slice(-30).reverse(),
        });
      }

      // --- BILLING & RAZORPAY SUBSCRIPTIONS ---
      if (req.method === "GET" && pathname === "/v1/billing/plans") {
        const plans = (await store.listDocs("subscription_plans"))
          .filter((p) => p.active)
          .sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0));
        return sendJson(res, 200, { plans, creditPacks: DEFAULT_CREDIT_PACKS });
      }

      if (req.method === "GET" && pathname === "/v1/billing/summary") {
        const user = await requireFirebaseUser(req);
        const wallet = await walletService.ensureWallet(user.uid);
        const subs = (await store.queryDocs("subscriptions", (s) => s.userId === user.uid)).sort(
          (a, b) => String(b.updatedAt).localeCompare(String(a.updatedAt))
        );
        const payments = (await store.queryDocs("payments", (p) => p.userId === user.uid)).sort(
          (a, b) => String(b.createdAt).localeCompare(String(a.createdAt))
        );
        const txs = (await store.queryDocs("credit_transactions", (t) => t.userId === user.uid)).sort(
          (a, b) => String(b.createdAt).localeCompare(String(a.createdAt))
        );
        return sendJson(res, 200, {
          wallet,
          activeSubscription: subs.find((s) => s.status === "ACTIVE") || subs[0] || null,
          subscriptions: subs,
          payments,
          creditTransactions: txs.slice(0, 50),
          razorpayConfigured: razorpayService.isConfigured(),
        });
      }

      if (req.method === "POST" && pathname === "/v1/billing/subscriptions") {
        const user = await requireFirebaseUser(req);
        const body = JSON.parse((await readRawBody(req)) || "{}");
        const plan = await store.getDoc("subscription_plans", body.planId || "");
        const interval = body.billingInterval === "ANNUAL" ? "ANNUAL" : "MONTHLY";
        const result = await razorpayService.createSubscriptionCheckout({
          userId: user.uid,
          email: user.email,
          plan,
          billingInterval: interval,
        });
        return sendJson(res, 201, result);
      }

      if (req.method === "POST" && pathname === "/v1/billing/orders/credits") {
        const user = await requireFirebaseUser(req);
        const body = JSON.parse((await readRawBody(req)) || "{}");
        const pack = DEFAULT_CREDIT_PACKS.find((p) => p.id === body.packId);
        if (!pack) {
          return sendJson(res, 404, { error: "Credit pack not found." });
        }
        const result = await razorpayService.createCreditOrder({ userId: user.uid, pack });
        return sendJson(res, 201, result);
      }

      if (req.method === "POST" && pathname === "/v1/billing/verify") {
        const user = await requireFirebaseUser(req);
        const body = JSON.parse((await readRawBody(req)) || "{}");
        const result = await razorpayService.verifyAndSyncCheckout({
          userId: user.uid,
          razorpayPaymentId: body.razorpayPaymentId || body.razorpay_payment_id,
          razorpaySubscriptionId: body.razorpaySubscriptionId || body.razorpay_subscription_id || "",
          razorpayOrderId: body.razorpayOrderId || body.razorpay_order_id || "",
          razorpaySignature: body.razorpaySignature || body.razorpay_signature,
        });
        return sendJson(res, 200, result);
      }

      if (req.method === "POST" && /^\/v1\/billing\/subscriptions\/[^/]+\/cancel$/.test(pathname)) {
        const user = await requireFirebaseUser(req);
        const subId = decodeURIComponent(pathname.split("/")[4]);
        const body = JSON.parse((await readRawBody(req)) || "{}");
        const updated = await razorpayService.cancelSubscription({
          subscriptionId: subId,
          requesterUid: user.uid,
          cancelAtCycleEnd: body.cancelAtCycleEnd !== false,
        });
        return sendJson(res, 200, { subscription: updated });
      }

      if (req.method === "GET" && pathname === "/v1/billing/checkout-page") {
        const subscriptionId = url.searchParams.get("subscription_id") || "";
        const orderId = url.searchParams.get("order_id") || "";
        if (!razorpayService.isConfigured()) {
          return sendHtml(
            res,
            503,
            "<html><body style='background:#030917;color:#fda4af;font-family:sans-serif;padding:24px'><h3>Razorpay Not Configured</h3><p>RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET must be set on the backend before checkout can open.</p></body></html>"
          );
        }
        return sendHtml(
          res,
          200,
          buildCheckoutHtml({
            keyId: config.razorpayKeyId,
            subscriptionId,
            orderId,
          })
        );
      }

      if (req.method === "GET" && /^\/v1\/billing\/payments\/[^/]+\/receipt$/.test(pathname)) {
        const user = await requireFirebaseUser(req);
        const payId = decodeURIComponent(pathname.split("/")[4]);
        const pay = await store.getDoc("payments", payId);
        if (!pay) return sendJson(res, 404, { error: "Payment not found" });
        const isAdmin = await authVerifier.isVerifiedAdmin(store, user);
        if (!isAdmin && pay.userId !== user.uid) {
          return sendJson(res, 403, { error: "Forbidden" });
        }
        if (url.searchParams.get("format") === "html") {
          return sendHtml(res, 200, razorpayService.generateReceiptHtml(pay));
        }
        return sendJson(res, 200, { receipt: pay, html: razorpayService.generateReceiptHtml(pay) });
      }

      // --- RAZORPAY WEBHOOK (Verified via X-Razorpay-Signature, processed idempotently) ---
      if (req.method === "POST" && pathname === "/v1/webhooks/razorpay") {
        const rawBody = await readRawBody(req);
        const signature = req.headers["x-razorpay-signature"] || "";
        const eventId = req.headers["x-razorpay-event-id"] || "";
        const result = await razorpayService.processWebhook({
          rawBody,
          signature,
          eventId,
        });
        return sendJson(res, 200, result);
      }

      // --- OWNER-ONLY ADMIN CONTROL PANEL ---
      if (req.method === "GET" && pathname === "/v1/admin/overview") {
        await requireAdminUser(req);
        const [users, developers, subscriptions, payments, apiKeys, apiRequests, auditLogs] =
          await Promise.all([
            store.listDocs("users"),
            store.listDocs("developers"),
            store.listDocs("subscriptions"),
            store.listDocs("payments"),
            store.listDocs("api_keys"),
            store.listDocs("api_requests"),
            store.listDocs("audit_logs"),
          ]);
        const capturedRevenueInr =
          payments
            .filter((p) => p.status === "CAPTURED")
            .reduce((sum, p) => sum + Number(p.amountPaise || 0), 0) / 100;

        return sendJson(res, 200, {
          metrics: {
            totalUsers: users.length,
            totalDevelopers: developers.length,
            activeSubscriptions: subscriptions.filter((s) => s.status === "ACTIVE").length,
            capturedRevenueInr,
            totalApiRequests: apiRequests.length,
            activeApiKeys: apiKeys.filter((k) => k.status === "ACTIVE").length,
          },
          users,
          developers,
          subscriptions,
          payments,
          apiKeys: apiKeys.map((k) => apiKeyService.sanitizeKeyRecord(k)),
          recentApiErrors: apiRequests.filter((r) => r.statusCode >= 400).slice(-25).reverse(),
          auditLogs: auditLogs.slice(-50).reverse(),
        });
      }

      if (req.method === "GET" && pathname === "/v1/admin/providers") {
        await requireAdminUser(req);
        const providers = await store.listDocs("ai_providers");
        return sendJson(res, 200, {
          providers: providers.map((p) => providerRouter.sanitizeProviderForAdmin(p)),
        });
      }

      if (req.method === "POST" && pathname === "/v1/admin/providers") {
        const admin = await requireAdminUser(req);
        const body = JSON.parse((await readRawBody(req)) || "{}");
        const id = String(body.id || "").trim().replace(/[^a-zA-Z0-9_-]/g, "_");
        if (!id || !body.name || !body.adapterType) {
          return sendJson(res, 400, { error: "id, name, and adapterType are required." });
        }
        const secretEnvRef = String(body.secretEnvRef || `PROVIDER_${id.toUpperCase()}_API_KEY`).trim();
        if (body.apiCredential) {
          // Stored strictly in server runtime memory/secret manager, NEVER in Firestore!
          providerRouter.setProviderCredential(secretEnvRef, String(body.apiCredential).trim());
        }
        const now = new Date().toISOString();
        const existing = await store.getDoc("ai_providers", id);
        const doc = {
          id,
          name: String(body.name).slice(0, 80),
          adapterType: body.adapterType,
          baseUrl: String(body.baseUrl || "").slice(0, 300),
          secretEnvRef,
          enabled: body.enabled !== false,
          priority: Number(body.priority || 1),
          fallbackProviderId: String(body.fallbackProviderId || ""),
          healthStatus: existing?.healthStatus || "HEALTHY",
          totalRequests: Number(existing?.totalRequests || 0),
          totalErrors: Number(existing?.totalErrors || 0),
          lastError: existing?.lastError || "",
          updatedAt: now,
        };
        await store.setDoc("ai_providers", id, doc);
        const logId = `aud_${crypto.randomBytes(8).toString("hex")}`;
        await store.setDoc("audit_logs", logId, {
          id: logId,
          actorUid: admin.uid,
          actorEmail: admin.email,
          action: "UPSERT_AI_PROVIDER",
          targetPath: `ai_providers/${id}`,
          details: `Updated provider ${doc.name} (${doc.adapterType}), enabled=${doc.enabled}`,
          createdAt: now,
        });
        return sendJson(res, 200, { provider: providerRouter.sanitizeProviderForAdmin(doc) });
      }

      if (req.method === "POST" && pathname === "/v1/admin/models") {
        const admin = await requireAdminUser(req);
        const body = JSON.parse((await readRawBody(req)) || "{}");
        const modelId = String(body.modelId || "").trim();
        if (!modelId || !body.providerId) {
          return sendJson(res, 400, { error: "modelId and providerId are required." });
        }
        const now = new Date().toISOString();
        const existing = await store.getDoc("ai_models", modelId);
        const doc = {
          id: modelId,
          modelId,
          displayName: String(body.displayName || existing?.displayName || modelId),
          providerId: String(body.providerId),
          fallbackProviderId: String(body.fallbackProviderId || ""),
          suppliedBy: String(body.suppliedBy || existing?.suppliedBy || `External Provider: ${body.providerId}`),
          capability: body.capability || existing?.capability || "CHAT",
          creditCost: Number(body.creditCost ?? existing?.creditCost ?? 2),
          rateLimitRpm: Number(body.rateLimitRpm ?? existing?.rateLimitRpm ?? 60),
          enabled: body.enabled !== false,
          isDefault: Boolean(body.isDefault),
          updatedAt: now,
        };
        await store.setDoc("ai_models", modelId, doc);
        const logId = `aud_${crypto.randomBytes(8).toString("hex")}`;
        await store.setDoc("audit_logs", logId, {
          id: logId,
          actorUid: admin.uid,
          actorEmail: admin.email,
          action: "UPSERT_MODEL_ROUTING",
          targetPath: `ai_models/${modelId}`,
          details: `Provider=${doc.providerId}, Cost=${doc.creditCost}, Enabled=${doc.enabled}`,
          createdAt: now,
        });
        return sendJson(res, 200, { model: doc });
      }

      if (req.method === "POST" && pathname === "/v1/admin/plans") {
        const admin = await requireAdminUser(req);
        const body = JSON.parse((await readRawBody(req)) || "{}");
        const id = String(body.id || "").trim().toUpperCase();
        if (!id || !body.name) {
          return sendJson(res, 400, { error: "Plan id and name are required." });
        }
        const now = new Date().toISOString();
        const existing = await store.getDoc("subscription_plans", id);
        const plan = {
          id,
          name: String(body.name).slice(0, 60),
          planCategory: body.planCategory === "DEVELOPER" ? "DEVELOPER" : existing?.planCategory || "CONSUMER",
          monthlyPriceInr: Number(body.monthlyPriceInr ?? existing?.monthlyPriceInr ?? 0),
          annualPriceInr: Number(body.annualPriceInr ?? existing?.annualPriceInr ?? 0),
          monthlyCredits: Number(body.monthlyCredits ?? existing?.monthlyCredits ?? 100),
          rateLimitRpm: Number(body.rateLimitRpm ?? existing?.rateLimitRpm ?? 30),
          razorpayMonthlyPlanId: String(body.razorpayMonthlyPlanId ?? existing?.razorpayMonthlyPlanId ?? ""),
          razorpayAnnualPlanId: String(body.razorpayAnnualPlanId ?? existing?.razorpayAnnualPlanId ?? ""),
          badge: String(body.badge ?? existing?.badge ?? ""),
          features: Array.isArray(body.features) ? body.features : existing?.features || [],
          sortOrder: Number(body.sortOrder ?? existing?.sortOrder ?? 10),
          active: body.active !== false,
          updatedAt: now,
        };
        await store.setDoc("subscription_plans", id, plan);
        const logId = `aud_${crypto.randomBytes(8).toString("hex")}`;
        await store.setDoc("audit_logs", logId, {
          id: logId,
          actorUid: admin.uid,
          actorEmail: admin.email,
          action: "UPSERT_SUBSCRIPTION_PLAN",
          targetPath: `subscription_plans/${id}`,
          details: `₹${plan.monthlyPriceInr}/mo, ${plan.monthlyCredits} credits, ${plan.rateLimitRpm} RPM`,
          createdAt: now,
        });
        return sendJson(res, 200, { plan });
      }

      if (req.method === "POST" && pathname === "/v1/admin/credits/adjust") {
        const admin = await requireAdminUser(req);
        const body = JSON.parse((await readRawBody(req)) || "{}");
        if (!body.targetUid || typeof body.delta !== "number") {
          return sendJson(res, 400, { error: "targetUid and numeric delta are required." });
        }
        const resWallet = await walletService.adminAdjustCredits({
          targetUid: body.targetUid,
          delta: body.delta,
          reason: body.reason || "Admin manual adjustment",
          actorUid: admin.uid,
          actorEmail: admin.email,
        });
        return sendJson(res, 200, resWallet);
      }

      if (req.method === "POST" && /^\/v1\/admin\/payments\/[^/]+\/refund$/.test(pathname)) {
        const admin = await requireAdminUser(req);
        const paymentDocId = decodeURIComponent(pathname.split("/")[4]);
        const body = JSON.parse((await readRawBody(req)) || "{}");
        const updated = await razorpayService.refundPayment({
          paymentDocId,
          actorUid: admin.uid,
          actorEmail: admin.email,
          reason: body.reason || "Owner refund",
        });
        return sendJson(res, 200, { payment: updated });
      }

      if (req.method === "POST" && /^\/v1\/admin\/keys\/[^/]+\/revoke$/.test(pathname)) {
        await requireAdminUser(req);
        const keyId = decodeURIComponent(pathname.split("/")[4]);
        const result = await apiKeyService.revokeKey(store, { keyId, requesterUid: "admin", isAdmin: true });
        return sendJson(res, result.ok ? 200 : result.status, result.ok ? result : { error: result.error });
      }

      return sendJson(res, 404, { error: `Route not found: ${req.method} ${pathname}` });
    } catch (err) {
      const status = err.statusCode || 500;
      return sendJson(
        res,
        status,
        { error: err.message || "Internal server error" },
        err.headers || {}
      );
    }
  });

  return {
    server,
    store,
    config,
    apiKeyService,
    providerRouter,
    razorpayService,
    walletService,
    readyPromise,
  };
}

module.exports = { createCerevixBackend };
