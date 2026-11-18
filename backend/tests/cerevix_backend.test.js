const { test, describe, beforeEach, after } = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const { createCerevixBackend } = require("../src/app");
const { createConfig } = require("../src/config");
const { InMemoryTransactionalStore } = require("../src/store/dataStore");

const TEST_RAZORPAY_KEY_ID = "rzp_test_cerevix1234";
const TEST_RAZORPAY_SECRET = "rzp_test_secret_9876543210";
const TEST_WEBHOOK_SECRET = "whsec_test_cerevix_abcdef";
const TEST_API_PEPPER = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

function buildFakeFetch() {
  const createdSubs = new Map();
  const createdOrders = new Map();
  let failGemini = false;

  const fn = async (url, opts = {}) => {
    const u = String(url);
    const body = opts.body ? JSON.parse(opts.body) : null;

    if (u.startsWith("https://api.razorpay.com/v1/plans")) {
      return {
        ok: true,
        status: 200,
        json: async () => ({ id: `plan_test_${Date.now()}`, ...body }),
      };
    }

    if (u === "https://api.razorpay.com/v1/subscriptions") {
      const id = `sub_rzp_${crypto.randomBytes(6).toString("hex")}`;
      const subObj = {
        id,
        status: "created",
        short_url: `https://rzp.io/i/${id}`,
        current_end: Math.floor(Date.now() / 1000) + 30 * 86400,
        ...body,
      };
      createdSubs.set(id, subObj);
      return { ok: true, status: 200, json: async () => subObj };
    }

    if (u.startsWith("https://api.razorpay.com/v1/subscriptions/") && u.endsWith("/cancel")) {
      const subId = u.split("/")[5];
      const existing = createdSubs.get(subId) || { id: subId };
      existing.status = "cancelled";
      createdSubs.set(subId, existing);
      return { ok: true, status: 200, json: async () => existing };
    }

    if (u.startsWith("https://api.razorpay.com/v1/subscriptions/")) {
      const subId = u.split("/")[5];
      const existing = createdSubs.get(subId) || { id: subId, status: "active" };
      return {
        ok: true,
        status: 200,
        json: async () => ({ ...existing, status: existing.forceStatus || "active" }),
      };
    }

    if (u === "https://api.razorpay.com/v1/orders") {
      const id = `order_rzp_${crypto.randomBytes(6).toString("hex")}`;
      const ord = { id, status: "created", ...body };
      createdOrders.set(id, ord);
      return { ok: true, status: 200, json: async () => ord };
    }

    if (u.startsWith("https://api.razorpay.com/v1/payments/") && u.endsWith("/refund")) {
      const payId = u.split("/")[5];
      return {
        ok: true,
        status: 200,
        json: async () => ({ id: `rfnd_${payId}`, payment_id: payId, amount: body.amount }),
      };
    }

    if (u.startsWith("https://api.razorpay.com/v1/payments/")) {
      const payId = u.split("/")[5];
      const isPending = payId.includes("pending");
      const isFailed = payId.includes("failed");
      return {
        ok: true,
        status: 200,
        json: async () => ({
          id: payId,
          status: isFailed ? "failed" : isPending ? "authorized" : "captured",
          method: "upi",
          amount: 49900,
        }),
      };
    }

    if (u.includes("generativelanguage.googleapis.com")) {
      if (failGemini) {
        return {
          ok: false,
          status: 503,
          json: async () => ({ error: { message: "Simulated upstream Gemini outage" } }),
        };
      }
      return {
        ok: true,
        status: 200,
        json: async () => ({
          candidates: [
            {
              content: { parts: [{ text: "CEREVIX AI Gateway verified response." }] },
              finishReason: "STOP",
            },
          ],
          usageMetadata: { promptTokenCount: 10, candidatesTokenCount: 12, totalTokenCount: 22 },
        }),
      };
    }

    return { ok: false, status: 404, json: async () => ({ error: "Unmocked URL: " + u }) };
  };

  fn.setFailGemini = (flag) => {
    failGemini = flag;
  };
  fn.setSubscriptionStatus = (subId, status) => {
    const s = createdSubs.get(subId);
    if (s) s.forceStatus = status;
  };
  return fn;
}

