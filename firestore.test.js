const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_EMAIL = "prakashprusty334@gmail.com";
const ADMIN_UID = "admin_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validUserPayload(uid, email = "alice@example.com") {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    email,
    displayName: "Alice Creator",
    role: "USER",
    status: "ACTIVE",
    planId: "FREE",
    creditBalance: 100,
    totalCreditsUsed: 0,
    preferredModel: "gemini-3.8-flash",
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated users cannot read or write any collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));
  await assertFails(unauthDb.collection("subscription_plans").get());
  await assertFails(unauthDb.collection("ai_providers").get());
  await assertFails(unauthDb.collection("payments").get());
  await assertFails(unauthDb.collection("payment_events").get());
  await assertFails(unauthDb.collection("audit_logs").get());
});

test("User registration enforces FREE plan, USER role, and 100 starter credits", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID))
  );

  // Reject self-assigned ADMIN role
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      ...validUserPayload(BOB_UID),
      userId: ALICE_UID,
      role: "ADMIN",
    })
  );

  // Reject inflated starter credits
  const otherDb = testEnv.authenticatedContext("charlie_999").firestore();
  await assertFails(
    otherDb.collection("users").doc("charlie_999").set({
      ...validUserPayload("charlie_999", "charlie@example.com"),
      creditBalance: 99999,
    })
  );
});

test("Owner can deduct credits and update profile, but cannot inflate credits or escalate role/plan", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      creditBalance: 95,
      totalCreditsUsed: 5,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      creditBalance: 500,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      planId: "BUSINESS",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "ADMIN",
      updatedAt: new Date(Date.now() - 400),
    })
  );
});

test("Cross-user isolation blocks reading or writing another user's data", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1").set({
      userId: ALICE_UID,
      title: "Architecture Chat",
      modelId: "gemini-3.8-flash",
      lastMessagePreview: "Hello Cerevix",
      messageCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("conversations").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("projects").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("credit_transactions").get());
  await assertFails(aliceDb.collection("users").get());
});

test("Billing, credit wallets, API keys, and webhook events enforce zero-trust server-only writes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);

  // Regular users cannot fake payments, subscriptions, or inflate credit_wallets
  await assertFails(
    aliceDb.collection("payments").doc("pay_fake").set({
      userId: ALICE_UID,
      planId: "PRO",
      amountPaise: 49900,
      currency: "INR",
      status: "CAPTURED",
      receiptNumber: "CRVX-FAKE",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("subscriptions").doc("sub_fake").set({
      userId: ALICE_UID,
      planId: "PRO",
      planCategory: "CONSUMER",
      billingInterval: "MONTHLY",
      status: "ACTIVE",
      amountInr: 499,
      cancelAtPeriodEnd: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("credit_wallets").doc(ALICE_UID).set({
      userId: ALICE_UID,
      balance: 999999,
      subscriptionCredits: 999999,
      purchasedCredits: 0,
      lifetimeUsed: 0,
      lifetimeRefunded: 0,
      updatedAt: now,
    })
  );
  await assertFails(aliceDb.collection("payment_events").doc("evt_1").get());
  await assertFails(
    aliceDb.collection("api_keys").doc("key_1").set({
      userId: ALICE_UID,
      developerId: ALICE_UID,
      name: "Self forged",
      keyPrefix: "crvx_live_1234",
      keyHash: "abc",
      rateLimitRpm: 10000,
      status: "ACTIVE",
      requestCount: 0,
      createdAt: now,
    })
  );

  // Developer can self-register only on FREE_DEVELOPER, cannot escalate developerPlanId
  await assertSucceeds(
    aliceDb.collection("developers").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      organizationName: "Alice Labs",
      developerPlanId: "FREE_DEVELOPER",
      status: "ACTIVE",
      rateLimitRpm: 10,
      monthlyApiQuota: 1000,
      apiCreditsBalance: 50,
      totalApiRequests: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("developers").doc(ALICE_UID).update({
      developerPlanId: "ENTERPRISE",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Admin security & provider credential isolation: verified admin succeeds, secrets forbidden in Firestore", async () => {
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_1", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  const realAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);

  const planPayload = {
    name: "PRO",
    planCategory: "CONSUMER",
    monthlyPriceInr: 499,
    annualPriceInr: 4990,
    monthlyCredits: 2500,
    rateLimitRpm: 60,
    badge: "Popular",
    features: ["Gemini 3.8 Flash + Image Studio", "2,500 monthly credits"],
    sortOrder: 2,
    active: true,
    updatedAt: now,
  };

  await assertFails(spoofedAdminDb.collection("subscription_plans").doc("PRO").set(planPayload));
  await assertFails(spoofedAdminDb.collection("ai_providers").get());
  await assertFails(aliceDb.collection("ai_providers").get());

  await assertSucceeds(realAdminDb.collection("subscription_plans").doc("PRO").set(planPayload));
  await assertSucceeds(realAdminDb.collection("users").get());

  // Admin can configure provider metadata without secrets
  await assertSucceeds(
    realAdminDb.collection("ai_providers").doc("gemini").set({
      name: "Google Gemini",
      adapterType: "GEMINI",
      baseUrl: "https://generativelanguage.googleapis.com",
      secretEnvRef: "GEMINI_API_KEY",
      hasServerCredential: true,
      enabled: true,
      priority: 1,
      fallbackProviderId: "",
      healthStatus: "HEALTHY",
      totalRequests: 0,
      totalErrors: 0,
      lastError: "",
      updatedAt: now,
    })
  );

  // Rules reject storing raw apiKey inside Firestore even by admin
  await assertFails(
    realAdminDb.collection("ai_providers").doc("openai").set({
      name: "OpenAI",
      adapterType: "OPENAI_COMPATIBLE",
      apiKey: "sk-must-not-be-stored-in-firestore",
      enabled: true,
      priority: 2,
      healthStatus: "HEALTHY",
      totalRequests: 0,
      totalErrors: 0,
      updatedAt: now,
    })
  );
});
