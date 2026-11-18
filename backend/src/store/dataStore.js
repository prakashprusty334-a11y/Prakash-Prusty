const crypto = require("node:crypto");
const fs = require("node:fs");

class InMemoryTransactionalStore {
  constructor() {
    this.collections = new Map();
    this.txQueue = Promise.resolve();
  }

  _getCol(name) {
    if (!this.collections.has(name)) {
      this.collections.set(name, new Map());
    }
    return this.collections.get(name);
  }

  async getDoc(collection, id) {
    const doc = this._getCol(collection).get(id);
    return doc ? structuredClone(doc) : null;
  }

  async setDoc(collection, id, data) {
    this._getCol(collection).set(id, structuredClone({ ...data, id }));
    return this.getDoc(collection, id);
  }

  async updateDoc(collection, id, patch) {
    const col = this._getCol(collection);
    const prev = col.get(id);
    if (!prev) throw new Error(`Document ${collection}/${id} not found`);
    const next = structuredClone({ ...prev, ...patch, id });
    col.set(id, next);
    return structuredClone(next);
  }

  async deleteDoc(collection, id) {
    this._getCol(collection).delete(id);
  }

  async listDocs(collection) {
    return Array.from(this._getCol(collection).values()).map((d) => structuredClone(d));
  }

  async queryDocs(collection, predicate) {
    const all = await this.listDocs(collection);
    return all.filter(predicate);
  }

  async runTransaction(fn) {
    const run = async () => {
      const txProxy = {
        get: (col, id) => this.getDoc(col, id),
        set: (col, id, data) => this.setDoc(col, id, data),
        update: (col, id, patch) => this.updateDoc(col, id, patch),
        delete: (col, id) => this.deleteDoc(col, id),
      };
      return fn(txProxy);
    };
    const next = this.txQueue.then(run, run);
    this.txQueue = next.catch(() => {});
    return next;
  }
}

class FirestoreRestStore {
  constructor({ projectId, databaseId, serviceAccount, emulatorHost }) {
    this.projectId = projectId;
    this.databaseId = databaseId || "(default)";
    this.emulatorHost = emulatorHost || "";
    this.serviceAccount = this._parseServiceAccount(serviceAccount);
    this.tokenCache = { token: "", expiresAtMs: 0 };
    this.txQueue = Promise.resolve();
  }

  _parseServiceAccount(raw) {
    if (!raw) return null;
    try {
      if (typeof raw === "object") return raw;
      const trimmed = raw.trim();
      if (trimmed.startsWith("{")) return JSON.parse(trimmed);
      if (fs.existsSync(trimmed)) return JSON.parse(fs.readFileSync(trimmed, "utf8"));
      const decoded = Buffer.from(trimmed, "base64").toString("utf8");
      if (decoded.trim().startsWith("{")) return JSON.parse(decoded);
    } catch {
      return null;
    }
    return null;
  }

  _baseUrl() {
    const dbName = `projects/${this.projectId}/databases/${this.databaseId}/documents`;
    if (this.emulatorHost) {
      return `http://${this.emulatorHost}/v1/${dbName}`;
    }
    return `https://firestore.googleapis.com/v1/${dbName}`;
  }

