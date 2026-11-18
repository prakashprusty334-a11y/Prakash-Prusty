const crypto = require("node:crypto");

const GOOGLE_CERTS_URL =
  "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";

class FirebaseAuthVerifier {
  constructor({ projectId, adminEmails = [], customTokenVerifier = null }) {
    this.projectId = projectId;
    this.adminEmails = new Set(adminEmails.map((e) => e.toLowerCase()));
    this.customTokenVerifier = customTokenVerifier;
    this.certCache = { certs: null, expiresAtMs: 0 };
  }

  async _fetchGoogleCerts() {
    if (this.certCache.certs && Date.now() < this.certCache.expiresAtMs) {
      return this.certCache.certs;
    }
    const resp = await fetch(GOOGLE_CERTS_URL);
    if (!resp.ok) {
      throw new Error(`Unable to fetch Firebase Auth public certs: ${resp.status}`);
    }
    const cacheControl = resp.headers.get("cache-control") || "";
    const match = cacheControl.match(/max-age=(\d+)/);
    const maxAgeSec = match ? Number(match[1]) : 3600;
    const certs = await resp.json();
    this.certCache = {
      certs,
      expiresAtMs: Date.now() + maxAgeSec * 1000,
    };
    return certs;
  }

  async verifyIdToken(idToken) {
    if (!idToken || typeof idToken !== "string") {
      throw new Error("Missing Firebase ID token");
    }
    if (this.customTokenVerifier) {
      return this.customTokenVerifier(idToken);
    }

    const parts = idToken.split(".");
    if (parts.length !== 3) {
      throw new Error("Malformed JWT");
    }
    const [rawHeader, rawPayload, rawSig] = parts;
    const header = JSON.parse(Buffer.from(rawHeader, "base64url").toString("utf8"));
    const payload = JSON.parse(Buffer.from(rawPayload, "base64url").toString("utf8"));

    if (header.alg !== "RS256" || !header.kid) {
      throw new Error("Unsupported JWT algorithm or missing kid");
    }
    const certs = await this._fetchGoogleCerts();
    const pem = certs[header.kid];
    if (!pem) {
      throw new Error("Signing certificate not found for Firebase token");
    }

    const verifier = crypto.createVerify("RSA-SHA256");
    verifier.update(`${rawHeader}.${rawPayload}`);
    const validSig = verifier.verify(pem, Buffer.from(rawSig, "base64url"));
    if (!validSig) {
      throw new Error("Invalid Firebase ID token signature");
    }

    const nowSec = Math.floor(Date.now() / 1000);
    if (payload.aud !== this.projectId) {
      throw new Error("Firebase token audience mismatch");
    }
    if (payload.iss !== `https://securetoken.google.com/${this.projectId}`) {
      throw new Error("Firebase token issuer mismatch");
    }
    if (!payload.sub || typeof payload.sub !== "string") {
      throw new Error("Firebase token missing subject");
    }
    if (Number(payload.exp || 0) <= nowSec) {
      throw new Error("Firebase token expired");
    }

    return {
      uid: payload.sub,
      email: (payload.email || "").toLowerCase(),
      emailVerified: payload.email_verified === true,
      name: payload.name || "",
      picture: payload.picture || "",
    };
  }

  async isVerifiedAdmin(store, decodedUser) {
    if (!decodedUser || !decodedUser.uid) return false;
    if (decodedUser.emailVerified && this.adminEmails.has((decodedUser.email || "").toLowerCase())) {
      return true;
    }
    const adminDoc = await store.getDoc("admin_users", decodedUser.uid);
    return Boolean(adminDoc);
  }
}

module.exports = { FirebaseAuthVerifier };
