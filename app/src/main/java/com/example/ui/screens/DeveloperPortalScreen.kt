package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
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
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseError
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.DeveloperViewModel

@Composable
fun DeveloperPortalScreen(
    viewModel: DeveloperViewModel,
    credits: Long,
    planName: String,
    onOpenPricing: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val devState by viewModel.developerState.collectAsStateWithLifecycle()
    val keysState by viewModel.keysState.collectAsStateWithLifecycle()
    val analyticsState by viewModel.analyticsState.collectAsStateWithLifecycle()
    val oneTimeKey by viewModel.oneTimeCreatedKey.collectAsStateWithLifecycle()
    val playgroundState by viewModel.playgroundState.collectAsStateWithLifecycle()
    val gatewayUrl by viewModel.gatewayUrl.collectAsStateWithLifecycle()
    val devMsg by viewModel.devMessage.collectAsStateWithLifecycle()

    LaunchedEffect(devMsg) {
        if (!devMsg.isNullOrBlank()) {
            Toast.makeText(context, devMsg, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 Keys, 1 Analytics, 2 Docs, 3 Playground
    var orgInput by remember { mutableStateOf("") }
    var newKeyName by remember { mutableStateOf("Production Key") }
    var gatewayInput by remember(gatewayUrl) { mutableStateOf(gatewayUrl) }

    // Playground inputs
    var playgroundEndpoint by remember { mutableStateOf("/v1/chat/completions") }
    var playgroundMethod by remember { mutableStateOf("POST") }
    var playgroundApiKey by remember { mutableStateOf("") }
    var playgroundJson by remember {
        mutableStateOf(
            """{"model":"gemini-3.8-flash","messages":[{"role":"user","content":"Hello CEREVIX API!"}]}"""
        )
    }

    val playgroundPresets = listOf(
        Triple("Chat", "POST", "/v1/chat/completions") to
            """{"model":"gemini-3.8-flash","messages":[{"role":"user","content":"Summarize quantum computing in 2 lines."}]}""",
        Triple("Image", "POST", "/v1/images/generations") to
            """{"model":"gemini-nano-banana-2.1","prompt":"Futuristic dark cobalt AI core emblem"}""",
        Triple("Video Job", "POST", "/v1/videos/generations") to
            """{"model":"cerevix-video-v1","prompt":"Drone flyover of neon cyberpunk skyline"}""",
        Triple("TTS", "POST", "/v1/audio/speech") to
            """{"model":"cerevix-speech-v1","input":"Welcome to CEREVIX AI platform."}""",
        Triple("SFX", "POST", "/v1/audio/sfx") to
            """{"model":"cerevix-sfx-v1","prompt":"Futuristic UI holographic whoosh"}""",
        Triple("Models", "GET", "/v1/models") to "",
        Triple("Credits", "GET", "/v1/credits") to "",
        Triple("Usage", "GET", "/v1/usage") to ""
    )

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "CEREVIX API Platform",
            subtitle = "api.cerevix.ai/v1 • Developer Console",
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
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("API Keys", "Analytics & Logs", "API Docs", "Playground").forEachIndexed { idx, label ->
                        FilterChip(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            label = { Text(label) },
                            modifier = Modifier.testTag("dev_tab_$idx")
                        )
                    }
                }
            }

            // Gateway endpoint switcher card (helps switch between production & local emulator test server)
            item {
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Backend Gateway Endpoint", style = MaterialTheme.typography.titleSmall, color = LuminousCyan)
                        TextButton(onClick = { viewModel.refreshAll() }) {
                            Icon(Icons.Filled.Refresh, null, tint = LuminousCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync", color = LuminousCyan)
                        }
                    }
                    OutlinedTextField(
                        value = gatewayInput,
                        onValueChange = { gatewayInput = it },
                        label = { Text("Gateway Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                gatewayInput = "https://api.cerevix.ai"
                                viewModel.updateGatewayUrl(gatewayInput)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Production", color = TextPrimary)
                        }
                        OutlinedButton(
                            onClick = {
                                gatewayInput = "http://10.0.2.2:8080"
                                viewModel.updateGatewayUrl(gatewayInput)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Local (10.0.2.2)", color = LuminousCyan)
                        }
                        Button(
                            onClick = { viewModel.updateGatewayUrl(gatewayInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    when (val d = devState) {
                        is UiState.Loading -> item { ShimmerLoadingCard("Connecting to CEREVIX API Gateway...") }
                        is UiState.Error -> item {
                            StatusBanner("Gateway Connection Status", d.message, isError = true)
                        }
                        is UiState.Success -> {
                            val (dev, wallet) = d.data
                            if (dev == null) {
                                item {
                                    GlassCard(borderColor = LuminousCyan.copy(alpha = 0.5f)) {
                                        Text("Register as a CEREVIX Developer", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "Create your developer workspace to generate SHA-256 HMAC API keys for Chat, Image, Video, Speech, and SFX endpoints.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        OutlinedTextField(
                                            value = orgInput,
                                            onValueChange = { orgInput = it },
                                            label = { Text("Organization / App Name") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = { viewModel.registerDeveloper(orgInput) },
                                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("register_developer_button"),
                                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                        ) {
                                            Icon(Icons.Filled.Api, null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Register Developer Account")
                                        }
                                    }
                                }
                            } else {
                                item {
                                    GlassCard(borderColor = ElectricBlue.copy(alpha = 0.5f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(dev.organizationName, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                                                Text(
                                                    "${dev.developerPlanId} • ${dev.rateLimitRpm} RPM • ${wallet.balance}⚡ API Credits",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFont),
                                                    color = LuminousCyan
                                                )
                                            }
                                            OutlinedButton(onClick = onOpenPricing) {
                                                Text("Dev Plans", color = LuminousCyan)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }

                    // Generate Key Card
                    item {
                        GlassCard {
                            Text("Generate New API Key", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Plaintext keys are shown only once and hashed with HMAC-SHA256 on the backend.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newKeyName,
                                onValueChange = { newKeyName = it },
                                label = { Text("Key Label (e.g. Production Android, CI/CD)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    viewModel.createApiKey(
                                        newKeyName,
                                        listOf("chat", "images", "audio", "video")
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("create_api_key_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                            ) {
                                Icon(Icons.Filled.Add, null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generate API Key")
                            }
                        }
                    }

                    // Existing Keys List
                    when (val kState = keysState) {
                        is UiState.Success -> {
                            items(kState.data, key = { it.id }) { keyRec ->
                                val isActive = keyRec.status == "ACTIVE"
                                GlassCard(borderColor = if (isActive) BorderGlass else RoseError.copy(alpha = 0.4f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Key, null, tint = if (isActive) LuminousCyan else RoseError, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(keyRec.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = (if (isActive) EmeraldSuccess else RoseError).copy(alpha = 0.18f)
                                        ) {
                                            Text(
                                                keyRec.status,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isActive) EmeraldSuccess else RoseError,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "${keyRec.keyPrefix}••••••••••••••••  (${keyRec.rateLimitRpm} RPM • ${keyRec.requestCount} calls)",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFont),
                                        color = TextSecondary
                                    )
                                    if (isActive) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = { viewModel.regenerateKey(keyRec.id) },
                                                modifier = Modifier.weight(1f).height(44.dp)
                                            ) {
                                                Text("Regenerate", color = LuminousCyan)
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.revokeKey(keyRec.id) },
                                                modifier = Modifier.weight(1f).height(44.dp),
                                                border = BorderStroke(1.dp, RoseError.copy(alpha = 0.6f))
                                            ) {
                                                Text("Revoke", color = RoseError)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                1 -> {
                    when (val aState = analyticsState) {
                        is UiState.Loading -> item { ShimmerLoadingCard("Loading API usage analytics...") }
                        is UiState.Error -> item { StatusBanner("Analytics Unavailable", aState.message, isError = true) }
                        is UiState.Success -> {
                            val a = aState.data
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    GlassCard(modifier = Modifier.weight(1f)) {
                                        Text("Requests", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text("${a.totalRequests}", style = MaterialTheme.typography.headlineMedium, color = LuminousCyan)
                                    }
                                    GlassCard(modifier = Modifier.weight(1f)) {
                                        Text("Errors", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text("${a.errorCount}", style = MaterialTheme.typography.headlineMedium, color = if (a.errorCount > 0) RoseError else EmeraldSuccess)
                                    }
                                    GlassCard(modifier = Modifier.weight(1f)) {
                                        Text("Avg Latency", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text("${a.avgLatencyMs}ms", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                                    }
                                }
                            }
                            items(a.recentRequests, key = { it.id }) { reqLog ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SlateSurface,
                                    border = BorderStroke(1.dp, BorderGlass),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "${reqLog.method} ${reqLog.endpoint} • HTTP ${reqLog.statusCode} (${reqLog.latencyMs}ms)",
                                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFont),
                                            color = if (reqLog.statusCode < 400) EmeraldSuccess else RoseError
                                        )
                                        Text(
                                            "Model: ${reqLog.modelId.ifBlank { "-" }} • Provider: ${reqLog.providerId.ifBlank { "-" }} • -${reqLog.creditsCharged}⚡",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                        if (reqLog.errorMessage.isNotBlank()) {
                                            Text(reqLog.errorMessage, style = MaterialTheme.typography.labelSmall, color = RoseError)
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                2 -> {
                    item {
                        ApiDocumentationCard(baseUrl = "$gatewayUrl/v1", context = context)
                    }
                }

                else -> {
                    item {
                        GlassCard {
                            Text("Interactive API Testing Playground", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                playgroundPresets.forEach { (meta, bodySample) ->
                                    val (label, method, path) = meta
                                    FilterChip(
                                        selected = playgroundEndpoint == path,
                                        onClick = {
                                            playgroundMethod = method
                                            playgroundEndpoint = path
                                            playgroundJson = bodySample
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = playgroundApiKey,
                                onValueChange = { playgroundApiKey = it },
                                label = { Text("CEREVIX API Key (crvx_live_...)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "$playgroundMethod $gatewayUrl$playgroundEndpoint",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFont),
                                color = LuminousCyan
                            )
                            if (playgroundMethod != "GET") {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = playgroundJson,
                                    onValueChange = { playgroundJson = it },
                                    label = { Text("JSON Request Body") },
                                    minLines = 3,
                                    maxLines = 8,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    viewModel.runPlayground(playgroundMethod, playgroundEndpoint, playgroundApiKey, playgroundJson)
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("run_playground_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                            ) {
                                Icon(Icons.Filled.PlayArrow, null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Send Live API Request")
                            }
                        }
                    }

                    when (val pg = playgroundState) {
                        is UiState.Loading -> item { ShimmerLoadingCard("Executing request against CEREVIX API Gateway...") }
                        is UiState.Error -> item { StatusBanner("Request Error", pg.message, isError = true) }
                        is UiState.Success -> item {
                            val r = pg.data
                            GlassCard(borderColor = if (r.statusCode < 400) EmeraldSuccess.copy(alpha = 0.5f) else RoseError.copy(alpha = 0.5f)) {
                                Text(
                                    "HTTP ${r.statusCode} • ${r.latencyMs}ms",
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFont),
                                    color = if (r.statusCode < 400) EmeraldSuccess else RoseError
                                )
                                if (r.headersText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        r.headersText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFont),
                                        color = LuminousCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    r.bodyText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFont),
                                    color = TextPrimary
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    // One-time Full Key Reveal Modal
    oneTimeKey?.let { created ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissOneTimeKey() },
            containerColor = MidnightNavy,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = AmberWarning)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Your API Key Now", color = TextPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        "For security, CEREVIX stores only an HMAC-SHA256 hash and will NEVER display this key again after you close this dialog.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberWarning
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = ObsidianBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, LuminousCyan)
                    ) {
                        Text(
                            text = created.plaintextKey,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFont,
                                fontWeight = FontWeight.Bold
                            ),
                            color = LuminousCyan,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        copyText(context, "CEREVIX API Key", created.plaintextKey)
                        playgroundApiKey = created.plaintextKey
                        viewModel.dismissOneTimeKey()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy & Load in Playground")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissOneTimeKey() }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ApiDocumentationCard(baseUrl: String, context: Context) {
    val jsExample = """
const res = await fetch("$baseUrl/chat/completions", {
  method: "POST",
  headers: {
    "Authorization": "Bearer crvx_live_YOUR_KEY",
    "Content-Type": "application/json"
  },
  body: JSON.stringify({
    model: "gemini-3.8-flash",
    messages: [{ role: "user", content: "Hello CEREVIX!" }]
  })
});
console.log(await res.json());
    """.trimIndent()

    val pyExample = """
import requests
resp = requests.post(
    "$baseUrl/chat/completions",
    headers={"Authorization": "Bearer crvx_live_YOUR_KEY"},
    json={"model": "gemini-3.8-flash", "messages": [{"role": "user", "content": "Hello!"}]}
)
print(resp.json())
    """.trimIndent()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard {
            Text("Base URL & Authentication", style = MaterialTheme.typography.titleMedium, color = LuminousCyan)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Base URL: $baseUrl", style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFont), color = TextPrimary)
            Text("Header: Authorization: Bearer crvx_live_...", style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFont), color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Endpoints", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            listOf(
                "POST /chat/completions — Chat & reasoning (2–5⚡)",
                "POST /images/generations — Nano Banana 2.1 / Pro (5–15⚡)",
                "POST /videos/generations — Async video render job (25⚡)",
                "POST /audio/speech — Speech synthesis (4⚡)",
                "POST /audio/sfx — Sound effects synthesis (5⚡)",
                "GET /models — Enabled models & supplier attribution",
                "GET /usage — Request logs & consumption summary",
                "GET /credits — Live wallet balance",
                "GET /jobs/{id} — Async job status & output URL"
            ).forEach { line ->
                Text("• $line", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Error Codes & Rate Limits", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(
                "401 Invalid/Revoked Key • 402 Insufficient Credits • 429 Rate Limit (check Retry-After & X-RateLimit-Remaining) • 503 Provider Unconfigured (credits auto-refunded)",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        GlassCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("JavaScript / Node.js", style = MaterialTheme.typography.titleMedium, color = LuminousCyan)
                TextButton(onClick = { copyText(context, "JS Snippet", jsExample) }) {
                    Text("Copy", color = LuminousCyan)
                }
            }
            Text(jsExample, style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFont), color = TextPrimary)
        }

        GlassCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Python", style = MaterialTheme.typography.titleMedium, color = LuminousCyan)
                TextButton(onClick = { copyText(context, "Python Snippet", pyExample) }) {
                    Text("Copy", color = LuminousCyan)
                }
            }
            Text(pyExample, style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFont), color = TextPrimary)
        }
    }
}

private fun copyText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}