  async _getAccessToken() {
    if (this.emulatorHost) return "owner";
    if (!this.serviceAccount) {
      throw new Error("FIREBASE_SERVICE_ACCOUNT is required for Firestore REST access outside emulator.");
    }
    if (this.tokenCache.token && Date.now() < this.tokenCache.expiresAtMs - 60_000) {
      return this.tokenCache.token;
    }
    const nowSec = Math.floor(Date.now() / 1000);
    const header = Buffer.from(JSON.stringify({ alg: "RS256", typ: "JWT" })).toString("base64url");
    const claim = Buffer.from(
      JSON.stringify({
        iss: this.serviceAccount.client_email,
        scope: "https://www.googleapis.com/auth/datastore",
        aud: this.serviceAccount.token_uri || "https://oauth2.googleapis.com/token",
        exp: nowSec + 3600,
        iat: nowSec,
      })
    ).toString("base64url");
    const signer = crypto.createSign("RSA-SHA256");
    signer.update(`${header}.${claim}`);
    const signature = signer.sign(this.serviceAccount.private_key, "base64url");
    const jwt = `${header}.${claim}.${signature}`;

    const resp = await fetch(this.serviceAccount.token_uri || "https://oauth2.googleapis.com/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${encodeURIComponent(jwt)}`,
    });
    if (!resp.ok) {
      const text = await resp.text();
      throw new Error(`Failed to obtain GCP OAuth token: ${resp.status} ${text}`);
    }
    const json = await resp.json();
    this.tokenCache = {
      token: json.access_token,
      expiresAtMs: Date.now() + Number(json.expires_in || 3600) * 1000,
    };
    return this.tokenCache.token;
  }

  _toFirestoreValue(val) {
    if (val === null || val === undefined) return { nullValue: null };
    if (typeof val === "boolean") return { booleanValue: val };
    if (typeof val === "number") {
      return Number.isInteger(val) ? { integerValue: String(val) } : { doubleValue: val };
    }
    if (typeof val === "string") {
      if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/.test(val) && !Number.isNaN(Date.parse(val))) {
        return { timestampValue: new Date(val).toISOString() };
      }
      return { stringValue: val };
    }
    if (val instanceof Date) return { timestampValue: val.toISOString() };
    if (Array.isArray(val)) {
      return { arrayValue: { values: val.map((item) => this._toFirestoreValue(item)) } };
    }
    if (typeof val === "object") {
      const fields = {};
      for (const [k, v] of Object.entries(val)) {
        if (v !== undefined) fields[k] = this._toFirestoreValue(v);
      }
      return { mapValue: { fields } };
    }
    return { stringValue: String(val) };
  }

  _fromFirestoreValue(v) {
    if (!v || "nullValue" in v) return null;
    if ("booleanValue" in v) return v.booleanValue;
    if ("integerValue" in v) return Number(v.integerValue);
    if ("doubleValue" in v) return Number(v.doubleValue);
    if ("timestampValue" in v) return v.timestampValue;
    if ("stringValue" in v) return v.stringValue;
    if ("arrayValue" in v) return (v.arrayValue.values || []).map((x) => this._fromFirestoreValue(x));
    if ("mapValue" in v) {
      const out = {};
      for (const [k, fv] of Object.entries(v.mapValue.fields || {})) {
        out[k] = this._fromFirestoreValue(fv);
      }
      return out;
    }
    return null;
  }

  _fromDocument(doc) {
    const out = {};
    for (const [k, fv] of Object.entries(doc.fields || {})) {
      out[k] = this._fromFirestoreValue(fv);
    }
    out.id = doc.name.split("/").pop();
    return out;
  }

  async _headers() {
    const token = await this._getAccessToken();
    return {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    };
  }

  async getDoc(collection, id) {
    const resp = await fetch(`${this._baseUrl()}/${collection}/${encodeURIComponent(id)}`, {
      headers: await this._headers(),
    });
    if (resp.status === 404) return null;
    if (!resp.ok) throw new Error(`Firestore GET ${collection}/${id} failed: ${resp.status}`);
    return this._fromDocument(await resp.json());
  }

  async setDoc(collection, id, data) {
    const fields = {};
    for (const [k, v] of Object.entries(data)) {
      if (k !== "id" && v !== undefined) fields[k] = this._toFirestoreValue(v);
    }
    const resp = await fetch(`${this._baseUrl()}/${collection}/${encodeURIComponent(id)}`, {
      method: "PATCH",
      headers: await this._headers(),
      body: JSON.stringify({ fields }),
    });
    if (!resp.ok) {
      const txt = await resp.text();
      throw new Error(`Firestore SET ${collection}/${id} failed: ${resp.status} ${txt}`);
    }
    return this._fromDocument(await resp.json());
  }

  async updateDoc(collection, id, patch) {
    const prev = await this.getDoc(collection, id);
    if (!prev) throw new Error(`Document ${collection}/${id} not found`);
    return this.setDoc(collection, id, { ...prev, ...patch });
  }

  async deleteDoc(collection, id) {
    const resp = await fetch(`${this._baseUrl()}/${collection}/${encodeURIComponent(id)}`, {
      method: "DELETE",
      headers: await this._headers(),
    });
    if (!resp.ok && resp.status !== 404) {
      throw new Error(`Firestore DELETE ${collection}/${id} failed: ${resp.status}`);
    }
  }

  async listDocs(collection) {
    const resp = await fetch(`${this._baseUrl()}/${collection}?pageSize=200`, {
      headers: await this._headers(),
    });
    if (resp.status === 404) return [];
    if (!resp.ok) throw new Error(`Firestore LIST ${collection} failed: ${resp.status}`);
    const json = await resp.json();
    return (json.documents || []).map((d) => this._fromDocument(d));
  }

  async queryDocs(collection, predicate) {
    const all = await this.listDocs(collection);
    return all.filter(predicate);
  }

  async runTransaction(fn) {
    const run = async () => {
      const txProxy = {
        get: (col, id) => this.getDoc(col, id),
        set: (col, id, data) => this.setDoc(col, id, data),
        update: (col, id, patch) => this.updateDoc(col, id, patch),
        delete: (col, id) => this.deleteDoc(col, id),
      };
      return fn(txProxy);
    };
    const next = this.txQueue.then(run, run);
    this.txQueue = next.catch(() => {});
    return next;
  }
}

function createDataStore(config) {
  if (config.firestoreEmulatorHost || config.firebaseServiceAccount) {
    return new FirestoreRestStore({
      projectId: config.firebaseProjectId,
      databaseId: config.firebaseDatabaseId,
      serviceAccount: config.firebaseServiceAccount,
      emulatorHost: config.firestoreEmulatorHost,
    });
  }
  return new InMemoryTransactionalStore();
}

module.exports = {
  InMemoryTransactionalStore,
  FirestoreRestStore,
  createDataStore,
};
