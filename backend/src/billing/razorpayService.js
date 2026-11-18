const crypto = require("node:crypto");

class RazorpayService {
  constructor({ config, store, fetchImpl = fetch }) {
    this.config = config;
    this.store = store;
    this.fetchImpl = fetchImpl;
  }

  isConfigured() {
    return Boolean(this.config.razorpayKeyId && this.config.razorpayKeySecret);
  }

  isWebhookConfigured() {
    return Boolean(this.config.razorpayWebhookSecret);
  }

  _authHeader() {
    const creds = Buffer.from(
      `${this.config.razorpayKeyId}:${this.config.razorpayKeySecret}`
    ).toString("base64");
    return `Basic ${creds}`;
  }

  async _razorpayRequest(method, path, body = null) {
    if (!this.isConfigured()) {
      const err = new Error(
        "Razorpay API credentials (RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET) are not configured on the backend."
      );
      err.statusCode = 503;
      throw err;
    }
    const resp = await this.fetchImpl(`https://api.razorpay.com/v1${path}`, {
      method,
      headers: {
        Authorization: this._authHeader(),
        "Content-Type": "application/json",
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    const data = await resp.json();
    if (!resp.ok) {
      const err = new Error(data?.error?.description || `Razorpay API error (${resp.status})`);
      err.statusCode = resp.status;
      throw err;
    }
    return data;
  }

  verifySubscriptionCheckoutSignature({ razorpayPaymentId, razorpaySubscriptionId, razorpaySignature }) {
    if (!this.config.razorpayKeySecret || !razorpayPaymentId || !razorpaySubscriptionId || !razorpaySignature) {
      return false;
    }
    const expected = crypto
      .createHmac("sha256", this.config.razorpayKeySecret)
      .update(`${razorpayPaymentId}|${razorpaySubscriptionId}`)
      .digest("hex");
    return this._timingSafeHexEqual(expected, razorpaySignature);
  }

  verifyOrderCheckoutSignature({ razorpayOrderId, razorpayPaymentId, razorpaySignature }) {
    if (!this.config.razorpayKeySecret || !razorpayOrderId || !razorpayPaymentId || !razorpaySignature) {
      return false;
    }
    const expected = crypto
      .createHmac("sha256", this.config.razorpayKeySecret)
      .update(`${razorpayOrderId}|${razorpayPaymentId}`)
      .digest("hex");
    return this._timingSafeHexEqual(expected, razorpaySignature);
  }

  verifyWebhookSignature(rawBody, signatureHeader) {
    if (!this.config.razorpayWebhookSecret || !rawBody || !signatureHeader) {
      return false;
    }
    const expected = crypto
      .createHmac("sha256", this.config.razorpayWebhookSecret)
      .update(rawBody)
      .digest("hex");
    return this._timingSafeHexEqual(expected, signatureHeader);
  }

  _timingSafeHexEqual(a, b) {
    if (!a || !b || a.length !== b.length) return false;
    const bufA = Buffer.from(String(a), "hex");
    const bufB = Buffer.from(String(b), "hex");
    if (bufA.length !== bufB.length) return false;
    return crypto.timingSafeEqual(bufA, bufB);
  }

  async createRazorpayPlan({ name, amountInr, interval }) {
    const period = interval === "ANNUAL" ? "yearly" : "monthly";
    return this._razorpayRequest("POST", "/plans", {
      period,
      interval: 1,
      item: {
        name: `CEREVIX AI ${name} (${period})`,
        amount: Number(amountInr) * 100,
        currency: "INR",
        description: `CEREVIX AI ${name} subscription`,
      },
    });
  }

  async createSubscriptionCheckout({ userId, email, plan, billingInterval }) {
    if (!plan || !plan.active) {
      const err = new Error("Selected subscription plan is unavailable.");
      err.statusCode = 404;
      throw err;
    }
    const amountInr = billingInterval === "ANNUAL" ? plan.annualPriceInr : plan.monthlyPriceInr;
    if (amountInr <= 0) {
      const err = new Error("Free or custom-pricing plans do not use self-serve checkout.");
      err.statusCode = 400;
      throw err;
    }

    let rzpPlanId =
      billingInterval === "ANNUAL" ? plan.razorpayAnnualPlanId : plan.razorpayMonthlyPlanId;
    if (!rzpPlanId) {
      const createdPlan = await this.createRazorpayPlan({
        name: plan.name,
        amountInr,
        interval: billingInterval,
      });
      rzpPlanId = createdPlan.id;
      const patch =
        billingInterval === "ANNUAL"
          ? { razorpayAnnualPlanId: rzpPlanId }
          : { razorpayMonthlyPlanId: rzpPlanId };
      await this.store.updateDoc("subscription_plans", plan.id, {
        ...patch,
        updatedAt: new Date().toISOString(),
      });
    }

    const rzpSub = await this._razorpayRequest("POST", "/subscriptions", {
      plan_id: rzpPlanId,
      total_count: billingInterval === "ANNUAL" ? 10 : 120,
      customer_notify: 1,
      notes: {
        userId,
        planId: plan.id,
        planCategory: plan.planCategory || "CONSUMER",
        billingInterval,
        email: email || "",
      },
    });

    const now = new Date().toISOString();
    const localSubId = `sub_${crypto.randomBytes(10).toString("hex")}`;
    const record = {
      id: localSubId,
      userId,
      planId: plan.id,
      planCategory: plan.planCategory || "CONSUMER",
      billingInterval,
      status: "CREATED",
      razorpaySubscriptionId: rzpSub.id,
      razorpayPlanId: rzpPlanId,
      amountInr,
      shortUrl: rzpSub.short_url || "",
      nextBillingAt: null,
      cancelAtPeriodEnd: false,
      createdAt: now,
      updatedAt: now,
    };
    await this.store.setDoc("subscriptions", localSubId, record);

    return {
      subscription: record,
      checkout: {
        razorpayKeyId: this.config.razorpayKeyId,
        razorpaySubscriptionId: rzpSub.id,
        amountPaise: amountInr * 100,
        currency: "INR",
        shortUrl: rzpSub.short_url || "",
        checkoutPageUrl: `${this.config.appBaseUrl.replace(/\/$/, "")}/v1/billing/checkout-page?subscription_id=${encodeURIComponent(rzpSub.id)}&local_id=${encodeURIComponent(localSubId)}`,
      },
    };
  }

  async createCreditOrder({ userId, pack }) {
    const receipt = `CRVX-ORD-${Date.now()}`;
    const rzpOrder = await this._razorpayRequest("POST", "/orders", {
      amount: Number(pack.priceInr) * 100,
      currency: "INR",
      receipt,
      notes: {
        userId,
        packId: pack.id,
        credits: String(pack.credits),
        kind: "CREDIT_PACK",
      },
    });

    const now = new Date().toISOString();
    const paymentId = `payrec_${crypto.randomBytes(10).toString("hex")}`;
    const pendingPayment = {
      id: paymentId,
      userId,
      subscriptionId: "",
      planId: pack.id,
      creditsToGrant: Number(pack.credits),
      amountPaise: Number(pack.priceInr) * 100,
      currency: "INR",
      status: "CREATED",
      razorpayOrderId: rzpOrder.id,
      razorpayPaymentId: "",
      razorpaySubscriptionId: "",
      method: "",
      receiptNumber: receipt,
      refundId: "",
      refundAmountPaise: 0,
      failureReason: "",
      createdAt: now,
      updatedAt: now,
    };
    await this.store.setDoc("payments", paymentId, pendingPayment);

    return {
      payment: pendingPayment,
      checkout: {
        razorpayKeyId: this.config.razorpayKeyId,
        razorpayOrderId: rzpOrder.id,
        amountPaise: pendingPayment.amountPaise,
        currency: "INR",
        checkoutPageUrl: `${this.config.appBaseUrl.replace(/\/$/, "")}/v1/billing/checkout-page?order_id=${encodeURIComponent(rzpOrder.id)}&local_id=${encodeURIComponent(paymentId)}`,
      },
    };
  }

  async verifyAndSyncCheckout({
    userId,
    razorpayPaymentId,
    razorpaySubscriptionId = "",
    razorpayOrderId = "",
    razorpaySignature,
  }) {
    if (razorpaySubscriptionId) {
      const sigOk = this.verifySubscriptionCheckoutSignature({
        razorpayPaymentId,
        razorpaySubscriptionId,
        razorpaySignature,
      });
      if (!sigOk) {
        const err = new Error("Invalid Razorpay subscription signature.");
        err.statusCode = 400;
        throw err;
      }

      // Never trust frontend status alone: verify authoritative subscription & payment from Razorpay API
      const [rzpSub, rzpPay] = await Promise.all([
        this._razorpayRequest("GET", `/subscriptions/${encodeURIComponent(razorpaySubscriptionId)}`),
        this._razorpayRequest("GET", `/payments/${encodeURIComponent(razorpayPaymentId)}`),
      ]);

      const mappedSubStatus = String(rzpSub.status || "PENDING").toUpperCase();
      const payCaptured = rzpPay.status === "captured";
      const subActive = mappedSubStatus === "ACTIVE" || mappedSubStatus === "AUTHENTICATED";

      if (!payCaptured && !subActive) {
        const err = new Error(
          `Payment is currently '${rzpPay.status}' and subscription is '${rzpSub.status}'. Paid entitlements will activate only after capture.`
        );
        err.statusCode = 402;
        throw err;
      }

      const result = await this._activateSubscriptionAndGrantCredits({
        razorpaySubscriptionId,
        razorpayPaymentId,
        paymentMethod: rzpPay.method || "upi/card",
        amountPaise: Number(rzpPay.amount || 0),
        periodEndEpochSec: rzpSub.current_end || null,
        idempotencyEventId: `verify_sub_${razorpayPaymentId}`,
      });
      return { verified: true, ...result };
    }

    if (razorpayOrderId) {
      const sigOk = this.verifyOrderCheckoutSignature({
        razorpayOrderId,
        razorpayPaymentId,
        razorpaySignature,
      });
      if (!sigOk) {
        const err = new Error("Invalid Razorpay order signature.");
        err.statusCode = 400;
        throw err;
      }
      const rzpPay = await this._razorpayRequest(
        "GET",
        `/payments/${encodeURIComponent(razorpayPaymentId)}`
      );
      if (rzpPay.status !== "captured") {
        const err = new Error(`Payment not captured yet (status: ${rzpPay.status}).`);
        err.statusCode = 402;
        throw err;
      }
      const result = await this._captureCreditOrder({
        userId,
        razorpayOrderId,
        razorpayPaymentId,
        paymentMethod: rzpPay.method || "upi/card",
        amountPaise: Number(rzpPay.amount || 0),
        idempotencyEventId: `verify_ord_${razorpayPaymentId}`,
      });
      return { verified: true, ...result };
    }

    const err = new Error("Missing razorpaySubscriptionId or razorpayOrderId.");
    err.statusCode = 400;
    throw err;
  }

  async processWebhook({ rawBody, signature, eventId }) {
    if (!this.verifyWebhookSignature(rawBody, signature)) {
      const err = new Error("Invalid Razorpay webhook signature.");
      err.statusCode = 401;
      throw err;
    }

    const payload = JSON.parse(rawBody);
    const resolvedEventId =
      eventId ||
      `${payload.event}_${payload.created_at}_${crypto
        .createHash("sha256")
        .update(rawBody)
        .digest("hex")
        .slice(0, 12)}`;
    const payloadHash = crypto.createHash("sha256").update(rawBody).digest("hex");

    // Idempotency lock inside transaction
    const alreadyProcessed = await this.store.runTransaction(async (tx) => {
      const existing = await tx.get("payment_events", resolvedEventId);
      if (existing) return true;
      await tx.set("payment_events", resolvedEventId, {
        id: resolvedEventId,
        eventId: resolvedEventId,
        eventType: payload.event || "unknown",
        signatureVerified: true,
        payloadHash,
        status: "PROCESSING",
        processedAt: new Date().toISOString(),
      });
      return false;
    });

    if (alreadyProcessed) {
      return { ok: true, duplicate: true, eventId: resolvedEventId };
    }

    const eventType = payload.event;
    const subEntity = payload?.payload?.subscription?.entity;
    const payEntity = payload?.payload?.payment?.entity;
    const refundEntity = payload?.payload?.refund?.entity;

    try {
      if (eventType === "subscription.activated" || eventType === "subscription.charged") {
        await this._activateSubscriptionAndGrantCredits({
          razorpaySubscriptionId: subEntity?.id,
          razorpayPaymentId: payEntity?.id || `subevt_${resolvedEventId}`,
          paymentMethod: payEntity?.method || "subscription_autopay",
          amountPaise: Number(payEntity?.amount || 0),
          periodEndEpochSec: subEntity?.current_end || null,
          idempotencyEventId: resolvedEventId,
        });
      } else if (eventType === "subscription.pending" || eventType === "subscription.halted") {
        await this._markSubscriptionState(subEntity?.id, eventType === "subscription.pending" ? "PENDING" : "HALTED");
      } else if (eventType === "subscription.cancelled" || eventType === "subscription.completed") {
        await this._markSubscriptionState(
          subEntity?.id,
          eventType === "subscription.cancelled" ? "CANCELLED" : "EXPIRED",
          true
        );
      } else if (eventType === "payment.captured" && payEntity?.order_id) {
        await this._captureCreditOrder({
          userId: payEntity?.notes?.userId || "",
          razorpayOrderId: payEntity.order_id,
          razorpayPaymentId: payEntity.id,
          paymentMethod: payEntity.method || "upi",
          amountPaise: Number(payEntity.amount || 0),
          idempotencyEventId: resolvedEventId,
        });
      } else if (eventType === "payment.failed") {
        await this._recordFailedPayment({
          userId: payEntity?.notes?.userId || "",
          razorpayOrderId: payEntity?.order_id || "",
          razorpayPaymentId: payEntity?.id || "",
          razorpaySubscriptionId: subEntity?.id || payEntity?.subscription_id || "",
          amountPaise: Number(payEntity?.amount || 0),
          reason: payEntity?.error_description || payEntity?.error_reason || "Payment failed",
        });
      } else if (eventType === "refund.processed" && refundEntity) {
        await this._recordRefund({
          razorpayPaymentId: refundEntity.payment_id,
          refundId: refundEntity.id,
          refundAmountPaise: Number(refundEntity.amount || 0),
        });
      }

      await this.store.updateDoc("payment_events", resolvedEventId, {
        status: "PROCESSED",
        processedAt: new Date().toISOString(),
      });
      return { ok: true, duplicate: false, eventId: resolvedEventId, eventType };
    } catch (err) {
      await this.store.updateDoc("payment_events", resolvedEventId, {
        status: `FAILED:${err.message}`.slice(0, 120),
        processedAt: new Date().toISOString(),
      });
      throw err;
    }
  }

  async _activateSubscriptionAndGrantCredits({
    razorpaySubscriptionId,
    razorpayPaymentId,
    paymentMethod,
    amountPaise,
    periodEndEpochSec,
    idempotencyEventId,
  }) {
    const subs = await this.store.queryDocs(
      "subscriptions",
      (s) => s.razorpaySubscriptionId === razorpaySubscriptionId
    );
    const sub = subs[0];
    if (!sub) {
      throw new Error(`Subscription record for Razorpay ID '${razorpaySubscriptionId}' not found.`);
    }
    const plan = await this.store.getDoc("subscription_plans", sub.planId);
    const creditsToGrant = Number(plan?.monthlyCredits || 0);
    const now = new Date().toISOString();
    const nextBillingAt = periodEndEpochSec
      ? new Date(Number(periodEndEpochSec) * 1000).toISOString()
      : new Date(Date.now() + (sub.billingInterval === "ANNUAL" ? 365 : 30) * 86400_000).toISOString();

    return this.store.runTransaction(async (tx) => {
      // Check duplicate paymentId capture
      const existingPays = await this.store.queryDocs(
        "payments",
        (p) => p.razorpayPaymentId && p.razorpayPaymentId === razorpayPaymentId && p.status === "CAPTURED"
      );
      if (existingPays.length > 0) {
        return { duplicatePayment: true, subscription: await tx.get("subscriptions", sub.id) };
      }

      const updatedSub = {
        ...sub,
        status: "ACTIVE",
        nextBillingAt,
        updatedAt: now,
      };
      await tx.set("subscriptions", sub.id, updatedSub);

      // Update user or developer entitlement
      const user = await tx.get("users", sub.userId);
      const wallet = (await tx.get("credit_wallets", sub.userId)) || {
        id: sub.userId,
        userId: sub.userId,
        balance: Number(user?.creditBalance || 0),
        subscriptionCredits: 0,
        purchasedCredits: 0,
        lifetimeUsed: Number(user?.totalCreditsUsed || 0),
        lifetimeRefunded: 0,
        updatedAt: now,
      };

      const newBalance = Number(wallet.balance || 0) + creditsToGrant;
      await tx.set("credit_wallets", sub.userId, {
        ...wallet,
        balance: newBalance,
        subscriptionCredits: Number(wallet.subscriptionCredits || 0) + creditsToGrant,
        updatedAt: now,
      });

      if (user) {
        await tx.set("users", sub.userId, {
          ...user,
          planId: sub.planCategory === "CONSUMER" ? sub.planId : user.planId,
          creditBalance: newBalance,
          updatedAt: now,
        });
      }

      if (sub.planCategory === "DEVELOPER") {
        const dev = await tx.get("developers", sub.userId);
        if (dev) {
          await tx.set("developers", sub.userId, {
            ...dev,
            developerPlanId: sub.planId,
            rateLimitRpm: Number(plan?.rateLimitRpm || dev.rateLimitRpm),
            apiCreditsBalance: Number(dev.apiCreditsBalance || 0) + creditsToGrant,
            updatedAt: now,
          });
        }
      }

      const payDocId = `pay_${crypto.randomBytes(8).toString("hex")}`;
      const receiptNumber = `CRVX-INV-${Date.now().toString().slice(-6)}`;
      const paymentDoc = {
        id: payDocId,
        userId: sub.userId,
        subscriptionId: sub.id,
        planId: sub.planId,
        amountPaise: amountPaise || Number(sub.amountInr) * 100,
        currency: "INR",
        status: "CAPTURED",
        razorpayOrderId: "",
        razorpayPaymentId,
        razorpaySubscriptionId,
        method: paymentMethod,
        receiptNumber,
        refundId: "",
        refundAmountPaise: 0,
        failureReason: "",
        idempotencyEventId,
        createdAt: now,
        updatedAt: now,
      };
      await tx.set("payments", payDocId, paymentDoc);

      const txId = `ctx_${crypto.randomBytes(8).toString("hex")}`;
      await tx.set("credit_transactions", txId, {
        id: txId,
        userId: sub.userId,
        txType: "SUBSCRIPTION_GRANT",
        amount: creditsToGrant,
        balanceAfter: newBalance,
        toolId: `subscription_${sub.planId}`,
        description: `${sub.planId} (${sub.billingInterval}) subscription activated — ${receiptNumber}`,
        createdAt: now,
      });

      return { subscription: updatedSub, payment: paymentDoc, newBalance };
    });
  }

  async _captureCreditOrder({
    userId,
    razorpayOrderId,
    razorpayPaymentId,
    paymentMethod,
    amountPaise,
    idempotencyEventId,
  }) {
    const matches = await this.store.queryDocs(
      "payments",
      (p) => p.razorpayOrderId === razorpayOrderId
    );
    const payDoc = matches[0];
    if (!payDoc) throw new Error(`Order '${razorpayOrderId}' not found.`);

    const targetUid = payDoc.userId || userId;
    const now = new Date().toISOString();

    return this.store.runTransaction(async (tx) => {
      const freshPay = await tx.get("payments", payDoc.id);
      if (freshPay.status === "CAPTURED") {
        return { duplicatePayment: true, payment: freshPay };
      }

      const creditsToAdd = Number(freshPay.creditsToGrant || 0);
      const user = await tx.get("users", targetUid);
      const wallet = (await tx.get("credit_wallets", targetUid)) || {
        id: targetUid,
        userId: targetUid,
        balance: Number(user?.creditBalance || 0),
        subscriptionCredits: 0,
        purchasedCredits: 0,
        lifetimeUsed: Number(user?.totalCreditsUsed || 0),
        lifetimeRefunded: 0,
        updatedAt: now,
      };
      const newBalance = Number(wallet.balance || 0) + creditsToAdd;

      await tx.set("credit_wallets", targetUid, {
        ...wallet,
        balance: newBalance,
        purchasedCredits: Number(wallet.purchasedCredits || 0) + creditsToAdd,
        updatedAt: now,
      });

      if (user) {
        await tx.set("users", targetUid, {
          ...user,
          creditBalance: newBalance,
          updatedAt: now,
        });
      }

      const updatedPay = {
        ...freshPay,
        status: "CAPTURED",
        razorpayPaymentId,
        method: paymentMethod,
        amountPaise: amountPaise || freshPay.amountPaise,
        idempotencyEventId,
        updatedAt: now,
      };
      await tx.set("payments", freshPay.id, updatedPay);

      const txId = `ctx_${crypto.randomBytes(8).toString("hex")}`;
      await tx.set("credit_transactions", txId, {
        id: txId,
        userId: targetUid,
        txType: "CREDIT_PURCHASE",
        amount: creditsToAdd,
        balanceAfter: newBalance,
        toolId: freshPay.planId || "credit_pack",
        description: `Purchased ${creditsToAdd} credits (${freshPay.receiptNumber})`,
        createdAt: now,
      });

      return { payment: updatedPay, newBalance };
    });
  }

  async _markSubscriptionState(razorpaySubscriptionId, status, cancelAtPeriodEnd = false) {
    if (!razorpaySubscriptionId) return;
    const subs = await this.store.queryDocs(
      "subscriptions",
      (s) => s.razorpaySubscriptionId === razorpaySubscriptionId
    );
    const sub = subs[0];
    if (!sub) return;
    const now = new Date().toISOString();
    await this.store.updateDoc("subscriptions", sub.id, {
      status,
      cancelAtPeriodEnd: cancelAtPeriodEnd || sub.cancelAtPeriodEnd,
      updatedAt: now,
    });
    if (status === "CANCELLED" || status === "EXPIRED") {
      const user = await this.store.getDoc("users", sub.userId);
      if (user && sub.planCategory === "CONSUMER") {
        await this.store.updateDoc("users", sub.userId, { planId: "FREE", updatedAt: now });
      }
      if (sub.planCategory === "DEVELOPER") {
        const dev = await this.store.getDoc("developers", sub.userId);
        if (dev) {
          await this.store.updateDoc("developers", sub.userId, {
            developerPlanId: "FREE_DEVELOPER",
            rateLimitRpm: 10,
            updatedAt: now,
          });
        }
      }
    }
  }

  async _recordFailedPayment({
    userId,
    razorpayOrderId,
    razorpayPaymentId,
    razorpaySubscriptionId,
    amountPaise,
    reason,
  }) {
    const now = new Date().toISOString();
    const existing = razorpayOrderId
      ? (await this.store.queryDocs("payments", (p) => p.razorpayOrderId === razorpayOrderId))[0]
      : null;

    if (existing) {
      await this.store.updateDoc("payments", existing.id, {
        status: "FAILED",
        razorpayPaymentId,
        failureReason: String(reason || "Payment failed").slice(0, 240),
        updatedAt: now,
      });
    } else {
      let resolvedUid = userId;
      let planId = "UNKNOWN";
      if (!resolvedUid && razorpaySubscriptionId) {
        const subs = await this.store.queryDocs(
          "subscriptions",
          (s) => s.razorpaySubscriptionId === razorpaySubscriptionId
        );
        if (subs[0]) {
          resolvedUid = subs[0].userId;
          planId = subs[0].planId;
        }
      }
      const id = `pay_fail_${crypto.randomBytes(8).toString("hex")}`;
      await this.store.setDoc("payments", id, {
        id,
        userId: resolvedUid || "unknown",
        subscriptionId: razorpaySubscriptionId,
        planId,
        amountPaise: amountPaise || 0,
        currency: "INR",
        status: "FAILED",
        razorpayOrderId,
        razorpayPaymentId,
        razorpaySubscriptionId,
        method: "",
        receiptNumber: `CRVX-FAIL-${Date.now().toString().slice(-6)}`,
        refundId: "",
        refundAmountPaise: 0,
        failureReason: String(reason || "Payment failed").slice(0, 240),
        createdAt: now,
        updatedAt: now,
      });
    }

    if (razorpaySubscriptionId) {
      await this._markSubscriptionState(razorpaySubscriptionId, "PENDING");
    }
  }

  async _recordRefund({ razorpayPaymentId, refundId, refundAmountPaise }) {
    const pays = await this.store.queryDocs(
      "payments",
      (p) => p.razorpayPaymentId === razorpayPaymentId
    );
    const pay = pays[0];
    if (!pay) return null;
    return this.store.updateDoc("payments", pay.id, {
      status: "REFUNDED",
      refundId,
      refundAmountPaise,
      updatedAt: new Date().toISOString(),
    });
  }

  async cancelSubscription({ subscriptionId, requesterUid, isAdmin = false, cancelAtCycleEnd = true }) {
    const sub = await this.store.getDoc("subscriptions", subscriptionId);
    if (!sub) {
      const err = new Error("Subscription not found");
      err.statusCode = 404;
      throw err;
    }
    if (!isAdmin && sub.userId !== requesterUid) {
      const err = new Error("Forbidden");
      err.statusCode = 403;
      throw err;
    }
    if (sub.razorpaySubscriptionId) {
      await this._razorpayRequest(
        "POST",
        `/subscriptions/${encodeURIComponent(sub.razorpaySubscriptionId)}/cancel`,
        { cancel_at_cycle_end: cancelAtCycleEnd ? 1 : 0 }
      );
    }
    const now = new Date().toISOString();
    const updated = await this.store.updateDoc("subscriptions", sub.id, {
      status: cancelAtCycleEnd ? "ACTIVE" : "CANCELLED",
      cancelAtPeriodEnd: cancelAtCycleEnd,
      updatedAt: now,
    });
    if (!cancelAtCycleEnd) {
      await this._markSubscriptionState(sub.razorpaySubscriptionId, "CANCELLED", true);
    }
    return updated;
  }

  async refundPayment({ paymentDocId, actorUid, actorEmail, reason = "Admin refund" }) {
    const pay = await this.store.getDoc("payments", paymentDocId);
    if (!pay) {
      const err = new Error("Payment record not found");
      err.statusCode = 404;
      throw err;
    }
    if (pay.status !== "CAPTURED" || !pay.razorpayPaymentId) {
      const err = new Error("Only captured Razorpay payments can be refunded.");
      err.statusCode = 400;
      throw err;
    }
    const refund = await this._razorpayRequest(
      "POST",
      `/payments/${encodeURIComponent(pay.razorpayPaymentId)}/refund`,
      { amount: pay.amountPaise, notes: { reason, actorEmail } }
    );
    const updated = await this.store.updateDoc("payments", pay.id, {
      status: "REFUNDED",
      refundId: refund.id,
      refundAmountPaise: Number(refund.amount || pay.amountPaise),
      updatedAt: new Date().toISOString(),
    });
    const logId = `aud_${crypto.randomBytes(8).toString("hex")}`;
    await this.store.setDoc("audit_logs", logId, {
      id: logId,
      actorUid,
      actorEmail,
      action: "REFUND_PAYMENT",
      targetPath: `payments/${pay.id}`,
      details: `Refunded ₹${(pay.amountPaise / 100).toFixed(2)} (${refund.id}) — ${reason}`,
      createdAt: new Date().toISOString(),
    });
    return updated;
  }

  generateReceiptHtml(payment) {
    const inr = (Number(payment.amountPaise || 0) / 100).toFixed(2);
    return `<!doctype html>
<html><head><meta charset="utf-8"><title>Receipt ${payment.receiptNumber}</title>
<style>body{font-family:system-ui,sans-serif;background:#030917;color:#f1f5f9;padding:32px}
.card{max-width:600px;margin:auto;background:#0b132b;border:1px solid #1e3a5f;border-radius:16px;padding:24px}
.cyan{color:#38bdf8}.row{display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid #172554}</style>
</head><body><div class="card">
<h2 class="cyan">CEREVIX AI — Tax Invoice / Receipt</h2>
<div class="row"><span>Receipt No.</span><strong>${payment.receiptNumber}</strong></div>
<div class="row"><span>Plan / Item</span><strong>${payment.planId}</strong></div>
<div class="row"><span>Amount</span><strong>INR ₹${inr}</strong></div>
<div class="row"><span>Status</span><strong>${payment.status}</strong></div>
<div class="row"><span>Razorpay Payment ID</span><strong>${payment.razorpayPaymentId || "-"}</strong></div>
<div class="row"><span>Payment Method</span><strong>${payment.method || "-"}</strong></div>
<div class="row"><span>Date</span><strong>${payment.createdAt}</strong></div>
</div></body></html>`;
  }
}

module.exports = { RazorpayService };
