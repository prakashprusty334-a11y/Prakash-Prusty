package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

enum class UserRole { USER, ADMIN }
enum class AccountStatus { ACTIVE, SUSPENDED }
enum class PlanTier { FREE, PRO, STUDIO, BUSINESS }
enum class PlanCategory { CONSUMER, DEVELOPER }
enum class SenderRole { user, model }
enum class TxType {
    STARTER_GRANT,
    USAGE_DEBIT,
    ADMIN_GRANT,
    ADMIN_ADJUSTMENT,
    SUBSCRIPTION_GRANT,
    CREDIT_PURCHASE,
    GENERATION_REFUND
}
enum class ProjectCategory { CHAT, IMAGE, AUDIO, WRITING, CODE, DOCUMENT, PHOTO_EDIT, VIDEO_PREP }
enum class AnnouncementSeverity { INFO, WARNING, CRITICAL }

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val role: String = UserRole.USER.name,
    val status: String = AccountStatus.ACTIVE.name,
    val planId: String = PlanTier.FREE.name,
    val creditBalance: Long = 100L,
    val totalCreditsUsed: Long = 0L,
    val preferredModel: String = AiModelCatalog.DEFAULT_TEXT_MODEL.id,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun resolvedRole(): UserRole =
        enumValues<UserRole>().firstOrNull { it.name == role } ?: UserRole.USER

    fun resolvedStatus(): AccountStatus =
        enumValues<AccountStatus>().firstOrNull { it.name == status } ?: AccountStatus.ACTIVE

    fun resolvedPlan(): PlanTier =
        enumValues<PlanTier>().firstOrNull { it.name == planId } ?: PlanTier.FREE
}

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Conversation",
    val modelId: String = AiModelCatalog.DEFAULT_TEXT_MODEL.id,
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderRole: String = SenderRole.user.name,
    val content: String = "",
    val modelId: String = AiModelCatalog.DEFAULT_TEXT_MODEL.id,
    val attachmentLabel: String? = null,
    val createdAt: Timestamp? = null
) {
    fun resolvedSenderRole(): SenderRole =
        enumValues<SenderRole>().firstOrNull { it.name == senderRole } ?: SenderRole.user
}

@Keep
@IgnoreExtraProperties
data class CreditTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val txType: String = TxType.USAGE_DEBIT.name,
    val amount: Long = 0L,
    val balanceAfter: Long = 0L,
    val toolId: String = "",
    val description: String = "",
    val createdAt: Timestamp? = null
) {
    fun resolvedTxType(): TxType =
        enumValues<TxType>().firstOrNull { it.name == txType } ?: TxType.USAGE_DEBIT
}

@Keep
@IgnoreExtraProperties
data class AiProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = ProjectCategory.WRITING.name,
    val prompt: String = "",
    val outputText: String = "",
    val modelId: String = AiModelCatalog.DEFAULT_TEXT_MODEL.id,
    val creditsCost: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun resolvedCategory(): ProjectCategory =
        enumValues<ProjectCategory>().firstOrNull { it.name == category } ?: ProjectCategory.WRITING
}

@Keep
@IgnoreExtraProperties
data class SubscriptionPlan(
    @DocumentId val id: String = "",
    val name: String = "",
    val planCategory: String = PlanCategory.CONSUMER.name,
    val monthlyPriceInr: Long = 0L,
    val annualPriceInr: Long = 0L,
    val monthlyCredits: Long = 100L,
    val rateLimitRpm: Long = 30L,
    val razorpayMonthlyPlanId: String = "",
    val razorpayAnnualPlanId: String = "",
    val badge: String = "",
    val features: List<String> = emptyList(),
    val sortOrder: Long = 0L,
    val active: Boolean = true,
    val updatedAt: Timestamp? = null
) {
    fun resolvedCategory(): PlanCategory =
        enumValues<PlanCategory>().firstOrNull { it.name == planCategory } ?: PlanCategory.CONSUMER
}

@Keep
@IgnoreExtraProperties
data class SystemAnnouncement(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val severity: String = AnnouncementSeverity.INFO.name,
    val active: Boolean = true,
    val createdBy: String = "",
    val createdAt: Timestamp? = null
) {
    fun resolvedSeverity(): AnnouncementSeverity =
        enumValues<AnnouncementSeverity>().firstOrNull { it.name == severity } ?: AnnouncementSeverity.INFO
}

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val action: String = "",
    val targetPath: String = "",
    val details: String = "",
    val createdAt: Timestamp? = null
)

