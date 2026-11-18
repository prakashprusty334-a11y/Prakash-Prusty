const crypto = require("node:crypto");

const KEY_PREFIX_HEADER = "crvx_live_";

class ApiKeyService {
  constructor(secretPepper) {
    if (!secretPepper || secretPepper.length < 16) {
      this.secretPepper = crypto.randomBytes(32).toString("hex");
      this.ephemeralPepper = true;
    } else {
      this.secretPepper = secretPepper;
      this.ephemeralPepper = false;
    }
    this.rateWindows = new Map();
  }

  generateRawKey() {
    const token = crypto.randomBytes(24).toString("hex");
    return `${KEY_PREFIX_HEADER}${token}`;
  }

  hashKey(rawKey) {
    return crypto.createHmac("sha256", this.secretPepper).update(String(rawKey)).digest("hex");
  }

  extractPrefix(rawKey) {
    return String(rawKey).slice(0, 16);
  }

  timingSafeCompareHex(a, b) {
    if (!a || !b || a.length !== b.length) return false;
    const bufA = Buffer.from(a, "hex");
    const bufB = Buffer.from(b, "hex");
    if (bufA.length !== bufB.length) return false;
    return crypto.timingSafeEqual(bufA, bufB);
  }

  async createKey(store, { userId, developerId, name, scopes = ["chat", "images", "audio", "video"], rateLimitRpm = 10 }) {
    const rawKey = this.generateRawKey();
    const keyHash = this.hashKey(rawKey);
    const keyPrefix = this.extractPrefix(rawKey);
    const keyId = `key_${crypto.randomBytes(10).toString("hex")}`;
    const now = new Date().toISOString();

    const record = {
      id: keyId,
      userId,
      developerId: developerId || userId,
      name: String(name || "Default Key").trim().slice(0, 80) || "Default Key",
      keyPrefix,
      keyHash,
      scopes,
      rateLimitRpm: Number(rateLimitRpm) || 10,
      status: "ACTIVE",
      requestCount: 0,
      lastUsedAt: null,
      createdAt: now,
      revokedAt: null,
    };

    await store.setDoc("api_keys", keyId, record);

    return {
      keyRecord: this.sanitizeKeyRecord(record),
      // Full plaintext key returned ONLY ONCE at creation time
      plaintextKey: rawKey,
    };
  }

  async authenticateKey(store, rawKey) {
    if (!rawKey || typeof rawKey !== "string" || !rawKey.startsWith(KEY_PREFIX_HEADER)) {
      return { ok: false, status: 401, error: "Invalid or missing CEREVIX API key" };
    }
    const computedHash = this.hashKey(rawKey.trim());
    const prefix = this.extractPrefix(rawKey.trim());
    const candidates = await store.queryDocs("api_keys", (doc) => doc.keyPrefix === prefix);

    for (const candidate of candidates) {
      if (this.timingSafeCompareHex(candidate.keyHash, computedHash)) {
        if (candidate.status !== "ACTIVE") {
          return { ok: false, status: 401, error: "API key has been revoked" };
        }
        return { ok: true, keyRecord: candidate };
      }
    }
    return { ok: false, status: 401, error: "API key not recognized" };
  }

  async revokeKey(store, { keyId, requesterUid, isAdmin = false }) {
    const existing = await store.getDoc("api_keys", keyId);
    if (!existing) {
      return { ok: false, status: 404, error: "API key not found" };
    }
    if (!isAdmin && existing.userId !== requesterUid) {
      return { ok: false, status: 403, error: "Forbidden" };
    }
    const now = new Date().toISOString();
    const updated = {
      ...existing,
      status: "REVOKED",
      revokedAt: now,
    };
    await store.setDoc("api_keys", keyId, updated);
    return { ok: true, keyRecord: this.sanitizeKeyRecord(updated) };
  }

  async regenerateKey(store, { keyId, requesterUid, isAdmin = false }) {
    const existing = await store.getDoc("api_keys", keyId);
    if (!existing) {
      return { ok: false, status: 404, error: "API key not found" };
    }
    if (!isAdmin && existing.userId !== requesterUid) {
      return { ok: false, status: 403, error: "Forbidden" };
    }
    await this.revokeKey(store, { keyId, requesterUid, isAdmin });
    return this.createKey(store, {
      userId: existing.userId,
      developerId: existing.developerId,
      name: existing.name,
      scopes: existing.scopes,
      rateLimitRpm: existing.rateLimitRpm,
    });
  }

  checkRateLimit(keyId, limitRpm, nowMs = Date.now()) {
    const limit = Math.max(1, Number(limitRpm || 10));
    const windowMs = 60_000;
    const cutoff = nowMs - windowMs;
    const timestamps = (this.rateWindows.get(keyId) || []).filter((t) => t > cutoff);

    if (timestamps.length >= limit) {
      const oldest = timestamps[0];
      const resetSeconds = Math.max(1, Math.ceil((oldest + windowMs - nowMs) / 1000));
      this.rateWindows.set(keyId, timestamps);
      return {
        allowed: false,
        limit,
        remaining: 0,
        resetSeconds,
      };
    }

    timestamps.push(nowMs);
    this.rateWindows.set(keyId, timestamps);
    return {
      allowed: true,
      limit,
      remaining: Math.max(0, limit - timestamps.length),
      resetSeconds: 60,
    };
  }

  sanitizeKeyRecord(record) {
    const { keyHash, ...safe } = record;
    return safe;
  }
}

module.exports = { ApiKeyService, KEY_PREFIX_HEADER };
