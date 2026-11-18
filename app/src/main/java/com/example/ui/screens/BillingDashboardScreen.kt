package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.data.model.AiModelCatalog
import com.example.data.model.CheckoutLaunchData
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
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.RoseError
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.BillingViewModel

@Composable
fun BillingDashboardScreen(
    billingViewModel: BillingViewModel,
    credits: Long,
    currentPlanId: String,
    onOpenPricing: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val summaryState by billingViewModel.summaryState.collectAsStateWithLifecycle()
    val checkoutSession by billingViewModel.checkoutSession.collectAsStateWithLifecycle()
    val billingNotice by billingViewModel.billingNotice.collectAsStateWithLifecycle()
    val receiptHtml by billingViewModel.receiptHtml.collectAsStateWithLifecycle()
    val isBusy by billingViewModel.isBusy.collectAsStateWithLifecycle()

    LaunchedEffect(billingNotice) {
        if (!billingNotice.isNullOrBlank()) {
            Toast.makeText(context, billingNotice, Toast.LENGTH_LONG).show()
            billingViewModel.clearNotice()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "Billing & Credit Wallet",
            subtitle = "Razorpay Subscriptions & Receipts",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Subscription & Wallet Overview", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    OutlinedButton(onClick = { billingViewModel.refreshBillingSummary() }) {
                        Icon(Icons.Filled.Refresh, null, tint = LuminousCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh", color = LuminousCyan)
                    }
                }
            }

            when (val s = summaryState) {
                is UiState.Loading -> item { ShimmerLoadingCard("Fetching verified wallet & billing status...") }
                is UiState.Error -> {
                    item {
                        StatusBanner(
                            title = "Backend Billing Service Status",
                            message = "${s.message}\n\nNote: Live Razorpay billing, webhook verification, and credit top-ups require the CEREVIX backend service to be running with configured Razorpay credentials.",
                            isError = true
                        )
                    }
                    item {
                        Button(
                            onClick = onOpenPricing,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) {
                            Text("View Consumer & Developer Plans")
                        }
                    }
                }
                is UiState.Success -> {
                    val summary = s.data
                    val activeSub = summary.activeSubscription
                    if (!summary.razorpayConfigured) {
                        item {
                            StatusBanner(
                                title = "Razorpay Test/Live Keys Required",
                                message = "Backend reached, but RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET are not set yet. Subscriptions cannot activate until real Razorpay credentials & webhooks are verified.",
                                isOperational = false
                            )
                        }
                    }

                    // Active Subscription Card
                    item {
                        GlassCard(borderColor = LuminousCyan.copy(alpha = 0.45f)) {
                            Text("Current Subscription", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        activeSub?.planId ?: currentPlanId,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = if (activeSub != null)
                                            "₹${activeSub.amountInr}/${activeSub.billingInterval.lowercase()} • Next billing: ${activeSub.nextBillingAt?.take(10) ?: "Pending payment"}"
                                        else "Free tier • Upgrade anytime via Razorpay UPI / Card",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = (if (activeSub?.status == "ACTIVE") EmeraldSuccess else AmberWarning).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        activeSub?.status ?: "FREE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (activeSub?.status == "ACTIVE") EmeraldSuccess else AmberWarning,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onOpenPricing,
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("billing_upgrade_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                ) {
                                    Text("Upgrade / Change Plan")
                                }
                                if (activeSub != null && activeSub.status == "ACTIVE" && !activeSub.cancelAtPeriodEnd) {
                                    OutlinedButton(
                                        onClick = { billingViewModel.cancelSubscription(activeSub.id) },
                                        enabled = !isBusy,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        border = BorderStroke(1.dp, RoseError.copy(alpha = 0.6f))
                                    ) {
                                        Text("Cancel", color = RoseError)
                                    }
                                }
                            }
                        }
                    }

                    // Credit Wallet Breakdown
                    item {
                        GlassCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AccountBalanceWallet, null, tint = LuminousCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Credit Wallet", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Available", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text("${summary.wallet.balance}⚡", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFont), color = LuminousCyan)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Used", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text("${summary.wallet.lifetimeUsed}⚡", style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFont), color = TextPrimary)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Refunded", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text("${summary.wallet.lifetimeRefunded}⚡", style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFont), color = EmeraldSuccess)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Purchase Additional Credit Packs", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AiModelCatalog.creditPacks.forEach { pack ->
                                    OutlinedButton(
                                        onClick = { billingViewModel.startCreditPackOrder(pack.id) },
                                        enabled = !isBusy,
                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(pack.label, color = TextPrimary)
                                            Text("₹${pack.priceInr} via Razorpay", color = LuminousCyan, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Payment History & Receipts
                    item {
                        Text("Payment History & Invoices", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    }
                    if (summary.payments.isEmpty()) {
                        item {
                            GlassCard {
                                Text("No payment transactions recorded yet.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    } else {
                        items(summary.payments, key = { it.id }) { pay ->
                            val inr = pay.amountPaise / 100.0
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
                                        Text("${pay.planId} • ₹${"%.2f".format(inr)}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        Text(
                                            "${pay.status} • ${pay.receiptNumber} • ${pay.method.ifBlank { "razorpay" }}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFont),
                                            color = if (pay.status == "CAPTURED") EmeraldSuccess else if (pay.status == "FAILED") RoseError else AmberWarning
                                        )
                                        if (pay.failureReason.isNotBlank()) {
                                            Text(pay.failureReason, style = MaterialTheme.typography.labelSmall, color = RoseError)
                                        }
                                    }
                                    TextButton(onClick = { billingViewModel.loadReceipt(pay.id) }) {
                                        Icon(Icons.Filled.ReceiptLong, null, tint = LuminousCyan, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Receipt", color = LuminousCyan)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> Unit
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

    // Invoice Receipt Preview & Share Dialog
    receiptHtml?.let { html ->
        AlertDialog(
            onDismissRequest = { billingViewModel.dismissReceipt() },
            containerColor = MidnightNavy,
            title = { Text("Invoice / Payment Receipt", color = TextPrimary) },
            text = {
                Text(
                    html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/html"
                            putExtra(Intent.EXTRA_SUBJECT, "CEREVIX AI Invoice Receipt")
                            putExtra(Intent.EXTRA_TEXT, html)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Receipt"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Download / Share")
                }
            },
            dismissButton = {
                TextButton(onClick = { billingViewModel.dismissReceipt() }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun RazorpayCheckoutDialog(
    session: CheckoutLaunchData,
    isBusy: Boolean,
    onVerify: (paymentId: String, signature: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var verifyPayId by remember(session) { mutableStateOf("") }
    var verifySig by remember(session) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightNavy,
        title = { Text("Razorpay Secure Checkout", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "1. Open Razorpay Checkout to pay via UPI, Card, or Netbanking.\n2. Subscriptions and credits activate ONLY after backend signature & webhook verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Button(
                    onClick = {
                        val targetUrl = session.shortUrl.ifBlank { session.checkoutPageUrl }
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                        }.onFailure {
                            Toast.makeText(context, "Unable to open browser: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Icon(Icons.Filled.OpenInBrowser, null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Razorpay Checkout (₹${session.amountPaise / 100})")
                }
                OutlinedTextField(
                    value = verifyPayId,
                    onValueChange = { verifyPayId = it },
                    label = { Text("razorpay_payment_id") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = verifySig,
                    onValueChange = { verifySig = it },
                    label = { Text("razorpay_signature") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onVerify(verifyPayId, verifySig) },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
            ) {
                Icon(Icons.Filled.VerifiedUser, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Verify on Backend")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}