// --- COMMERCIAL API, BILLING & PROVIDER ROUTING MODELS ---

data class CreditPack(
    val id: String,
    val label: String,
    val credits: Long,
    val priceInr: Long
)

data class DeveloperAccount(
    val id: String = "",
    val userId: String = "",
    val email: String = "",
    val organizationName: String = "",
    val developerPlanId: String = "FREE_DEVELOPER",
    val status: String = "ACTIVE",
    val rateLimitRpm: Long = 10L,
    val monthlyApiQuota: Long = 1000L,
    val apiCreditsBalance: Long = 0L,
    val totalApiRequests: Long = 0L,
    val createdAt: String = ""
)

data class ApiKeyRecord(
    val id: String = "",
    val userId: String = "",
    val developerId: String = "",
    val name: String = "",
    val keyPrefix: String = "",
    val scopes: List<String> = emptyList(),
    val rateLimitRpm: Long = 10L,
    val status: String = "ACTIVE",
    val requestCount: Long = 0L,
    val lastUsedAt: String? = null,
    val createdAt: String = "",
    val revokedAt: String? = null
)

data class CreatedApiKeyResponse(
    val keyRecord: ApiKeyRecord,
    val plaintextKey: String
)

data class ApiRequestLog(
    val id: String = "",
    val endpoint: String = "",
    val method: String = "POST",
    val modelId: String = "",
    val providerId: String = "",
    val statusCode: Int = 200,
    val latencyMs: Long = 0L,
    val creditsCharged: Long = 0L,
    val tokensUsed: Long = 0L,
    val errorMessage: String = "",
    val createdAt: String = ""
)

data class DeveloperAnalytics(
    val totalRequests: Int = 0,
    val errorCount: Int = 0,
    val activeKeysCount: Int = 0,
    val avgLatencyMs: Long = 0L,
    val totalCreditsUsed: Long = 0L,
    val recentRequests: List<ApiRequestLog> = emptyList()
)

data class SubscriptionRecord(
    val id: String = "",
    val userId: String = "",
    val planId: String = "FREE",
    val planCategory: String = "CONSUMER",
    val billingInterval: String = "MONTHLY",
    val status: String = "CREATED",
    val razorpaySubscriptionId: String = "",
    val amountInr: Long = 0L,
    val shortUrl: String = "",
    val nextBillingAt: String? = null,
    val cancelAtPeriodEnd: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = ""
)

data class PaymentRecord(
    val id: String = "",
    val userId: String = "",
    val planId: String = "",
    val amountPaise: Long = 0L,
    val currency: String = "INR",
    val status: String = "CREATED",
    val razorpayOrderId: String = "",
    val razorpayPaymentId: String = "",
    val razorpaySubscriptionId: String = "",
    val method: String = "",
    val receiptNumber: String = "",
    val refundId: String = "",
    val failureReason: String = "",
    val createdAt: String = ""
)

data class CreditWallet(
    val userId: String = "",
    val balance: Long = 0L,
    val subscriptionCredits: Long = 0L,
    val purchasedCredits: Long = 0L,
    val lifetimeUsed: Long = 0L,
    val lifetimeRefunded: Long = 0L,
    val updatedAt: String = ""
)

data class CheckoutLaunchData(
    val razorpayKeyId: String = "",
    val razorpaySubscriptionId: String = "",
    val razorpayOrderId: String = "",
    val amountPaise: Long = 0L,
    val shortUrl: String = "",
    val checkoutPageUrl: String = ""
)

data class BillingSummary(
    val wallet: CreditWallet = CreditWallet(),
    val activeSubscription: SubscriptionRecord? = null,
    val subscriptions: List<SubscriptionRecord> = emptyList(),
    val payments: List<PaymentRecord> = emptyList(),
    val ledgerEntries: List<CreditTransaction> = emptyList(),
    val razorpayConfigured: Boolean = false
)

@Keep
@IgnoreExtraProperties
data class AiProviderConfig(
    @DocumentId val id: String = "",
    val name: String = "",
    val adapterType: String = "GEMINI",
    val baseUrl: String = "",
    val secretEnvRef: String = "",
    val hasServerCredential: Boolean = false,
    val enabled: Boolean = true,
    val priority: Long = 1L,
    val fallbackProviderId: String = "",
    val healthStatus: String = "HEALTHY",
    val totalRequests: Long = 0L,
    val totalErrors: Long = 0L,
    val lastError: String = ""
)

