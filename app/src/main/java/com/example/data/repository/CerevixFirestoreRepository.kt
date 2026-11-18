package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AccountStatus
import com.example.data.model.AiModelCatalog
import com.example.data.model.AiProject
import com.example.data.model.AuditLog
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.CreditTransaction
import com.example.data.model.PlanTier
import com.example.data.model.ProjectCategory
import com.example.data.model.SenderRole
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SystemAnnouncement
import com.example.data.model.TxType
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CerevixFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun ensureUserProfileInitialized(uid: String): Result<Unit> {
        val path = "users/$uid"
        return try {
            val current = auth.currentUser
            check(current != null && current.uid == uid) { "Authenticated user mismatch" }
            val userRef = db.collection("users").document(uid)
            val snap = userRef.get().await()
            if (!snap.exists()) {
                val safeDisplayName = (current.displayName ?: current.email?.substringBefore("@") ?: "Cerevix Creator")
                    .trim()
                    .take(80)
                    .ifBlank { "Cerevix Creator" }
                val safeEmail = (current.email ?: "").trim().take(320)
                val photoUrl = current.photoUrl?.toString()?.take(500)

                val batch = db.batch()
                val profilePayload = mapOf(
                    "userId" to uid,
                    "email" to safeEmail,
                    "displayName" to safeDisplayName,
                    "photoUrl" to photoUrl,
                    "role" to UserRole.USER.name,
                    "status" to AccountStatus.ACTIVE.name,
                    "planId" to PlanTier.FREE.name,
                    "creditBalance" to 100L,
                    "totalCreditsUsed" to 0L,
                    "preferredModel" to AiModelCatalog.DEFAULT_TEXT_MODEL.id,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                batch.set(userRef, profilePayload)

                val txRef = userRef.collection("credit_transactions").document()
                val txPayload = mapOf(
                    "userId" to uid,
                    "txType" to TxType.STARTER_GRANT.name,
                    "amount" to 100L,
                    "balanceAfter" to 100L,
                    "toolId" to "onboarding",
                    "description" to "Welcome starter credits",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                batch.set(txRef, txPayload)
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    suspend fun isUserAdmin(uid: String): Boolean {
        val current = auth.currentUser
        if (current != null && current.isEmailVerified &&
            current.email.equals(AuthRepository.BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)
        ) {
            return true
        }
        return try {
            db.collection("admin_users").document(uid).get().await().exists()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun updateUserPreferences(
        uid: String,
        displayName: String,
        preferredModel: String
    ): Result<Unit> {
        val path = "users/$uid"
        return try {
            require(requireUserId() == uid)
            val cleanName = displayName.trim().take(80).ifBlank { "Cerevix Creator" }
            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to cleanName,
                    "preferredModel" to preferredModel.take(80),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- CREDIT LEDGER ---

    suspend fun commitCreditDeduction(
        uid: String,
        cost: Long,
        toolId: String,
        description: String
    ): Result<Long> {
        val userPath = "users/$uid"
        return try {
            require(requireUserId() == uid)
            require(cost in 0L..500L) { "Credit cost out of bounds" }
            if (cost == 0L) return Result.success(-1L)

            val userRef = db.collection("users").document(uid)
            if (!userRef.get().await().exists()) {
                ensureUserProfileInitialized(uid).getOrThrow()
            }

            val newBalance = db.runTransaction { tx ->
                val snap = tx.get(userRef)
                val status = snap.getString("status") ?: AccountStatus.ACTIVE.name
                if (status == AccountStatus.SUSPENDED.name) {
                    throw IllegalStateException("Your account is suspended. Contact an administrator.")
                }
                val currentBalance = snap.getLong("creditBalance") ?: 0L
                val totalUsed = snap.getLong("totalCreditsUsed") ?: 0L
                if (currentBalance < cost) {
                    throw IllegalStateException("Insufficient AI credits ($currentBalance available, $cost required).")
                }
                val updatedBalance = currentBalance - cost
                val updatedUsed = totalUsed + cost

                tx.update(
                    userRef,
                    mapOf(
                        "creditBalance" to updatedBalance,
                        "totalCreditsUsed" to updatedUsed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val ledgerRef = userRef.collection("credit_transactions").document()
                tx.set(
                    ledgerRef,
                    mapOf(
                        "userId" to uid,
                        "txType" to TxType.USAGE_DEBIT.name,
                        "amount" to -cost,
                        "balanceAfter" to updatedBalance,
                        "toolId" to toolId.take(80),
                        "description" to description.take(240),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                updatedBalance
            }.await()

            Result.success(newBalance)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userPath)
            Result.failure(e)
        }
    }

    fun observeCreditTransactions(uid: String): Flow<List<CreditTransaction>> = flow {
        val path = "users/$uid/credit_transactions"
        emitAll(
            db.collection("users").document(uid).collection("credit_transactions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CreditTransaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    // --- CONVERSATIONS & CHAT ---

    fun observeConversations(uid: String): Flow<List<Conversation>> = flow {
        val path = "users/$uid/conversations"
        emitAll(
            db.collection("users").document(uid).collection("conversations")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun createConversation(uid: String, title: String, modelId: String): Result<String> {
        val path = "users/$uid/conversations"
        return try {
            require(requireUserId() == uid)
            val docRef = db.collection("users").document(uid).collection("conversations").document()
            val safeModelId = modelId.trim().take(80).ifBlank { AiModelCatalog.DEFAULT_TEXT_MODEL.id }
            val payload = mapOf(
                "userId" to uid,
                "title" to title.trim().take(120).ifBlank { "New Conversation" },
                "modelId" to safeModelId,
                "lastMessagePreview" to "Start chatting with Cerevix AI",
                "messageCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun renameConversation(uid: String, conversationId: String, newTitle: String): Result<Unit> {
        val path = "users/$uid/conversations/$conversationId"
        return try {
            require(requireUserId() == uid)
            val clean = newTitle.trim().take(120)
            require(clean.isNotBlank()) { "Title cannot be empty" }
            db.document(path).update(
                mapOf(
                    "title" to clean,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(uid: String, conversationId: String): Result<Unit> {
        val path = "users/$uid/conversations/$conversationId"
        return try {
            require(requireUserId() == uid)
            val messagesSnap = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages").get().await()
            val batch = db.batch()
            messagesSnap.documents.forEach { batch.delete(it.reference) }
            batch.delete(db.document(path))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeMessages(uid: String, conversationId: String): Flow<List<ChatMessage>> = flow {
        val path = "users/$uid/conversations/$conversationId/messages"
        emitAll(
            db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun appendChatMessage(
        uid: String,
        conversationId: String,
        senderRole: SenderRole,
        content: String,
        modelId: String,
        attachmentLabel: String? = null,
        updateTitleIfFirst: String? = null
    ): Result<Unit> {
        val path = "users/$uid/conversations/$conversationId/messages"
        return try {
            require(requireUserId() == uid)
            val convRef = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
            val msgRef = convRef.collection("messages").document()
            val safeContent = content.trim().take(100000).ifBlank { "(empty)" }
            val safeModelId = modelId.trim().take(80).ifBlank { AiModelCatalog.DEFAULT_TEXT_MODEL.id }
            val msgPayload = mapOf(
                "userId" to uid,
                "senderRole" to senderRole.name,
                "content" to safeContent,
                "modelId" to safeModelId,
                "attachmentLabel" to attachmentLabel?.trim()?.take(200),
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            val convUpdate = mutableMapOf<String, Any>(
                "modelId" to safeModelId,
                "lastMessagePreview" to safeContent.replace("\n", " ").trim().take(180),
                "messageCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!updateTitleIfFirst.isNullOrBlank()) {
                convUpdate["title"] = updateTitleIfFirst.trim().take(60)
            }

            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            if (!convRef.get().await().exists()) {
                val createConvPayload = mapOf(
                    "userId" to uid,
                    "title" to (updateTitleIfFirst?.trim()?.take(60)?.ifBlank { null }
                        ?: safeContent.take(48).ifBlank { "New Conversation" }),
                    "modelId" to safeModelId,
                    "lastMessagePreview" to safeContent.replace("\n", " ").trim().take(180),
                    "messageCount" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(convRef, createConvPayload)
            } else {
                batch.update(convRef, convUpdate)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- SAVED AI PROJECTS ---

    fun observeProjects(uid: String): Flow<List<AiProject>> = flow {
        val path = "users/$uid/projects"
        emitAll(
            db.collection("users").document(uid).collection("projects")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AiProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun saveProject(
        uid: String,
        title: String,
        category: ProjectCategory,
        prompt: String,
        outputText: String,
        modelId: String,
        creditsCost: Long
    ): Result<String> {
        val path = "users/$uid/projects"
        return try {
            require(requireUserId() == uid)
            val docRef = db.collection("users").document(uid).collection("projects").document()
            val safeModelId = modelId.trim().take(80).ifBlank { AiModelCatalog.DEFAULT_TEXT_MODEL.id }
            val payload = mapOf(
                "userId" to uid,
                "title" to title.trim().take(140).ifBlank { "Untitled Creation" },
                "category" to category.name,
                "prompt" to prompt.take(20000),
                "outputText" to outputText.take(200000),
                "modelId" to safeModelId,
                "creditsCost" to creditsCost.coerceAtLeast(0L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(uid: String, projectId: String): Result<Unit> {
        val path = "users/$uid/projects/$projectId"
        return try {
            require(requireUserId() == uid)
            db.document(path).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearUserWorkspaceData(uid: String): Result<Unit> {
        val path = "users/$uid"
        return try {
            require(requireUserId() == uid)
            val userRef = db.collection("users").document(uid)
            val projects = userRef.collection("projects").get().await()
            val conversations = userRef.collection("conversations").get().await()
            val batch = db.batch()
            projects.documents.forEach { batch.delete(it.reference) }
            for (conv in conversations.documents) {
                val msgs = conv.reference.collection("messages").get().await()
                msgs.documents.forEach { batch.delete(it.reference) }
                batch.delete(conv.reference)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- PLANS & ANNOUNCEMENTS ---

    fun observeSubscriptionPlans(): Flow<List<SubscriptionPlan>> = flow {
        val path = "subscription_plans"
        emitAll(
            db.collection(path)
                .orderBy("sortOrder", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    val plans = snapshot.toObjects(
                        SubscriptionPlan::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    plans.ifEmpty { AiModelCatalog.defaultPlans }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, auth.currentUser?.uid ?: "")
                    throw error
                }
        )
    }

    fun observeAnnouncements(): Flow<List<SystemAnnouncement>> = flow {
        val path = "system_announcements"
        emitAll(
            db.collection(path)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(10)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SystemAnnouncement::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { it.active }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, auth.currentUser?.uid ?: "")
                    throw error
                }
        )
    }

    // --- ADMIN CAPABILITIES ---

    fun observeAllUsersAdmin(): Flow<List<UserProfile>> = flow {
        val path = "users"
        emitAll(
            db.collection(path)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, auth.currentUser?.uid ?: "")
                    throw error
                }
        )
    }

    fun observeAllAnnouncementsAdmin(): Flow<List<SystemAnnouncement>> = flow {
        val path = "system_announcements"
        emitAll(
            db.collection(path)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SystemAnnouncement::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, auth.currentUser?.uid ?: "")
                    throw error
                }
        )
    }

    fun observeAuditLogsAdmin(): Flow<List<AuditLog>> = flow {
        val path = "audit_logs"
        emitAll(
            db.collection(path)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AuditLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, auth.currentUser?.uid ?: "")
                    throw error
                }
        )
    }

    suspend fun adminSeedDefaultPlans(): Result<Unit> {
        return try {
            val batch = db.batch()
            AiModelCatalog.defaultPlans.forEach { plan ->
                val ref = db.collection("subscription_plans").document(plan.id)
                batch.set(
                    ref,
                    mapOf(
                        "name" to plan.name,
                        "planCategory" to plan.planCategory,
                        "monthlyPriceInr" to plan.monthlyPriceInr,
                        "annualPriceInr" to plan.annualPriceInr,
                        "monthlyCredits" to plan.monthlyCredits,
                        "rateLimitRpm" to plan.rateLimitRpm,
                        "razorpayMonthlyPlanId" to plan.razorpayMonthlyPlanId,
                        "razorpayAnnualPlanId" to plan.razorpayAnnualPlanId,
                        "badge" to plan.badge,
                        "features" to plan.features,
                        "sortOrder" to plan.sortOrder,
                        "active" to plan.active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            writeAuditLog("SEED_PLANS", "subscription_plans", "Seeded consumer & developer subscription tiers")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "subscription_plans")
            Result.failure(e)
        }
    }

    suspend fun adminGrantCredits(targetUid: String, delta: Long, reason: String): Result<Unit> {
        val path = "users/$targetUid"
        return try {
            val newBal = db.runTransaction { tx ->
                val ref = db.collection("users").document(targetUid)
                val snap = tx.get(ref)
                val current = snap.getLong("creditBalance") ?: 0L
                val updated = (current + delta).coerceIn(0L, 10_000_000L)
                tx.update(
                    ref,
                    mapOf(
                        "creditBalance" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                val txRef = ref.collection("credit_transactions").document()
                tx.set(
                    txRef,
                    mapOf(
                        "userId" to uidOrTarget(targetUid),
                        "txType" to if (delta >= 0) TxType.ADMIN_GRANT.name else TxType.ADMIN_ADJUSTMENT.name,
                        "amount" to delta,
                        "balanceAfter" to updated,
                        "toolId" to "admin_console",
                        "description" to reason.trim().take(240).ifBlank { "Admin credit adjustment" },
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                updated
            }.await()
            writeAuditLog("GRANT_CREDITS", path, "Delta=$delta, NewBalance=$newBal, Reason=$reason")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private fun uidOrTarget(uid: String) = uid

    suspend fun adminUpdateUserStatusAndPlan(
        targetUid: String,
        newPlan: PlanTier,
        newStatus: AccountStatus
    ): Result<Unit> {
        val path = "users/$targetUid"
        return try {
            db.document(path).update(
                mapOf(
                    "planId" to newPlan.name,
                    "status" to newStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog("UPDATE_USER", path, "Plan=${newPlan.name}, Status=${newStatus.name}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminUpsertPlan(plan: SubscriptionPlan): Result<Unit> {
        val docId = plan.id.ifBlank { plan.name.uppercase().replace(" ", "_") }
        val path = "subscription_plans/$docId"
        return try {
            db.document(path).set(
                mapOf(
                    "name" to plan.name.trim().take(60),
                    "planCategory" to plan.planCategory,
                    "monthlyPriceInr" to plan.monthlyPriceInr.coerceAtLeast(0L),
                    "annualPriceInr" to plan.annualPriceInr.coerceAtLeast(0L),
                    "monthlyCredits" to plan.monthlyCredits.coerceAtLeast(0L),
                    "rateLimitRpm" to plan.rateLimitRpm.coerceAtLeast(1L),
                    "razorpayMonthlyPlanId" to plan.razorpayMonthlyPlanId.trim().take(80),
                    "razorpayAnnualPlanId" to plan.razorpayAnnualPlanId.trim().take(80),
                    "badge" to plan.badge.trim().take(40),
                    "features" to plan.features.take(12),
                    "sortOrder" to plan.sortOrder.coerceIn(0L, 100L),
                    "active" to plan.active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog("UPSERT_PLAN", path, "Updated plan ${plan.name} (₹${plan.monthlyPriceInr}/mo)")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun adminPublishAnnouncement(
        title: String,
        message: String,
        severity: String
    ): Result<Unit> {
        val path = "system_announcements"
        return try {
            val uid = requireUserId()
            val ref = db.collection(path).document()
            ref.set(
                mapOf(
                    "title" to title.trim().take(120),
                    "message" to message.trim().take(600),
                    "severity" to severity,
                    "active" to true,
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog("PUBLISH_ANNOUNCEMENT", "$path/${ref.id}", title)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun adminToggleAnnouncement(announcementId: String, active: Boolean): Result<Unit> {
        val path = "system_announcements/$announcementId"
        return try {
            db.document(path).update(mapOf("active" to active)).await()
            writeAuditLog("TOGGLE_ANNOUNCEMENT", path, "active=$active")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private suspend fun writeAuditLog(action: String, targetPath: String, details: String) {
        val current = auth.currentUser ?: return
        try {
            db.collection("audit_logs").document().set(
                mapOf(
                    "actorUid" to current.uid,
                    "actorEmail" to (current.email ?: ""),
                    "action" to action.take(80),
                    "targetPath" to targetPath.take(200),
                    "details" to details.take(500),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "audit_logs")
        }
    }

    private fun logStreamError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && (expectedUid.isBlank() || auth.currentUser?.uid == expectedUid)) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
