const crypto = require("node:crypto");

class InsufficientCreditsError extends Error {
  constructor(message) {
    super(message);
    this.name = "InsufficientCreditsError";
    this.statusCode = 402;
  }
}

class WalletService {
  constructor(store) {
    this.store = store;
  }

  async ensureWallet(uid) {
    return this.store.runTransaction(async (tx) => {
      const existing = await tx.get("credit_wallets", uid);
      if (existing) return existing;
      const user = await tx.get("users", uid);
      const initialBal = Number(user?.creditBalance ?? 100);
      const wallet = {
        id: uid,
        userId: uid,
        balance: initialBal,
        subscriptionCredits: 0,
        purchasedCredits: 0,
        lifetimeUsed: Number(user?.totalCreditsUsed ?? 0),
        lifetimeRefunded: 0,
        updatedAt: new Date().toISOString(),
      };
      await tx.set("credit_wallets", uid, wallet);
      return wallet;
    });
  }

  async reserveAndDeductCredits({ uid, cost, toolId, description }) {
    const debit = Math.max(0, Number(cost || 0));
    return this.store.runTransaction(async (tx) => {
      const user = await tx.get("users", uid);
      if (user && user.status === "SUSPENDED") {
        const err = new Error("Account is suspended.");
        err.statusCode = 403;
        throw err;
      }
      const existingWallet = await tx.get("credit_wallets", uid);
      const currentBalance = existingWallet
        ? Number(existingWallet.balance || 0)
        : Number(user?.creditBalance ?? 100);

      if (currentBalance < debit) {
        throw new InsufficientCreditsError(
          `Insufficient credits (${currentBalance} available, ${debit} required).`
        );
      }

      const now = new Date().toISOString();
      const newBalance = currentBalance - debit;
      const lifetimeUsed = Number(existingWallet?.lifetimeUsed || user?.totalCreditsUsed || 0) + debit;

      const updatedWallet = {
        id: uid,
        userId: uid,
        balance: newBalance,
        subscriptionCredits: Number(existingWallet?.subscriptionCredits || 0),
        purchasedCredits: Number(existingWallet?.purchasedCredits || 0),
        lifetimeUsed,
        lifetimeRefunded: Number(existingWallet?.lifetimeRefunded || 0),
        updatedAt: now,
      };
      await tx.set("credit_wallets", uid, updatedWallet);

      if (user) {
        await tx.set("users", uid, {
          ...user,
          creditBalance: newBalance,
          totalCreditsUsed: lifetimeUsed,
          updatedAt: now,
        });
      }

      const dev = await tx.get("developers", uid);
      if (dev) {
        await tx.set("developers", uid, {
          ...dev,
          apiCreditsBalance: newBalance,
          totalApiRequests: Number(dev.totalApiRequests || 0) + 1,
          updatedAt: now,
        });
      }

      const txId = `ctx_${crypto.randomBytes(8).toString("hex")}`;
      await tx.set("credit_transactions", txId, {
        id: txId,
        userId: uid,
        txType: "USAGE_DEBIT",
        amount: -debit,
        balanceAfter: newBalance,
        toolId: String(toolId || "api").slice(0, 80),
        description: String(description || "API usage debit").slice(0, 240),
        createdAt: now,
      });

      return { newBalance, wallet: updatedWallet };
    });
  }

  async refundCredits({ uid, amount, toolId, description }) {
    const creditBack = Math.max(0, Number(amount || 0));
    if (creditBack === 0) return null;
    return this.store.runTransaction(async (tx) => {
      const now = new Date().toISOString();
      const user = await tx.get("users", uid);
      const existingWallet = await tx.get("credit_wallets", uid);
      const currentBalance = existingWallet
        ? Number(existingWallet.balance || 0)
        : Number(user?.creditBalance ?? 0);
      const newBalance = currentBalance + creditBack;
      const lifetimeRefunded = Number(existingWallet?.lifetimeRefunded || 0) + creditBack;

      const updatedWallet = {
        id: uid,
        userId: uid,
        balance: newBalance,
        subscriptionCredits: Number(existingWallet?.subscriptionCredits || 0),
        purchasedCredits: Number(existingWallet?.purchasedCredits || 0),
        lifetimeUsed: Number(existingWallet?.lifetimeUsed || 0),
        lifetimeRefunded,
        updatedAt: now,
      };
      await tx.set("credit_wallets", uid, updatedWallet);

      if (user) {
        await tx.set("users", uid, {
          ...user,
          creditBalance: newBalance,
          updatedAt: now,
        });
      }
      const dev = await tx.get("developers", uid);
      if (dev) {
        await tx.set("developers", uid, {
          ...dev,
          apiCreditsBalance: newBalance,
          updatedAt: now,
        });
      }

      const txId = `ctx_${crypto.randomBytes(8).toString("hex")}`;
      await tx.set("credit_transactions", txId, {
        id: txId,
        userId: uid,
        txType: "GENERATION_REFUND",
        amount: creditBack,
        balanceAfter: newBalance,
        toolId: String(toolId || "refund").slice(0, 80),
        description: String(description || "Automatic refund for failed generation").slice(0, 240),
        createdAt: now,
      });

      return { newBalance, wallet: updatedWallet };
    });
  }

  async adminAdjustCredits({ targetUid, delta, reason, actorUid, actorEmail }) {
    const amount = Number(delta || 0);
    return this.store.runTransaction(async (tx) => {
      const now = new Date().toISOString();
      const user = await tx.get("users", targetUid);
      const existingWallet = await tx.get("credit_wallets", targetUid);
      const currentBalance = existingWallet
        ? Number(existingWallet.balance || 0)
        : Number(user?.creditBalance ?? 0);
      const newBalance = Math.max(0, currentBalance + amount);

      const updatedWallet = {
        id: targetUid,
        userId: targetUid,
        balance: newBalance,
        subscriptionCredits: Number(existingWallet?.subscriptionCredits || 0),
        purchasedCredits: Number(existingWallet?.purchasedCredits || 0),
        lifetimeUsed: Number(existingWallet?.lifetimeUsed || 0),
        lifetimeRefunded: Number(existingWallet?.lifetimeRefunded || 0),
        updatedAt: now,
      };
      await tx.set("credit_wallets", targetUid, updatedWallet);

      if (user) {
        await tx.set("users", targetUid, {
          ...user,
          creditBalance: newBalance,
          updatedAt: now,
        });
      }
      const dev = await tx.get("developers", targetUid);
      if (dev) {
        await tx.set("developers", targetUid, {
          ...dev,
          apiCreditsBalance: newBalance,
          updatedAt: now,
        });
      }

      const txId = `ctx_${crypto.randomBytes(8).toString("hex")}`;
      await tx.set("credit_transactions", txId, {
        id: txId,
        userId: targetUid,
        txType: amount >= 0 ? "ADMIN_GRANT" : "ADMIN_ADJUSTMENT",
        amount,
        balanceAfter: newBalance,
        toolId: "admin_console",
        description: String(reason || "Admin credit adjustment").slice(0, 240),
        createdAt: now,
      });

      const logId = `aud_${crypto.randomBytes(8).toString("hex")}`;
      await tx.set("audit_logs", logId, {
        id: logId,
        actorUid,
        actorEmail,
        action: "ADMIN_CREDIT_ADJUSTMENT",
        targetPath: `credit_wallets/${targetUid}`,
        details: `Delta=${amount}, BalanceAfter=${newBalance}, Reason=${reason}`,
        createdAt: now,
      });

      return { newBalance, wallet: updatedWallet };
    });
  }
}

module.exports = { WalletService, InsufficientCreditsError };