@Keep
@IgnoreExtraProperties
data class AiModelRouting(
    @DocumentId val id: String = "",
    val modelId: String = "",
    val displayName: String = "",
    val providerId: String = "gemini",
    val fallbackProviderId: String = "",
    val suppliedBy: String = "External Provider: Google Gemini",
    val capability: String = "CHAT",
    val creditCost: Long = 2L,
    val rateLimitRpm: Long = 60L,
    val enabled: Boolean = true,
    val isDefault: Boolean = false
)

data class AdminGatewayOverview(
    val totalUsers: Int = 0,
    val totalDevelopers: Int = 0,
    val activeSubscriptions: Int = 0,
    val capturedRevenueInr: Double = 0.0,
    val totalApiRequests: Int = 0,
    val activeApiKeys: Int = 0,
    val developers: List<DeveloperAccount> = emptyList(),
    val subscriptions: List<SubscriptionRecord> = emptyList(),
    val payments: List<PaymentRecord> = emptyList(),
    val apiKeys: List<ApiKeyRecord> = emptyList(),
    val recentApiErrors: List<ApiRequestLog> = emptyList()
)

data class PlaygroundHttpResult(
    val statusCode: Int,
    val latencyMs: Long,
    val headersText: String,
    val bodyText: String
)

data class AiModelOption(
    val id: String,
    val label: String,
    val subtitle: String,
    val creditCost: Int
)

object AiModelCatalog {
    val DEFAULT_TEXT_MODEL = AiModelOption(
        id = "gemini-3.8-flash",
        label = "Gemini 3.8 Flash",
        subtitle = "Frontier intelligence & speed (Default)",
        creditCost = 2
    )
    val FAST_TEXT_MODEL = AiModelOption(
        id = "gemini-3.5-flash-lite",
        label = "Gemini 3.5 Flash-Lite",
        subtitle = "Ultra-low latency high volume",
        creditCost = 1
    )
    val PRO_REASONING_MODEL = AiModelOption(
        id = "gemini-4-argon",
        label = "Gemini 4 Argon",
        subtitle = "Deep coding, STEM & complex reasoning",
        creditCost = 5
    )

    val textModels = listOf(DEFAULT_TEXT_MODEL, FAST_TEXT_MODEL, PRO_REASONING_MODEL)

    val DEFAULT_IMAGE_MODEL = AiModelOption(
        id = "gemini-nano-banana-2.1",
        label = "Nano Banana 2.1",
        subtitle = "Fast high-efficiency image studio",
        creditCost = 8
    )
    val LITE_IMAGE_MODEL = AiModelOption(
        id = "gemini-3.1-flash-lite-image",
        label = "Nano Banana 2 Lite",
        subtitle = "Ultra-low latency image generation",
        creditCost = 5
    )
    val PRO_IMAGE_MODEL = AiModelOption(
        id = "gemini-3-pro-image",
        label = "Nano Banana Pro",
        subtitle = "Studio-grade typography & complex scenes",
        creditCost = 15
    )

    val imageModels = listOf(DEFAULT_IMAGE_MODEL, LITE_IMAGE_MODEL, PRO_IMAGE_MODEL)

    val creditPacks = listOf(
        CreditPack("PACK_500", "500 AI Credits", 500L, 199L),
        CreditPack("PACK_2500", "2,500 AI Credits", 2500L, 799L),
        CreditPack("PACK_10000", "10,000 AI Credits", 10000L, 2499L)
    )