async function startTestApp() {
  const store = new InMemoryTransactionalStore();
  const fakeFetch = buildFakeFetch();
  const config = createConfig({
    NODE_ENV: "test",
    GEMINI_API_KEY: "test_gemini_secret",
    RAZORPAY_KEY_ID: TEST_RAZORPAY_KEY_ID,
    RAZORPAY_KEY_SECRET: TEST_RAZORPAY_SECRET,
    RAZORPAY_WEBHOOK_SECRET: TEST_WEBHOOK_SECRET,
    CEREVIX_API_SECRET: TEST_API_PEPPER,
    ADMIN_EMAILS: "prakashprusty334@gmail.com",
  });

  const tokenUsers = {
    "token-alice": {
      uid: "alice_1",
      email: "alice@example.com",
      emailVerified: true,
      name: "Alice Creator",
    },
    "token-admin": {
      uid: "owner_1",
      email: "prakashprusty334@gmail.com",
      emailVerified: true,
      name: "Prakash Owner",
    },
    "token-spoof-admin": {
      uid: "spoof_1",
      email: "prakashprusty334@gmail.com",
      emailVerified: false,
      name: "Spoof",
    },
  };

  const backend = createCerevixBackend({
    config,
    store,
    fetchImpl: fakeFetch,
    customTokenVerifier: async (t) => {
      if (!tokenUsers[t]) throw new Error("Invalid Firebase token");
      return tokenUsers[t];
    },
  });

  await backend.readyPromise;
  await store.setDoc("users", "alice_1", {
    userId: "alice_1",
    email: "alice@example.com",
    displayName: "Alice Creator",
    role: "USER",
    status: "ACTIVE",
    planId: "FREE",
    creditBalance: 100,
    totalCreditsUsed: 0,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  });

  await new Promise((resolve) => backend.server.listen(0, "127.0.0.1", resolve));
  const port = backend.server.address().port;
  const baseUrl = `http://127.0.0.1:${port}`;
  return {
    backend,
    store,
    fakeFetch,
    baseUrl,
    close: () => {
      backend.server.closeAllConnections?.();
      backend.server.close();
    },
  };
}

