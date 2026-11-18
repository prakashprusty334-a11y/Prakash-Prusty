package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.data.model.AccountStatus
import com.example.data.model.AiModelCatalog
import com.example.data.model.AiModelRouting
import com.example.data.model.AiProviderConfig
import com.example.data.model.PlanTier
import com.example.data.model.SubscriptionPlan
import com.example.ui.components.GlassCard
import com.example.ui.components.ShimmerLoadingCard
import com.example.ui.components.StatusBanner
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.RoseError
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AdminViewModel

@Composable
fun AdminDashboardScreen(
    adminViewModel: AdminViewModel,
    plans: List<SubscriptionPlan>,
    credits: Long,
    planName: String,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val usersState by adminViewModel.usersState.collectAsStateWithLifecycle()
    val gatewayOverviewState by adminViewModel.gatewayOverview.collectAsStateWithLifecycle()
    val providersState by adminViewModel.providersState.collectAsStateWithLifecycle()
    val announcementsState by adminViewModel.announcementsState.collectAsStateWithLifecycle()
    val auditLogsState by adminViewModel.auditLogsState.collectAsStateWithLifecycle()
    val toastMsg by adminViewModel.adminToast.collectAsStateWithLifecycle()

    LaunchedEffect(toastMsg) {
        if (!toastMsg.isNullOrBlank()) {
            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
            adminViewModel.clearToast()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Overview",
        "Users & Wallets",
        "Providers & Routing",
        "Payments & Refunds",
        "Developers & Keys",
        "Plans Config",
        "Announcements",
        "Audit Logs"
    )

    // Announcement state
    var annTitle by remember { mutableStateOf("") }
    var annMessage by remember { mutableStateOf("") }
    var annSeverity by remember { mutableStateOf("INFO") }

    // Provider form state (credentials sent server-side only)
    var provId by remember { mutableStateOf("openai_compat") }
    var provName by remember { mutableStateOf("OpenAI Compatible Endpoint") }
    var provAdapter by remember { mutableStateOf("OPENAI_COMPATIBLE") }
    var provBaseUrl by remember { mutableStateOf("https://api.openai.com/v1") }
    var provFallback by remember { mutableStateOf("gemini") }
    var provPriority by remember { mutableStateOf("2") }
    var provSecret by remember { mutableStateOf("") }

    // Model routing form state
    var routeModelId by remember { mutableStateOf("gemini-3.8-flash") }
    var routeDisplayName by remember { mutableStateOf("Gemini 3.8 Flash") }
    var routeProviderId by remember { mutableStateOf("gemini") }
    var routeFallbackProviderId by remember { mutableStateOf("openai_compat") }
    var routeSuppliedBy by remember { mutableStateOf("External Provider: Google Gemini") }
    var routeCapability by remember { mutableStateOf("CHAT") }
    var routeCreditCost by remember { mutableStateOf("2") }
    var routeRpm by remember { mutableStateOf("60") }

    val mergedPlans = remember(plans) {
        val byId = plans.associateBy { it.id }
        AiModelCatalog.defaultPlans.map { def -> byId[def.id] ?: def } +
            plans.filter { p -> AiModelCatalog.defaultPlans.none { it.id == p.id } }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "Owner Governance Center",
            subtitle = "SaaS, Providers, Billing & API",
            credits = credits,
            planName = planName,
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val users = (usersState as? UiState.Success)?.data ?: emptyList()
                val overview = (gatewayOverviewState as? UiState.Success)?.data
                val activeSubs = overview?.activeSubscriptions ?: users.count { it.planId != PlanTier.FREE.name }
                val revenueInr = overview?.capturedRevenueInr ?: 0.0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassCard(modifier = Modifier.weight(1f)) {
                        Text("Users / Devs", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Text(
                            "${users.size} / ${overview?.totalDevelopers ?: 0}",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                    }
                    GlassCard(modifier = Modifier.weight(1f)) {
                        Text("Active Subs", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Text("$activeSubs", style = MaterialTheme.typography.titleLarge, color = LuminousCyan)
                    }
                    GlassCard(modifier = Modifier.weight(1f)) {
                        Text("Revenue", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Text("₹${revenueInr.toInt()}", style = MaterialTheme.typography.titleLarge, color = EmeraldSuccess)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEachIndexed { idx, label ->
                        FilterChip(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            label = { Text(label) },
                            modifier = Modifier.testTag("admin_tab_$idx")
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    // Overview & Gateway Telemetry
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Platform Gateway Telemetry", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            OutlinedButton(onClick = { adminViewModel.refreshGatewayAdminData() }) {
                                Icon(Icons.Filled.Refresh, null, tint = LuminousCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Refresh", color = LuminousCyan)
                            }
                        }
                    }
                    when (val ov = gatewayOverviewState) {
                        is UiState.Loading -> item { ShimmerLoadingCard("Loading owner backend metrics...") }
                        is UiState.Error -> item {
                            StatusBanner(
                                title = "Backend Gateway Unreachable",
                                message = "${ov.message}\n\nDeploy / start the CEREVIX backend service to manage live Razorpay refunds, API keys, and server-side AI provider credentials.",
                                isOperational = false
                            )
                        }
                        is UiState.Success -> {
                            val d = ov.data
                            item {
                                GlassCard {
                                    Text("API Requests: ${d.totalApiRequests} • Active Keys: ${d.activeApiKeys}", style = MaterialTheme.typography.titleMedium, color = LuminousCyan)
                                    Text("Captured Revenue: ₹${"%.2f".format(d.capturedRevenueInr)} • Developers: ${d.totalDevelopers}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                }
                            }
                            item {
                                Text("Recent API Errors", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            }
                            if (d.recentApiErrors.isEmpty()) {
                                item {
                                    GlassCard {
                                        Text("No recent API errors logged.", style = MaterialTheme.typography.bodySmall, color = EmeraldSuccess)
                                    }
                                }
                            } else {
                                items(d.recentApiErrors, key = { it.id }) { err ->
                                    GlassCard(borderColor = RoseError.copy(alpha = 0.5f)) {
                                        Text("${err.method} ${err.endpoint} • HTTP ${err.statusCode}", style = MaterialTheme.typography.labelLarge, color = RoseError)
                                        Text("Model: ${err.modelId} • Provider: ${err.providerId}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text(err.errorMessage, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                1 -> {
                    // Users & Credits (Existing preserved behavior)
                    when (val u = usersState) {
                        is UiState.Loading -> item { Text("Loading users...", color = TextSecondary) }
                        is UiState.Error -> item { StatusBanner("Admin Access Error", u.message, isError = true) }
                        is UiState.Success -> {
                            items(u.data, key = { it.userId }) { user ->
                                val isSuspended = user.resolvedStatus() == AccountStatus.SUSPENDED
                                GlassCard {
                                    Text(user.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Plan: ${user.planId} • Balance: ${user.creditBalance}⚡ • Status: ${user.status}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFont),
                                        color = if (isSuspended) RoseError else LuminousCyan
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { adminViewModel.grantCredits(user.userId, 500L, "Admin bonus grant") },
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                        ) {
                                            Text("+500⚡")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val nextPlan = when (user.resolvedPlan()) {
                                                    PlanTier.FREE -> PlanTier.PRO
                                                    PlanTier.PRO -> PlanTier.STUDIO
                                                    PlanTier.STUDIO -> PlanTier.BUSINESS
                                                    PlanTier.BUSINESS -> PlanTier.FREE
                                                }
                                                adminViewModel.updateUserPlanAndStatus(user.userId, nextPlan, user.resolvedStatus())
                                            },
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("Cycle Plan", color = LuminousCyan)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val toggled = if (isSuspended) AccountStatus.ACTIVE else AccountStatus.SUSPENDED
                                                adminViewModel.updateUserPlanAndStatus(user.userId, user.resolvedPlan(), toggled)
                                            },
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            border = BorderStroke(1.dp, if (isSuspended) EmeraldSuccess else RoseError)
                                        ) {
                                            Text(
                                                text = if (isSuspended) "Activate" else "Suspend",
                                                color = if (isSuspended) EmeraldSuccess else RoseError
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                2 -> {
                    // Providers & Model Routing (Owner-Only Module 2)
                    item {
                        StatusBanner(
                            title = "Zero-Leak Secret Policy",
                            message = "Provider API keys submitted here are stored ONLY in the backend server/secret manager and NEVER stored in client-readable Firestore documents or returned in API responses.",
                            isOperational = true
                        )
                    }
                    when (val p = providersState) {
                        is UiState.Loading -> item { ShimmerLoadingCard("Loading provider health & routing...") }
                        is UiState.Error -> item { StatusBanner("Provider Gateway Offline", p.message, isOperational = false) }
                        is UiState.Success -> {
                            items(p.data, key = { it.id }) { prov ->
                                GlassCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${prov.name} (${prov.id})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                            Text(
                                                "${prov.adapterType} • Priority ${prov.priority} • Fallback: ${prov.fallbackProviderId.ifBlank { "none" }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = LuminousCyan
                                            )
                                            Text(
                                                "Health: ${prov.healthStatus} • Cred: ${if (prov.hasServerCredential) "Server Configured" else "Missing"} • Reqs: ${prov.totalRequests} (Errs: ${prov.totalErrors})",
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFont),
                                                color = if (prov.hasServerCredential) EmeraldSuccess else AmberWarning
                                            )
                                        }
                                        Switch(
                                            checked = prov.enabled,
                                            onCheckedChange = { enabled ->
                                                adminViewModel.upsertProvider(prov.copy(enabled = enabled), "")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                    item {
                        GlassCard {
                            Text("Add / Configure AI Provider & Credential", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("GEMINI", "OPENAI_COMPATIBLE", "IMAGE", "VIDEO", "SPEECH", "AUDIO_SFX", "SELF_HOSTED").forEach { t ->
                                    FilterChip(selected = provAdapter == t, onClick = { provAdapter = t }, label = { Text(t) })
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = provId, onValueChange = { provId = it }, label = { Text("Provider ID (e.g. cerevix_llm)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(value = provName, onValueChange = { provName = it }, label = { Text("Display Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(value = provBaseUrl, onValueChange = { provBaseUrl = it }, label = { Text("Upstream Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = provFallback, onValueChange = { provFallback = it }, label = { Text("Fallback ID") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = provPriority, onValueChange = { provPriority = it.filter { c -> c.isDigit() } }, label = { Text("Priority") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = provSecret,
                                onValueChange = { provSecret = it },
                                label = { Text("Server API Credential (write-only)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    adminViewModel.upsertProvider(
                                        AiProviderConfig(
                                            id = provId.trim(),
                                            name = provName.trim(),
                                            adapterType = provAdapter,
                                            baseUrl = provBaseUrl.trim(),
                                            fallbackProviderId = provFallback.trim(),
                                            priority = provPriority.toLongOrNull() ?: 2L,
                                            enabled = true
                                        ),
                                        provSecret.trim()
                                    )
                                    provSecret = ""
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                            ) {
                                Text("Save Provider & Rotate Server Secret")
                            }
                        }
                    }
                    item {
                        GlassCard {
                            Text("Configure Model Routing & Credit Cost", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = routeModelId, onValueChange = { routeModelId = it }, label = { Text("Model ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(value = routeDisplayName, onValueChange = { routeDisplayName = it }, label = { Text("Model Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(value = routeSuppliedBy, onValueChange = { routeSuppliedBy = it }, label = { Text("Honest Supplier Attribution") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = routeProviderId, onValueChange = { routeProviderId = it }, label = { Text("Primary Provider") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = routeFallbackProviderId, onValueChange = { routeFallbackProviderId = it }, label = { Text("Fallback Provider") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = routeCapability, onValueChange = { routeCapability = it }, label = { Text("Capability") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = routeCreditCost, onValueChange = { routeCreditCost = it.filter { c -> c.isDigit() } }, label = { Text("Credit Cost") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = routeRpm, onValueChange = { routeRpm = it.filter { c -> c.isDigit() } }, label = { Text("RPM") }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    adminViewModel.upsertModelRouting(
                                        AiModelRouting(
                                            id = routeModelId.trim(),
                                            modelId = routeModelId.trim(),
                                            displayName = routeDisplayName.trim(),
                                            providerId = routeProviderId.trim(),
                                            fallbackProviderId = routeFallbackProviderId.trim(),
                                            suppliedBy = routeSuppliedBy.trim(),
                                            capability = routeCapability.trim(),
                                            creditCost = routeCreditCost.toLongOrNull() ?: 2L,
                                            rateLimitRpm = routeRpm.toLongOrNull() ?: 60L,
                                            enabled = true,
                                            isDefault = true
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                            ) {
                                Text("Save Model Routing")
                            }
                        }
                    }
                }

                3 -> {
                    // Payments & Razorpay Refunds
                    val payments = (gatewayOverviewState as? UiState.Success)?.data?.payments ?: emptyList()
                    if (payments.isEmpty()) {
                        item {
                            GlassCard {
                                Text("No payment transactions recorded on backend gateway yet.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    } else {
                        items(payments, key = { it.id }) { pay ->
                            GlassCard {
                                Text("${pay.planId} • ₹${"%.2f".format(pay.amountPaise / 100.0)} (${pay.status})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("User: ${pay.userId} • Receipt: ${pay.receiptNumber}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("Payment ID: ${pay.razorpayPaymentId.ifBlank { "pending" }}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFont), color = LuminousCyan)
                                if (pay.status == "CAPTURED" && pay.refundId.isBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { adminViewModel.refundPayment(pay.id, "Owner-issued refund via Admin Console") },
                                        border = BorderStroke(1.dp, RoseError)
                                    ) {
                                        Text("Issue Razorpay Refund", color = RoseError)
                                    }
                                }
                            }
                        }
                    }
                }

                4 -> {
                    // Developers & API Keys
                    val overview = (gatewayOverviewState as? UiState.Success)?.data
                    val devs = overview?.developers ?: emptyList()
                    val keys = overview?.apiKeys ?: emptyList()
                    item {
                        Text("Registered Developers (${devs.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    items(devs, key = { it.id }) { dev ->
                        GlassCard {
                            Text(dev.organizationName.ifBlank { dev.email }, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Plan: ${dev.developerPlanId} • ${dev.rateLimitRpm} RPM • Reqs: ${dev.totalApiRequests}", style = MaterialTheme.typography.labelSmall, color = LuminousCyan)
                        }
                    }
                    item {
                        Text("Issued CEREVIX API Keys (${keys.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    items(keys, key = { it.id }) { keyRec ->
                        GlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${keyRec.name} (${keyRec.keyPrefix}...)", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text("Status: ${keyRec.status} • Hits: ${keyRec.requestCount}", style = MaterialTheme.typography.labelSmall, color = if (keyRec.status == "ACTIVE") EmeraldSuccess else RoseError)
                                }
                                if (keyRec.status == "ACTIVE") {
                                    OutlinedButton(
                                        onClick = { adminViewModel.revokeDeveloperKey(keyRec.id) },
                                        border = BorderStroke(1.dp, RoseError)
                                    ) {
                                        Text("Revoke", color = RoseError)
                                    }
                                }
                            }
                        }
                    }
                }

                5 -> {
                    // Plans Config (Consumer + Developer)
                    item {
                        Button(
                            onClick = { adminViewModel.seedDefaultPlans() },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_seed_plans"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) {
                            Text("Sync Consumer & Developer Plans to Firestore")
                        }
                    }
                    items(mergedPlans, key = { it.id }) { plan ->
                        var priceInput by remember(plan.monthlyPriceInr) { mutableStateOf(plan.monthlyPriceInr.toString()) }
                        var creditsInput by remember(plan.monthlyCredits) { mutableStateOf(plan.monthlyCredits.toString()) }
                        var rpmInput by remember(plan.rateLimitRpm) { mutableStateOf(plan.rateLimitRpm.toString()) }
                        var rzpPlanInput by remember(plan.razorpayMonthlyPlanId) { mutableStateOf(plan.razorpayMonthlyPlanId) }

                        GlassCard {
                            Text("${plan.name} (${plan.planCategory})", style = MaterialTheme.typography.titleMedium, color = LuminousCyan)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = priceInput,
                                    onValueChange = { priceInput = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("INR/mo") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = creditsInput,
                                    onValueChange = { creditsInput = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Credits/mo") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = rpmInput,
                                    onValueChange = { rpmInput = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("RPM") },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = rzpPlanInput,
                                onValueChange = { rzpPlanInput = it },
                                label = { Text("Razorpay Monthly Plan ID (plan_...)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val mPrice = priceInput.toLongOrNull() ?: plan.monthlyPriceInr
                                    val mCreds = creditsInput.toLongOrNull() ?: plan.monthlyCredits
                                    val rpm = rpmInput.toLongOrNull() ?: plan.rateLimitRpm
                                    adminViewModel.upsertPlan(
                                        plan.copy(
                                            monthlyPriceInr = mPrice,
                                            annualPriceInr = mPrice * 10L,
                                            monthlyCredits = mCreds,
                                            rateLimitRpm = rpm,
                                            razorpayMonthlyPlanId = rzpPlanInput.trim()
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                            ) {
                                Text("Save ${plan.name} Config")
                            }
                        }
                    }
                }

                6 -> {
                    // Announcements (Preserved)
                    item {
                        GlassCard {
                            Text("Publish System Announcement", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("INFO", "WARNING", "CRITICAL").forEach { sev ->
                                    FilterChip(selected = annSeverity == sev, onClick = { annSeverity = sev }, label = { Text(sev) })
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = annTitle,
                                onValueChange = { annTitle = it },
                                label = { Text("Announcement title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = annMessage,
                                onValueChange = { annMessage = it },
                                label = { Text("Message") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    adminViewModel.publishAnnouncement(annTitle, annMessage, annSeverity)
                                    annTitle = ""
                                    annMessage = ""
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                            ) {
                                Text("Publish Announcement")
                            }
                        }
                    }
                    val anns = (announcementsState as? UiState.Success)?.data ?: emptyList()
                    items(anns, key = { it.id }) { ann ->
                        GlassCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ann.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(ann.message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                Switch(
                                    checked = ann.active,
                                    onCheckedChange = { adminViewModel.toggleAnnouncement(ann.id, it) }
                                )
                            }
                        }
                    }
                }

                else -> {
                    // Audit Logs (Preserved)
                    when (val logs = auditLogsState) {
                        is UiState.Loading -> item { Text("Loading audit logs...", color = TextSecondary) }
                        is UiState.Error -> item { StatusBanner("Audit Error", logs.message, isError = true) }
                        is UiState.Success -> {
                            items(logs.data, key = { it.id }) { log ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SlateSurface,
                                    border = BorderStroke(1.dp, BorderGlass),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("${log.action} • ${log.targetPath}", style = MaterialTheme.typography.labelLarge, color = LuminousCyan)
                                        Text(log.details, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                        Text("By: ${log.actorEmail}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}