    val defaultPlans = listOf(
        SubscriptionPlan(
            id = "FREE",
            name = "FREE",
            planCategory = PlanCategory.CONSUMER.name,
            monthlyPriceInr = 0L,
            annualPriceInr = 0L,
            monthlyCredits = 100L,
            rateLimitRpm = 15L,
            badge = "Starter",
            features = listOf(
                "100 starter AI credits",
                "Basic AI Chat (Gemini 3.8 Flash & Flash-Lite)",
                "Limited creative tools & Photo Editor",
                "Cloud project history sync"
            ),
            sortOrder = 1L,
            active = true
        ),
        SubscriptionPlan(
            id = "PRO",
            name = "PRO",
            planCategory = PlanCategory.CONSUMER.name,
            monthlyPriceInr = 499L,
            annualPriceInr = 4990L,
            monthlyCredits = 2500L,
            rateLimitRpm = 60L,
            badge = "Most Popular",
            features = listOf(
                "2,500 monthly AI credits",
                "Advanced AI Image, Writing & Coding Studios",
                "Priority access where supported",
                "Razorpay UPI & Card auto-renewal"
            ),
            sortOrder = 2L,
            active = true
        ),
        SubscriptionPlan(
            id = "STUDIO",
            name = "STUDIO",
            planCategory = PlanCategory.CONSUMER.name,
            monthlyPriceInr = 999L,
            annualPriceInr = 9990L,
            monthlyCredits = 7500L,
            rateLimitRpm = 120L,
            badge = "Creators",
            features = listOf(
                "7,500 monthly AI credits",
                "Creative Studio suite with higher image & audio limits",
                "Advanced editing tools & Document decks",
                "Extended cloud project archive"
            ),
            sortOrder = 3L,
            active = true
        ),
        SubscriptionPlan(
            id = "BUSINESS",
            name = "BUSINESS",
            planCategory = PlanCategory.CONSUMER.name,
            monthlyPriceInr = 2499L,
            annualPriceInr = 24990L,
            monthlyCredits = 25000L,
            rateLimitRpm = 300L,
            badge = "Business",
            features = listOf(
                "25,000 monthly AI credits",
                "Higher usage limits & Business workspace",
                "Team features & GST-ready invoice receipts",
                "Admin usage governance"
            ),
            sortOrder = 4L,
            active = true
        ),
        // Developer API Plans
        SubscriptionPlan(
            id = "FREE_DEVELOPER",
            name = "FREE DEVELOPER",
            planCategory = PlanCategory.DEVELOPER.name,
            monthlyPriceInr = 0L,
            annualPriceInr = 0L,
            monthlyCredits = 100L,
            rateLimitRpm = 10L,
            badge = "API Sandbox",
            features = listOf(
                "10 RPM API rate limit",
                "Personal CEREVIX API keys (SHA-256 HMAC)",
                "Interactive API Playground & Docs"
            ),
            sortOrder = 5L,
            active = true
        ),
        SubscriptionPlan(
            id = "PRO_DEVELOPER",
            name = "PRO DEVELOPER",
            planCategory = PlanCategory.DEVELOPER.name,
            monthlyPriceInr = 499L,
            annualPriceInr = 4990L,
            monthlyCredits = 3000L,
            rateLimitRpm = 60L,
            badge = "API Pro",
            features = listOf(
                "60 RPM API rate limit",
                "3,000 monthly API credits",
                "Request analytics & usage telemetry"
            ),
            sortOrder = 6L,
            active = true
        ),
        SubscriptionPlan(
            id = "BUSINESS_DEVELOPER",
            name = "BUSINESS DEVELOPER",
            planCategory = PlanCategory.DEVELOPER.name,
            monthlyPriceInr = 1999L,
            annualPriceInr = 19990L,
            monthlyCredits = 18000L,
            rateLimitRpm = 300L,
            badge = "Scale API",
            features = listOf(
                "300 RPM API rate limit",
                "18,000 monthly API credits",
                "Multi-provider fallback routing"
            ),
            sortOrder = 7L,
            active = true
        ),
        SubscriptionPlan(
            id = "ENTERPRISE",
            name = "ENTERPRISE",
            planCategory = PlanCategory.DEVELOPER.name,
            monthlyPriceInr = 0L,
            annualPriceInr = 0L,
            monthlyCredits = 100000L,
            rateLimitRpm = 1200L,
            badge = "Custom Pricing",
            features = listOf(
                "Custom pricing & dedicated SLA",
                "1,200+ RPM & custom model endpoints",
                "Owner-provisioned enterprise billing"
            ),
            sortOrder = 8L,
            active = true
        )
    )
}

data class StructuredDocSection(
    val heading: String,
    val bullets: List<String>
)

data class StructuredDocumentResult(
    val title: String,
    val subtitle: String,
    val sections: List<StructuredDocSection>,
    val summaryCallout: String
)

data class SfxDesignCue(
    val cueTitle: String,
    val acousticTexture: String,
    val frequencyProfile: String,
    val durationSeconds: Double,
    val productionNotes: String
)

data class VideoStoryboardShot(
    val shotNumber: Int,
    val durationSeconds: Int,
    val visualPrompt: String,
    val cameraMovement: String,
    val voiceoverLine: String
)

data class ProviderCapabilityStatus(
    val name: String,
    val provider: String,
    val operational: Boolean,
    val detail: String
)