describe("CEREVIX AI Backend — API Keys, Rate Limit, Credits, Razorpay, Webhooks & Admin", () => {
  let ctx;

  beforeEach(async () => {
    if (ctx) ctx.close();
    ctx = await startTestApp();
  });

  after(() => {
    if (ctx) ctx.close();
  });

  test("1-4. API key generation (shown once, hashed in DB), auth, revocation, regeneration & rate limiting", async () => {
    // Register developer
    const regResp = await fetch(`${ctx.baseUrl}/v1/developer/register`, {
      method: "POST",
      headers: { Authorization: "Bearer token-alice", "Content-Type": "application/json" },
      body: JSON.stringify({ organizationName: "Alice AI Studio" }),
    });
    assert.equal(regResp.status, 201);

    // Create key
    const keyResp = await fetch(`${ctx.baseUrl}/v1/developer/keys`, {
      method: "POST",
      headers: { Authorization: "Bearer token-alice", "Content-Type": "application/json" },
      body: JSON.stringify({ name: "Backend Test Key" }),
    });
    assert.equal(keyResp.status, 201);
    const created = await keyResp.json();
    assert.ok(created.plaintextKey.startsWith("crvx_live_"));
    assert.equal(created.keyRecord.keyHash, undefined);

    // Stored record in DB has hash, NOT plaintext
    const storedKey = await ctx.store.getDoc("api_keys", created.keyRecord.id);
    assert.ok(storedKey.keyHash);
    assert.notEqual(storedKey.keyHash, created.plaintextKey);

    // List keys endpoint never leaks plaintext or hash
    const listResp = await fetch(`${ctx.baseUrl}/v1/developer/keys`, {
      headers: { Authorization: "Bearer token-alice" },
    });
    const listJson = await listResp.json();
    assert.equal(listJson.keys[0].keyHash, undefined);
    assert.equal(listJson.keys[0].plaintextKey, undefined);

    // Missing/invalid key -> 401
    const badAuth = await fetch(`${ctx.baseUrl}/v1/models`, {
      headers: { Authorization: "Bearer crvx_live_invalid" },
    });
    assert.equal(badAuth.status, 401);

    // Valid key authenticates GET /v1/models
    const goodAuth = await fetch(`${ctx.baseUrl}/v1/models`, {
      headers: { Authorization: `Bearer ${created.plaintextKey}` },
    });
    assert.equal(goodAuth.status, 200);
    assert.ok(goodAuth.headers.get("x-ratelimit-limit"));

    // Rate limiting: set key RPM to 2 and exceed limit
    await ctx.store.updateDoc("api_keys", created.keyRecord.id, { rateLimitRpm: 2 });
    const req2 = await fetch(`${ctx.baseUrl}/v1/credits`, {
      headers: { "X-Cerevix-Api-Key": created.plaintextKey },
    });
    assert.equal(req2.status, 200);
    const req3 = await fetch(`${ctx.baseUrl}/v1/credits`, {
      headers: { "X-Cerevix-Api-Key": created.plaintextKey },
    });
    assert.equal(req3.status, 429);

    // Regenerate revokes old key and returns new one-time key
    const regenResp = await fetch(
      `${ctx.baseUrl}/v1/developer/keys/${created.keyRecord.id}/regenerate`,
      { method: "POST", headers: { Authorization: "Bearer token-alice" } }
    );
    assert.equal(regenResp.status, 200);
    const regen = await regenResp.json();
    assert.notEqual(regen.plaintextKey, created.plaintextKey);

    // Old key rejected immediately
    const oldAfterRegen = await fetch(`${ctx.baseUrl}/v1/models`, {
      headers: { Authorization: `Bearer ${created.plaintextKey}` },
    });
    assert.equal(oldAfterRegen.status, 401);

    // Revoke new key
    const revResp = await fetch(
      `${ctx.baseUrl}/v1/developer/keys/${regen.keyRecord.id}/revoke`,
      { method: "POST", headers: { Authorization: "Bearer token-alice" } }
    );
    assert.equal(revResp.status, 200);
    const afterRevoke = await fetch(`${ctx.baseUrl}/v1/models`, {
      headers: { Authorization: `Bearer ${regen.plaintextKey}` },
    });
    assert.equal(afterRevoke.status, 401);
  });

  test("5. Credit deduction & automatic refund when provider fails", async () => {
    const keyResp = await fetch(`${ctx.baseUrl}/v1/developer/keys`, {
      method: "POST",
      headers: { Authorization: "Bearer token-alice", "Content-Type": "application/json" },
      body: JSON.stringify({ name: "Chat Key" }),
    });
    const { plaintextKey } = await keyResp.json();

    // Successful chat deducts 2 credits (100 -> 98)
    const chatResp = await fetch(`${ctx.baseUrl}/v1/chat/completions`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${plaintextKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: "gemini-3.8-flash",
        messages: [{ role: "user", content: "Hello" }],
      }),
    });
    assert.equal(chatResp.status, 200);
    const chatJson = await chatResp.json();
    assert.equal(chatJson.cerevix_billing.credits_charged, 2);
    assert.equal(chatJson.cerevix_billing.credits_remaining, 98);

    // Simulated upstream provider outage triggers automatic refund (98 -> 96 -> 98)
    ctx.fakeFetch.setFailGemini(true);
    const failResp = await fetch(`${ctx.baseUrl}/v1/chat/completions`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${plaintextKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: "gemini-3.8-flash",
        messages: [{ role: "user", content: "Fail gracefully" }],
      }),
    });
    assert.equal(failResp.status, 503);
    const failJson = await failResp.json();
    assert.equal(failJson.credits_refunded, 2);

    const wallet = await ctx.store.getDoc("credit_wallets", "alice_1");
    assert.equal(wallet.balance, 98);
    assert.equal(wallet.lifetimeRefunded, 2);
  });

  test("6-12. Razorpay subscription lifecycle: create, tampered verify fail, valid verify, webhooks, duplicate prevention, renewal, failure & cancellation", async () => {
    // 6. Create Razorpay subscription (must stay CREATED, user stays FREE)
    const createSubResp = await fetch(`${ctx.baseUrl}/v1/billing/subscriptions`, {
      method: "POST",
      headers: { Authorization: "Bearer token-alice", "Content-Type": "application/json" },
      body: JSON.stringify({ planId: "PRO", billingInterval: "MONTHLY" }),
    });
    assert.equal(createSubResp.status, 201);
    const createdSub = await createSubResp.json();
    const rzpSubId = createdSub.checkout.razorpaySubscriptionId;
    const localSubId = createdSub.subscription.id;

    let aliceUser = await ctx.store.getDoc("users", "alice_1");
    assert.equal(aliceUser.planId, "FREE");

    // 7a. Tampered signature rejected
    const badVerify = await fetch(`${ctx.baseUrl}/v1/billing/verify`, {
      method: "POST",
      headers: { Authorization: "Bearer token-alice", "Content-Type": "application/json" },
      body: JSON.stringify({
        razorpayPaymentId: "pay_ok_1",
        razorpaySubscriptionId: rzpSubId,
        razorpaySignature: "0000000000000000000000000000000000000000000000000000000000000000",
      }),
    });
    assert.equal(badVerify.status, 400);

    // 7b. Pending payment does NOT grant PRO plan
    ctx.fakeFetch.setSubscriptionStatus(rzpSubId, "created");
    const pendingSig = crypto
      .createHmac("sha256", TEST_RAZORPAY_SECRET)
      .update(`pay_pending_1|${rzpSubId}`)
      .digest("hex");
    const pendingVerify = await fetch(`${ctx.baseUrl}/v1/billing/verify`, {
      method: "POST",
      headers: { Authorization: "Bearer token-alice", "Content-Type": "application/json" },
      body: JSON.stringify({
        razorpayPaymentId: "pay_pending_1",
        razorpaySubscriptionId: rzpSubId,
        razorpaySignature: pendingSig,
      }),
    });
    assert.equal(pendingVerify.status, 402);
    aliceUser = await ctx.store.getDoc("users", "alice_1");
    assert.equal(aliceUser.planId, "FREE");

    // 7c. Valid captured payment + active Razorpay status upgrades user & grants 2,500 credits
    ctx.fakeFetch.setSubscriptionStatus(rzpSubId, "active");
    const goodSig = crypto
      .createHmac("sha256", TEST_RAZORPAY_SECRET)
      .update(`pay_captured_1|${rzpSubId}`)
      .digest("hex");
    const goodVerify = await fetch(`${ctx.baseUrl}/v1/billing/verify`, {
      method: "POST",
      headers: { Authorization: "Bearer token-alice", "Content-Type": "application/json" },
      body: JSON.stringify({
        razorpayPaymentId: "pay_captured_1",
        razorpaySubscriptionId: rzpSubId,
        razorpaySignature: goodSig,
      }),
    });
    assert.equal(goodVerify.status, 200);
    aliceUser = await ctx.store.getDoc("users", "alice_1");
    assert.equal(aliceUser.planId, "PRO");
    assert.equal(aliceUser.creditBalance, 2600);

    // 8. Invalid webhook signature rejected 401
    const renewBody = JSON.stringify({
      event: "subscription.charged",
      created_at: 1700000000,
      payload: {
        subscription: { entity: { id: rzpSubId, current_end: Math.floor(Date.now() / 1000) + 60 * 86400 } },
        payment: { entity: { id: "pay_renew_1", amount: 49900, method: "upi" } },
      },
    });
    const invalidWebhook = await fetch(`${ctx.baseUrl}/v1/webhooks/razorpay`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Razorpay-Signature": "deadbeef",
        "X-Razorpay-Event-Id": "evt_renew_1",
      },
      body: renewBody,
    });
    assert.equal(invalidWebhook.status, 401);

    // 9 & 10. Valid renewal webhook adds +2500 credits ONCE; duplicate delivery is idempotent
    const validHookSig = crypto
      .createHmac("sha256", TEST_WEBHOOK_SECRET)
      .update(renewBody)
      .digest("hex");
    const hook1 = await fetch(`${ctx.baseUrl}/v1/webhooks/razorpay`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Razorpay-Signature": validHookSig,
        "X-Razorpay-Event-Id": "evt_renew_1",
      },
      body: renewBody,
    });
    assert.equal(hook1.status, 200);
    assert.equal((await hook1.json()).duplicate, false);

    const hook2 = await fetch(`${ctx.baseUrl}/v1/webhooks/razorpay`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Razorpay-Signature": validHookSig,
        "X-Razorpay-Event-Id": "evt_renew_1",
      },
      body: renewBody,
    });
    assert.equal(hook2.status, 200);
    assert.equal((await hook2.json()).duplicate, true);
    aliceUser = await ctx.store.getDoc("users", "alice_1");
    assert.equal(aliceUser.creditBalance, 5100); // 2600 + 2500 once only!

    // 12. Failed payment webhook records FAILED payment & marks subscription PENDING
    const failHookBody = JSON.stringify({
      event: "payment.failed",
      created_at: 1700000100,
      payload: {
        payment: {
          entity: {
            id: "pay_fail_9",
            subscription_id: rzpSubId,
            amount: 49900,
            error_description: "Insufficient funds in UPI account",
          },
        },
      },
    });
    const failHookSig = crypto
      .createHmac("sha256", TEST_WEBHOOK_SECRET)
      .update(failHookBody)
      .digest("hex");
    const failHookResp = await fetch(`${ctx.baseUrl}/v1/webhooks/razorpay`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Razorpay-Signature": failHookSig,
        "X-Razorpay-Event-Id": "evt_fail_9",
      },
      body: failHookBody,
    });
    assert.equal(failHookResp.status, 200);
    const failedPayments = await ctx.store.queryDocs("payments", (p) => p.status === "FAILED");
    assert.equal(failedPayments.length, 1);

    // 11. Cancellation
    const cancelResp = await fetch(`${ctx.baseUrl}/v1/billing/subscriptions/${localSubId}/cancel`, {
      method: "POST",
      headers: { Authorization: "Bearer token-alice", "Content-Type": "application/json" },
      body: JSON.stringify({ cancelAtCycleEnd: false }),
    });
    assert.equal(cancelResp.status, 200);
    aliceUser = await ctx.store.getDoc("users", "alice_1");
    assert.equal(aliceUser.planId, "FREE");
  });

  test("13. Owner-only admin authorization blocks non-admins and spoofed emails, never exposes secrets", async () => {
    const aliceAdminTry = await fetch(`${ctx.baseUrl}/v1/admin/overview`, {
      headers: { Authorization: "Bearer token-alice" },
    });
    assert.equal(aliceAdminTry.status, 403);

    const spoofAdminTry = await fetch(`${ctx.baseUrl}/v1/admin/providers`, {
      headers: { Authorization: "Bearer token-spoof-admin" },
    });
    assert.equal(spoofAdminTry.status, 403);

    // Verified owner succeeds
    const ownerProviders = await fetch(`${ctx.baseUrl}/v1/admin/providers`, {
      headers: { Authorization: "Bearer token-admin" },
    });
    assert.equal(ownerProviders.status, 200);
    const provJson = await ownerProviders.json();
    assert.ok(provJson.providers.length >= 4);
    assert.equal(JSON.stringify(provJson).includes("test_gemini_secret"), false);

    // Owner updates provider credential in runtime store; Firestore doc & response never expose raw secret
    const upsertProv = await fetch(`${ctx.baseUrl}/v1/admin/providers`, {
      method: "POST",
      headers: { Authorization: "Bearer token-admin", "Content-Type": "application/json" },
      body: JSON.stringify({
        id: "openai_compat",
        name: "OpenAI Gateway",
        adapterType: "OPENAI_COMPATIBLE",
        baseUrl: "https://api.openai.com/v1",
        apiCredential: "sk-super-secret-value",
        enabled: true,
      }),
    });
    assert.equal(upsertProv.status, 200);
    const upsertJson = await upsertProv.json();
    assert.equal(upsertJson.provider.hasServerCredential, true);
    assert.equal(JSON.stringify(upsertJson).includes("sk-super-secret-value"), false);

    // Owner adjusts user credits with audit log
    const adjResp = await fetch(`${ctx.baseUrl}/v1/admin/credits/adjust`, {
      method: "POST",
      headers: { Authorization: "Bearer token-admin", "Content-Type": "application/json" },
      body: JSON.stringify({ targetUid: "alice_1", delta: 250, reason: "Enterprise SLA bonus" }),
    });
    assert.equal(adjResp.status, 200);
    const adjJson = await adjResp.json();
    assert.equal(adjJson.newBalance, 350);
  });
});
