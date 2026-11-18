package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.data.model.AiModelCatalog
import com.example.data.model.CreditTransaction
import com.example.data.model.PlanCategory
import com.example.data.model.SubscriptionPlan
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusBanner
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.RoseError
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.BillingViewModel

@Composable
fun AccountScreen(
    profileState: UiState<UserProfile?>,
    transactionsState: UiState<List<CreditTransaction>>,
    isAdmin: Boolean,
    onUpdatePreferences: (String, String) -> Unit,
    onClearWorkspace: () -> Unit,
    onOpenPricing: () -> Unit,
    onOpenBilling: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenAdmin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val profile = (profileState as? UiState.Success)?.data
    var nameInput by remember(profile?.displayName) { mutableStateOf(profile?.displayName ?: "") }
    var preferredModel by remember(profile?.preferredModel) {
        mutableStateOf(profile?.preferredModel ?: AiModelCatalog.DEFAULT_TEXT_MODEL.id)
    }
    var showClearConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(ElectricBlue.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = "User avatar", tint = LuminousCyan)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile?.displayName ?: "Cerevix Creator",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = profile?.email ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "Plan: ${profile?.planId ?: "FREE"} • Role: ${profile?.role ?: "USER"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = LuminousCyan
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenPricing,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("account_pricing_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Icon(Icons.Filled.WorkspacePremium, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Plans & Pricing")
                    }
                    OutlinedButton(
                        onClick = onOpenBilling,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("account_billing_button"),
                        border = BorderStroke(1.dp, LuminousCyan)
                    ) {
                        Icon(Icons.Filled.AccountBalanceWallet, null, tint = LuminousCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Billing & Wallet", color = LuminousCyan)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onOpenDeveloper,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("account_developer_button"),
                        border = BorderStroke(1.dp, ElectricBlue)
                    ) {
                        Icon(Icons.Filled.Api, null, tint = LuminousCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CEREVIX API Portal", color = TextPrimary)
                    }
                    if (isAdmin) {
                        OutlinedButton(
                            onClick = onOpenAdmin,
                            modifier = Modifier.weight(1f).height(48.dp).testTag("account_admin_button"),
                            border = BorderStroke(1.dp, LuminousCyan)
                        ) {
                            Icon(Icons.Filled.AdminPanelSettings, null, tint = LuminousCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Owner Admin", color = LuminousCyan)
                        }
                    }
                }
            }
        }

        item {
            GlassCard {
                Text("Profile & Default Model", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("Preferred Intelligence Model", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiModelCatalog.textModels.forEach { m ->
                        FilterChip(
                            selected = preferredModel == m.id,
                            onClick = { preferredModel = m.id },
                            label = { Text("${m.label} — ${m.subtitle}") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onUpdatePreferences(nameInput, preferredModel) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Save Preferences")
                }
            }
        }

        item {
            Text("Credit Ledger", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }

        when (transactionsState) {
            is UiState.Loading -> item {
                Text("Loading ledger...", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            is UiState.Error -> item {
                StatusBanner("Ledger Error", transactionsState.message, isError = true)
            }
            is UiState.Success -> {
                if (transactionsState.data.isEmpty()) {
                    item {
                        GlassCard {
                            Text("No credit transactions recorded yet.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                } else {
                    items(transactionsState.data.take(20), key = { it.id }) { tx ->
                        val isPositive = tx.amount >= 0
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SlateSurface,
                            border = BorderStroke(1.dp, BorderGlass),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tx.description, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                    Text(
                                        text = "${tx.txType} • Balance: ${tx.balanceAfter}⚡",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = if (isPositive) "+${tx.amount}⚡" else "${tx.amount}⚡",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMonoFont,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (isPositive) EmeraldSuccess else LuminousCyan
                                )
                            }
                        }
                    }
                }
            }
            else -> Unit
        }

        item {
            GlassCard(borderColor = RoseError.copy(alpha = 0.4f)) {
                Text("Privacy & Session Controls", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Manage your cloud conversations, saved creations, and active Google session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    border = BorderStroke(1.dp, RoseError.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Filled.DeleteForever, null, tint = RoseError)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Saved Projects & Chat History", color = RoseError)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        AuthRepository.signOut(credentialManager, scope)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SlateSurface, contentColor = TextPrimary)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sign Out")
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = MidnightNavy,
            title = { Text("Delete Workspace Data?", color = TextPrimary) },
            text = {
                Text(
                    "This permanently deletes your saved projects and chat threads in Firestore. Your credit balance and account remain intact.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearWorkspace()
                    showClearConfirm = false
                }) {
                    Text("Delete", color = RoseError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun PricingScreen(
    plans: List<SubscriptionPlan>,
    currentPlanId: String,
    credits: Long,
    billingViewModel: BillingViewModel,
    onOpenBilling: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val summaryState by billingViewModel.summaryState.collectAsStateWithLifecycle()
    val checkoutSession by billingViewModel.checkoutSession.collectAsStateWithLifecycle()
    val billingNotice by billingViewModel.billingNotice.collectAsStateWithLifecycle()
    val isBusy by billingViewModel.isBusy.collectAsStateWithLifecycle()

    LaunchedEffect(billingNotice) {
        if (!billingNotice.isNullOrBlank()) {
            Toast.makeText(context, billingNotice, Toast.LENGTH_LONG).show()
            billingViewModel.clearNotice()
        }
    }

    var annualBilling by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(PlanCategory.CONSUMER) }

    val mergedPlans = remember(plans) {
        val byId = plans.associateBy { it.id }
        AiModelCatalog.defaultPlans.map { def -> byId[def.id] ?: def } +
            plans.filter { p -> AiModelCatalog.defaultPlans.none { it.id == p.id } }
    }
    val filteredPlans = mergedPlans
        .filter { it.active && it.resolvedCategory() == selectedCategory }
        .sortedBy { it.sortOrder }

    val razorpayConfigured = (summaryState as? UiState.Success)?.data?.razorpayConfigured ?: false

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "Plans & Subscriptions",
            subtitle = "Razorpay Consumer & API Plans",
            credits = credits,
            planName = currentPlanId,
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                if (summaryState is UiState.Error) {
                    StatusBanner(
                        title = "Gateway Connection Required for Live Checkout",
                        message = "Backend gateway unreachable. Live Razorpay checkout requires the CEREVIX backend service with RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET and webhook verification.",
                        isOperational = false
                    )
                } else if (!razorpayConfigured) {
                    StatusBanner(
                        title = "Razorpay Setup Incomplete",
                        message = "Backend connected, awaiting live/test Razorpay credentials and subscription plan IDs. Paid upgrades cannot activate without signature verification.",
                        isOperational = false
                    )
                } else {
                    StatusBanner(
                        title = "Razorpay Billing Active",
                        message = "UPI, Cards & Netbanking enabled with server-verified signatures and idempotent webhooks.",
                        isOperational = true
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == PlanCategory.CONSUMER,
                        onClick = { selectedCategory = PlanCategory.CONSUMER },
                        label = { Text("Consumer Plans") },
                        modifier = Modifier.testTag("pricing_tab_consumer")
                    )
                    FilterChip(
                        selected = selectedCategory == PlanCategory.DEVELOPER,
                        onClick = { selectedCategory = PlanCategory.DEVELOPER },
                        label = { Text("Developer API Plans") },
                        modifier = Modifier.testTag("pricing_tab_developer")
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenBilling) {
                        Text("Wallet & Receipts", color = LuminousCyan)
                    }
                }
            }

            item {
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Annual Billing", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Save ~17% vs monthly pricing", style = MaterialTheme.typography.bodySmall, color = LuminousCyan)
                        }
                        Switch(
                            checked = annualBilling,
                            onCheckedChange = { annualBilling = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = LuminousCyan, checkedTrackColor = ElectricBlue)
                        )
                    }
                }
            }

            items(filteredPlans, key = { it.id }) { plan ->
                val isCurrent = plan.id.equals(currentPlanId, ignoreCase = true) ||
                    plan.name.equals(currentPlanId, ignoreCase = true)
                val displayPrice = if (annualBilling) plan.annualPriceInr else plan.monthlyPriceInr
                val periodLabel = if (annualBilling) "/year" else "/month"
                val isEnterprise = plan.id == "ENTERPRISE"

                GlassCard(
                    borderColor = if (isCurrent) LuminousCyan else BorderGlass
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(plan.name, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isCurrent) EmeraldSuccess.copy(alpha = 0.2f) else ElectricBlue.copy(alpha = 0.22f)
                        ) {
                            Text(
                                text = if (isCurrent) "ACTIVE PLAN" else plan.badge.ifBlank { "TIER" },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCurrent) EmeraldSuccess else LuminousCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (isEnterprise) "Custom" else "₹$displayPrice",
                            style = MaterialTheme.typography.displayLarge.copy(fontFamily = JetBrainsMonoFont),
                            color = TextPrimary
                        )
                        if (!isEnterprise) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = periodLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }
                    Text(
                        text = "${plan.monthlyCredits} monthly credits • ${plan.rateLimitRpm} RPM",
                        style = MaterialTheme.typography.labelLarge,
                        color = LuminousCyan
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    plan.features.forEach { feat ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Check, null, tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(feat, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                    }
                    if (!isCurrent && !isEnterprise && plan.monthlyPriceInr > 0L) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                billingViewModel.startSubscriptionCheckout(
                                    planId = plan.id,
                                    billingInterval = if (annualBilling) "ANNUAL" else "MONTHLY"
                                )
                            },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("subscribe_${plan.id}"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) {
                            Text("Subscribe via Razorpay (₹$displayPrice$periodLabel)")
                        }
                    } else if (isEnterprise) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Contact CEREVIX Owner to provision custom rate limits, dedicated routing, and enterprise billing.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }

    checkoutSession?.let { session ->
        RazorpayCheckoutDialog(
            session = session,
            isBusy = isBusy,
            onVerify = { payId, sig ->
                billingViewModel.verifyPaymentWithBackend(
                    paymentId = payId,
                    subscriptionId = session.razorpaySubscriptionId,
                    orderId = session.razorpayOrderId,
                    signature = sig
                )
            },
            onDismiss = { billingViewModel.dismissCheckout() }
        )
    }
}
