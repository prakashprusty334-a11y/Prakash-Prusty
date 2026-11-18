package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.data.model.AdminGatewayOverview
import com.example.data.model.AiModelRouting
import com.example.data.model.AiProviderConfig
import com.example.data.model.ApiKeyRecord
import com.example.data.model.ApiRequestLog
import com.example.data.model.BillingSummary
import com.example.data.model.CheckoutLaunchData
import com.example.data.model.CreatedApiKeyResponse
import com.example.data.model.CreditTransaction
import com.example.data.model.CreditWallet
import com.example.data.model.DeveloperAccount
import com.example.data.model.DeveloperAnalytics
import com.example.data.model.PaymentRecord
import com.example.data.model.PlaygroundHttpResult
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SubscriptionRecord
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CerevixGatewayClient(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "cerevix_gateway_prefs",
        Context.MODE_PRIVATE
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun getGatewayBaseUrl(): String {
        val saved = prefs.getString("gateway_base_url", null)?.trim()
        return if (!saved.isNullOrBlank()) saved.trimEnd('/') else BuildConfig.CEREVIX_API_BASE_URL.trimEnd('/')
    }

    fun setGatewayBaseUrl(url: String) {
        val clean = url.trim().trimEnd('/').ifBlank { BuildConfig.CEREVIX_API_BASE_URL }
        prefs.edit().putString("gateway_base_url", clean).apply()
    }

    private suspend fun getFirebaseIdToken(): String {
        val user = Firebase.auth.currentUser
            ?: throw IllegalStateException("Please sign in with Google first.")
        return user.getIdToken(false).await().token
            ?: throw IllegalStateException("Unable to obtain Firebase ID token.")
    }

    private suspend fun requestJson(
        method: String,
        path: String,
        body: JSONObject? = null,
        requireFirebaseAuth: Boolean = true,
        apiKeyOverride: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val base = getGatewayBaseUrl()
        val url = "$base$path"
        val builder = Request.Builder().url(url)

        if (!apiKeyOverride.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer ${apiKeyOverride.trim()}")
        } else if (requireFirebaseAuth) {
            val token = getFirebaseIdToken()
            builder.addHeader("Authorization", "Bearer $token")
        }

        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> builder.post((body?.toString() ?: "{}").toRequestBody(jsonMedia))
            "PATCH" -> builder.patch((body?.toString() ?: "{}").toRequestBody(jsonMedia))
            else -> builder.method(method, (body?.toString() ?: "{}").toRequestBody(jsonMedia))
        }

        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: Exception) {
            throw IllegalStateException(
                "CEREVIX Backend Gateway ($base) is unreachable: ${e.localizedMessage ?: "Network error"}. " +
                    "Deploy the backend or configure your local URL (e.g. http://10.0.2.2:8080) in Developer Settings."
            )
        }

        val rawBody = response.body?.string().orEmpty()
        val parsed = runCatching { JSONObject(rawBody) }.getOrNull() ?: JSONObject()
        if (!response.isSuccessful) {
            val errMsg = parsed.optString("error").ifBlank {
                "Gateway HTTP ${response.code}: ${rawBody.take(220)}"
            }
            throw IllegalStateException(errMsg)
        }
        parsed
    }

    suspend fun checkHealth(): Result<JSONObject> = runCatching {
        requestJson("GET", "/health", requireFirebaseAuth = false)
    }

    // --- DEVELOPER PORTAL ---

    suspend fun registerDeveloper(organizationName: String): Result<DeveloperAccount> = runCatching {
        val json = requestJson(
            "POST",
            "/v1/developer/register",
            JSONObject().put("organizationName", organizationName)
        )
        parseDeveloper(json.getJSONObject("developer"))
    }

    suspend fun fetchDeveloperProfile(): Result<Pair<DeveloperAccount?, CreditWallet>> = runCatching {
        val json = requestJson("GET", "/v1/developer/profile")
        val devObj = json.optJSONObject("developer")
        val walletObj = json.optJSONObject("wallet") ?: JSONObject()
        Pair(devObj?.let { parseDeveloper(it) }, parseWallet(walletObj))
    }

    suspend fun fetchApiKeys(): Result<List<ApiKeyRecord>> = runCatching {
        val json = requestJson("GET", "/v1/developer/keys")
        val arr = json.optJSONArray("keys") ?: JSONArray()
        (0 until arr.length()).map { parseApiKey(arr.getJSONObject(it)) }
    }

    suspend fun createApiKey(name: String, scopes: List<String>): Result<CreatedApiKeyResponse> = runCatching {
        val json = requestJson(
            "POST",
            "/v1/developer/keys",
            JSONObject()
                .put("name", name)
                .put("scopes", JSONArray(scopes))
        )
        CreatedApiKeyResponse(
            keyRecord = parseApiKey(json.getJSONObject("keyRecord")),
            plaintextKey = json.getString("plaintextKey")
        )
    }

    suspend fun revokeApiKey(keyId: String): Result<ApiKeyRecord> = runCatching {
        val json = requestJson("POST", "/v1/developer/keys/$keyId/revoke", JSONObject())
        parseApiKey(json.getJSONObject("keyRecord"))
    }

    suspend fun regenerateApiKey(keyId: String): Result<CreatedApiKeyResponse> = runCatching {
        val json = requestJson("POST", "/v1/developer/keys/$keyId/regenerate", JSONObject())
        CreatedApiKeyResponse(
            keyRecord = parseApiKey(json.getJSONObject("keyRecord")),
            plaintextKey = json.getString("plaintextKey")
        )
    }

    suspend fun fetchDeveloperAnalytics(): Result<DeveloperAnalytics> = runCatching {
        val json = requestJson("GET", "/v1/developer/analytics")
        val arr = json.optJSONArray("recentRequests") ?: JSONArray()
        DeveloperAnalytics(
            totalRequests = json.optInt("totalRequests", 0),
            errorCount = json.optInt("errorCount", 0),
            activeKeysCount = json.optInt("activeKeysCount", 0),
            avgLatencyMs = json.optLong("avgLatencyMs", 0L),
            totalCreditsUsed = json.optLong("totalCreditsUsed", 0L),
            recentRequests = (0 until arr.length()).map { parseRequestLog(arr.getJSONObject(it)) }
        )
    }

    suspend fun executeApiPlaygroundRequest(
        method: String,
        endpointPath: String,
        apiKey: String,
        jsonPayload: String
    ): Result<PlaygroundHttpResult> = runCatching {
        withContext(Dispatchers.IO) {
            val base = getGatewayBaseUrl()
            val fullUrl = "$base${if (endpointPath.startsWith("/")) endpointPath else "/$endpointPath"}"
            val reqBuilder = Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")

            if (method.equals("GET", ignoreCase = true)) {
                reqBuilder.get()
            } else {
                val cleanBody = jsonPayload.ifBlank { "{}" }
                reqBuilder.post(cleanBody.toRequestBody(jsonMedia))
            }

            val start = System.currentTimeMillis()
            val resp = client.newCall(reqBuilder.build()).execute()
            val latency = System.currentTimeMillis() - start
            val respBody = resp.body?.string().orEmpty()
            val headersSummary = buildString {
                listOf("X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset", "Content-Type")
                    .forEach { h ->
                        resp.header(h)?.let { append("$h: $it\n") }
                    }
            }.trim()

            PlaygroundHttpResult(
                statusCode = resp.code,
                latencyMs = latency,
                headersText = headersSummary,
                bodyText = runCatching { JSONObject(respBody).toString(2) }.getOrElse { respBody }
            )
        }
    }

    // --- BILLING & RAZORPAY ---

    suspend fun fetchBillingSummary(): Result<BillingSummary> = runCatching {
        val json = requestJson("GET", "/v1/billing/summary")
        val wallet = parseWallet(json.optJSONObject("wallet") ?: JSONObject())
        val activeSub = json.optJSONObject("activeSubscription")?.let { parseSubscription(it) }
        val subsArr = json.optJSONArray("subscriptions") ?: JSONArray()
        val paysArr = json.optJSONArray("payments") ?: JSONArray()
        val txArr = json.optJSONArray("creditTransactions") ?: JSONArray()

        BillingSummary(
            wallet = wallet,
            activeSubscription = activeSub,
            subscriptions = (0 until subsArr.length()).map { parseSubscription(subsArr.getJSONObject(it)) },
            payments = (0 until paysArr.length()).map { parsePayment(paysArr.getJSONObject(it)) },
            ledgerEntries = (0 until txArr.length()).map {
                val o = txArr.getJSONObject(it)
                CreditTransaction(
                    id = o.optString("id"),
                    userId = o.optString("userId"),
                    txType = o.optString("txType"),
                    amount = o.optLong("amount", 0L),
                    balanceAfter = o.optLong("balanceAfter", 0L),
                    toolId = o.optString("toolId"),
                    description = o.optString("description")
                )
            },
            razorpayConfigured = json.optBoolean("razorpayConfigured", false)
        )
    }

    suspend fun initiateSubscriptionCheckout(
        planId: String,
        billingInterval: String
    ): Result<Pair<SubscriptionRecord, CheckoutLaunchData>> = runCatching {
        val json = requestJson(
            "POST",
            "/v1/billing/subscriptions",
            JSONObject()
                .put("planId", planId)
                .put("billingInterval", billingInterval)
        )
        Pair(
            parseSubscription(json.getJSONObject("subscription")),
            parseCheckout(json.getJSONObject("checkout"))
        )
    }

    suspend fun initiateCreditPackOrder(packId: String): Result<Pair<PaymentRecord, CheckoutLaunchData>> = runCatching {
        val json = requestJson(
            "POST",
            "/v1/billing/orders/credits",
            JSONObject().put("packId", packId)
        )
        Pair(
            parsePayment(json.getJSONObject("payment")),
            parseCheckout(json.getJSONObject("checkout"))
        )
    }

    suspend fun verifyRazorpayPayment(
        razorpayPaymentId: String,
        razorpaySubscriptionId: String,
        razorpayOrderId: String,
        razorpaySignature: String
    ): Result<JSONObject> = runCatching {
        requestJson(
            "POST",
            "/v1/billing/verify",
            JSONObject()
                .put("razorpayPaymentId", razorpayPaymentId)
                .put("razorpaySubscriptionId", razorpaySubscriptionId)
                .put("razorpayOrderId", razorpayOrderId)
                .put("razorpaySignature", razorpaySignature)
        )
    }

    suspend fun cancelSubscription(subscriptionId: String, cancelAtCycleEnd: Boolean = true): Result<SubscriptionRecord> = runCatching {
        val json = requestJson(
            "POST",
            "/v1/billing/subscriptions/$subscriptionId/cancel",
            JSONObject().put("cancelAtCycleEnd", cancelAtCycleEnd)
        )
        parseSubscription(json.getJSONObject("subscription"))
    }

    suspend fun fetchPaymentReceiptHtml(paymentId: String): Result<String> = runCatching {
        val json = requestJson("GET", "/v1/billing/payments/$paymentId/receipt")
        json.optString("html")
    }

    // --- OWNER ADMIN GATEWAY ENDPOINTS ---

    suspend fun fetchAdminOverview(): Result<AdminGatewayOverview> = runCatching {
        val json = requestJson("GET", "/v1/admin/overview")
        val m = json.optJSONObject("metrics") ?: JSONObject()
        val devs = json.optJSONArray("developers") ?: JSONArray()
        val subs = json.optJSONArray("subscriptions") ?: JSONArray()
        val pays = json.optJSONArray("payments") ?: JSONArray()
        val keys = json.optJSONArray("apiKeys") ?: JSONArray()
        val errs = json.optJSONArray("recentApiErrors") ?: JSONArray()

        AdminGatewayOverview(
            totalUsers = m.optInt("totalUsers", 0),
            totalDevelopers = m.optInt("totalDevelopers", 0),
            activeSubscriptions = m.optInt("activeSubscriptions", 0),
            capturedRevenueInr = m.optDouble("capturedRevenueInr", 0.0),
            totalApiRequests = m.optInt("totalApiRequests", 0),
            activeApiKeys = m.optInt("activeApiKeys", 0),
            developers = (0 until devs.length()).map { parseDeveloper(devs.getJSONObject(it)) },
            subscriptions = (0 until subs.length()).map { parseSubscription(subs.getJSONObject(it)) },
            payments = (0 until pays.length()).map { parsePayment(pays.getJSONObject(it)) },
            apiKeys = (0 until keys.length()).map { parseApiKey(keys.getJSONObject(it)) },
            recentApiErrors = (0 until errs.length()).map { parseRequestLog(errs.getJSONObject(it)) }
        )
    }

    suspend fun fetchAdminProviders(): Result<List<AiProviderConfig>> = runCatching {
        val json = requestJson("GET", "/v1/admin/providers")
        val arr = json.optJSONArray("providers") ?: JSONArray()
        (0 until arr.length()).map { o ->
            val j = arr.getJSONObject(o)
            AiProviderConfig(
                id = j.optString("id"),
                name = j.optString("name"),
                adapterType = j.optString("adapterType"),
                baseUrl = j.optString("baseUrl"),
                secretEnvRef = j.optString("secretEnvRef"),
                hasServerCredential = j.optBoolean("hasServerCredential", false),
                enabled = j.optBoolean("enabled", false),
                priority = j.optLong("priority", 1L),
                fallbackProviderId = j.optString("fallbackProviderId"),
                healthStatus = j.optString("healthStatus", "UNCONFIGURED"),
                totalRequests = j.optLong("totalRequests", 0L),
                totalErrors = j.optLong("totalErrors", 0L),
                lastError = j.optString("lastError")
            )
        }
    }

    suspend fun upsertAdminProvider(
        provider: AiProviderConfig,
        newCredentialSecret: String
    ): Result<AiProviderConfig> = runCatching {
        val body = JSONObject()
            .put("id", provider.id)
            .put("name", provider.name)
            .put("adapterType", provider.adapterType)
            .put("baseUrl", provider.baseUrl)
            .put("secretEnvRef", provider.secretEnvRef)
            .put("enabled", provider.enabled)
            .put("priority", provider.priority)
            .put("fallbackProviderId", provider.fallbackProviderId)
        if (newCredentialSecret.isNotBlank()) {
            body.put("apiCredential", newCredentialSecret.trim())
        }
        val json = requestJson("POST", "/v1/admin/providers", body)
        val j = json.getJSONObject("provider")
        AiProviderConfig(
            id = j.optString("id"),
            name = j.optString("name"),
            adapterType = j.optString("adapterType"),
            baseUrl = j.optString("baseUrl"),
            secretEnvRef = j.optString("secretEnvRef"),
            hasServerCredential = j.optBoolean("hasServerCredential", false),
            enabled = j.optBoolean("enabled", false),
            priority = j.optLong("priority", 1L),
            fallbackProviderId = j.optString("fallbackProviderId"),
            healthStatus = j.optString("healthStatus", "UNCONFIGURED"),
            totalRequests = j.optLong("totalRequests", 0L),
            totalErrors = j.optLong("totalErrors", 0L),
            lastError = j.optString("lastError")
        )
    }

    suspend fun upsertAdminModelRouting(model: AiModelRouting): Result<Unit> = runCatching {
        requestJson(
            "POST",
            "/v1/admin/models",
            JSONObject()
                .put("modelId", model.modelId)
                .put("displayName", model.displayName)
                .put("providerId", model.providerId)
                .put("fallbackProviderId", model.fallbackProviderId)
                .put("suppliedBy", model.suppliedBy)
                .put("capability", model.capability)
                .put("creditCost", model.creditCost)
                .put("rateLimitRpm", model.rateLimitRpm)
                .put("enabled", model.enabled)
                .put("isDefault", model.isDefault)
        )
        Unit
    }

    suspend fun upsertAdminPlan(plan: SubscriptionPlan): Result<Unit> = runCatching {
        requestJson(
            "POST",
            "/v1/admin/plans",
            JSONObject()
                .put("id", plan.id)
                .put("name", plan.name)
                .put("planCategory", plan.planCategory)
                .put("monthlyPriceInr", plan.monthlyPriceInr)
                .put("annualPriceInr", plan.annualPriceInr)
                .put("monthlyCredits", plan.monthlyCredits)
                .put("rateLimitRpm", plan.rateLimitRpm)
                .put("razorpayMonthlyPlanId", plan.razorpayMonthlyPlanId)
                .put("razorpayAnnualPlanId", plan.razorpayAnnualPlanId)
                .put("badge", plan.badge)
                .put("features", JSONArray(plan.features))
                .put("sortOrder", plan.sortOrder)
                .put("active", plan.active)
        )
        Unit
    }

    suspend fun refundPaymentAdmin(paymentId: String, reason: String): Result<PaymentRecord> = runCatching {
        val json = requestJson(
            "POST",
            "/v1/admin/payments/$paymentId/refund",
            JSONObject().put("reason", reason)
        )
        parsePayment(json.getJSONObject("payment"))
    }

    suspend fun revokeApiKeyAdmin(keyId: String): Result<Unit> = runCatching {
        requestJson("POST", "/v1/admin/keys/$keyId/revoke", JSONObject())
        Unit
    }

    private fun parseDeveloper(j: JSONObject) = DeveloperAccount(
        id = j.optString("id"),
        userId = j.optString("userId"),
        email = j.optString("email"),
        organizationName = j.optString("organizationName"),
        developerPlanId = j.optString("developerPlanId", "FREE_DEVELOPER"),
        status = j.optString("status", "ACTIVE"),
        rateLimitRpm = j.optLong("rateLimitRpm", 10L),
        monthlyApiQuota = j.optLong("monthlyApiQuota", 1000L),
        apiCreditsBalance = j.optLong("apiCreditsBalance", 0L),
        totalApiRequests = j.optLong("totalApiRequests", 0L),
        createdAt = j.optString("createdAt")
    )

    private fun parseApiKey(j: JSONObject): ApiKeyRecord {
        val scopesArr = j.optJSONArray("scopes") ?: JSONArray()
        return ApiKeyRecord(
            id = j.optString("id"),
            userId = j.optString("userId"),
            developerId = j.optString("developerId"),
            name = j.optString("name"),
            keyPrefix = j.optString("keyPrefix"),
            scopes = (0 until scopesArr.length()).map { scopesArr.optString(it) },
            rateLimitRpm = j.optLong("rateLimitRpm", 10L),
            status = j.optString("status", "ACTIVE"),
            requestCount = j.optLong("requestCount", 0L),
            lastUsedAt = j.optString("lastUsedAt").takeIf { it.isNotBlank() && it != "null" },
            createdAt = j.optString("createdAt"),
            revokedAt = j.optString("revokedAt").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private fun parseRequestLog(j: JSONObject) = ApiRequestLog(
        id = j.optString("id"),
        endpoint = j.optString("endpoint"),
        method = j.optString("method", "POST"),
        modelId = j.optString("modelId"),
        providerId = j.optString("providerId"),
        statusCode = j.optInt("statusCode", 200),
        latencyMs = j.optLong("latencyMs", 0L),
        creditsCharged = j.optLong("creditsCharged", 0L),
        tokensUsed = j.optLong("tokensUsed", 0L),
        errorMessage = j.optString("errorMessage"),
        createdAt = j.optString("createdAt")
    )

    private fun parseSubscription(j: JSONObject) = SubscriptionRecord(
        id = j.optString("id"),
        userId = j.optString("userId"),
        planId = j.optString("planId", "FREE"),
        planCategory = j.optString("planCategory", "CONSUMER"),
        billingInterval = j.optString("billingInterval", "MONTHLY"),
        status = j.optString("status", "CREATED"),
        razorpaySubscriptionId = j.optString("razorpaySubscriptionId"),
        amountInr = j.optLong("amountInr", 0L),
        shortUrl = j.optString("shortUrl"),
        nextBillingAt = j.optString("nextBillingAt").takeIf { it.isNotBlank() && it != "null" },
        cancelAtPeriodEnd = j.optBoolean("cancelAtPeriodEnd", false),
        createdAt = j.optString("createdAt"),
        updatedAt = j.optString("updatedAt")
    )

    private fun parsePayment(j: JSONObject) = PaymentRecord(
        id = j.optString("id"),
        userId = j.optString("userId"),
        planId = j.optString("planId"),
        amountPaise = j.optLong("amountPaise", 0L),
        currency = j.optString("currency", "INR"),
        status = j.optString("status", "CREATED"),
        razorpayOrderId = j.optString("razorpayOrderId"),
        razorpayPaymentId = j.optString("razorpayPaymentId"),
        razorpaySubscriptionId = j.optString("razorpaySubscriptionId"),
        method = j.optString("method"),
        receiptNumber = j.optString("receiptNumber"),
        refundId = j.optString("refundId"),
        failureReason = j.optString("failureReason"),
        createdAt = j.optString("createdAt")
    )

    private fun parseWallet(j: JSONObject) = CreditWallet(
        userId = j.optString("userId"),
        balance = j.optLong("balance", 0L),
        subscriptionCredits = j.optLong("subscriptionCredits", 0L),
        purchasedCredits = j.optLong("purchasedCredits", 0L),
        lifetimeUsed = j.optLong("lifetimeUsed", 0L),
        lifetimeRefunded = j.optLong("lifetimeRefunded", 0L),
        updatedAt = j.optString("updatedAt")
    )

    private fun parseCheckout(j: JSONObject) = CheckoutLaunchData(
        razorpayKeyId = j.optString("razorpayKeyId"),
        razorpaySubscriptionId = j.optString("razorpaySubscriptionId"),
        razorpayOrderId = j.optString("razorpayOrderId"),
        amountPaise = j.optLong("amountPaise", 0L),
        shortUrl = j.optString("shortUrl"),
        checkoutPageUrl = j.optString("checkoutPageUrl")
    )
}
