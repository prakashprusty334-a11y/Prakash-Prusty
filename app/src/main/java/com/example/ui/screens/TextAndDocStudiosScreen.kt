package com.example.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ExportUtils
import com.example.core.UiState
import com.example.data.model.AiModelCatalog
import com.example.data.model.ProjectCategory
import com.example.ui.components.GlassCard
import com.example.ui.components.ShimmerLoadingCard
import com.example.ui.components.StatusBanner
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.CreativeStudiosViewModel

@Composable
fun WritingStudioScreen(
    viewModel: CreativeStudiosViewModel,
    credits: Long,
    planName: String,
    accountSuspended: Boolean,
    onSaveProject: (String, ProjectCategory, String, String, String, Long) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val state by viewModel.writingState.collectAsStateWithLifecycle()

    val writingTools = listOf(
        "Blog Post", "Article", "Email", "Social Caption",
        "Instagram Carousel", "YouTube Script", "Product Description",
        "SEO Copy", "Grammar Fix", "Translate", "Summarize", "Paraphrase"
    )
    val tones = listOf("Executive", "Creative", "Technical", "Persuasive", "Casual")

    var selectedTool by remember { mutableStateOf(writingTools.first()) }
    var selectedTone by remember { mutableStateOf(tones.first()) }
    var targetLanguage by remember { mutableStateOf("English") }
    var prompt by remember { mutableStateOf("") }
    var selectedModelId by remember { mutableStateOf(AiModelCatalog.DEFAULT_TEXT_MODEL.id) }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar("AI Writing Studio", "12 Copy & Editorial Engines", credits, planName, onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassCard {
                    Text("Writing Mode", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        writingTools.forEach { t ->
                            FilterChip(selected = selectedTool == t, onClick = { selectedTool = t }, label = { Text(t) })
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tone", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tones.forEach { tone ->
                            FilterChip(selected = selectedTone == tone, onClick = { selectedTone = tone }, label = { Text(tone) })
                        }
                    }
                    if (selectedTool == "Translate") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = targetLanguage,
                            onValueChange = { targetLanguage = it },
                            label = { Text("Target language") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                GlassCard {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Topic, source text, or brief") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("writing_prompt_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            viewModel.generateWriting(
                                toolTitle = selectedTool,
                                tone = selectedTone,
                                targetLanguage = targetLanguage,
                                prompt = prompt,
                                modelId = selectedModelId,
                                currentCredits = credits,
                                accountSuspended = accountSuspended
                            )
                        },
                        enabled = state !is UiState.Loading,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_writing_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Text("Generate $selectedTool (2⚡)")
                    }
                }
            }

            item {
                val outputText = when (val s = state) {
                    is UiState.Streaming -> s.partialText
                    is UiState.Success -> s.data
                    else -> null
                }
                if (state is UiState.Loading) {
                    ShimmerLoadingCard("Drafting $selectedTool...")
                } else if (state is UiState.Error) {
                    StatusBanner("Writing Error", (state as UiState.Error).message, isError = true)
                }
                if (!outputText.isNullOrBlank()) {
                    GlassCard {
                        Text(outputText, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(outputText))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, null, tint = LuminousCyan)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", color = TextPrimary)
                            }
                            Button(
                                onClick = {
                                    onSaveProject(
                                        "$selectedTool: ${prompt.take(36)}",
                                        ProjectCategory.WRITING,
                                        prompt,
                                        outputText,
                                        selectedModelId,
                                        2L
                                    )
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                            ) {
                                Icon(Icons.Filled.BookmarkAdd, null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CodingStudioScreen(
    viewModel: CreativeStudiosViewModel,
    credits: Long,
    planName: String,
    accountSuspended: Boolean,
    onSaveProject: (String, ProjectCategory, String, String, String, Long) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val state by viewModel.codingState.collectAsStateWithLifecycle()

    val modes = listOf("Generate", "Explain", "Fix Bugs", "Code Review", "Tutorial")
    val languages = listOf("Kotlin", "TypeScript", "Python", "SQL", "Rust", "Go", "Swift", "HTML/CSS")
    var selectedMode by remember { mutableStateOf(modes.first()) }
    var selectedLang by remember { mutableStateOf(languages.first()) }
    var selectedModelId by remember { mutableStateOf(AiModelCatalog.DEFAULT_TEXT_MODEL.id) }
    var prompt by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar("AI Coding Studio", "Multi-Language Engineering Assistant", credits, planName, onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassCard {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        modes.forEach { m ->
                            FilterChip(selected = selectedMode == m, onClick = { selectedMode = m }, label = { Text(m) })
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        languages.forEach { lang ->
                            FilterChip(selected = selectedLang == lang, onClick = { selectedLang = lang }, label = { Text(lang) })
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiModelCatalog.textModels.forEach { m ->
                            FilterChip(
                                selected = selectedModelId == m.id,
                                onClick = { selectedModelId = m.id },
                                label = { Text("${m.label} (${m.creditCost}⚡)") }
                            )
                        }
                    }
                }
            }

            item {
                GlassCard {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Describe feature or paste source code") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth().testTag("coding_prompt_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            viewModel.generateCode(
                                mode = selectedMode,
                                language = selectedLang,
                                prompt = prompt,
                                modelId = selectedModelId,
                                currentCredits = credits,
                                accountSuspended = accountSuspended
                            )
                        },
                        enabled = state !is UiState.Loading,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_code_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Text("Run $selectedMode ($selectedLang)")
                    }
                }
            }

            item {
                val codeText = when (val s = state) {
                    is UiState.Streaming -> s.partialText
                    is UiState.Success -> s.data
                    else -> null
                }
                if (state is UiState.Loading) {
                    ShimmerLoadingCard("Analyzing & synthesizing $selectedLang...")
                } else if (state is UiState.Error) {
                    StatusBanner("Coding Studio Error", (state as UiState.Error).message, isError = true)
                }
                if (!codeText.isNullOrBlank()) {
                    GlassCard {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ObsidianBg)
                                .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = codeText,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFont),
                                color = LuminousCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(codeText))
                                    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, null, tint = LuminousCyan)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", color = TextPrimary)
                            }
                            OutlinedButton(
                                onClick = {
                                    ExportUtils.exportTextFileAndShare(
                                        context,
                                        "cerevix_${selectedLang.lowercase()}.txt",
                                        "text/plain",
                                        codeText
                                    )
                                },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Filled.Share, null, tint = LuminousCyan)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export", color = TextPrimary)
                            }
                            Button(
                                onClick = {
                                    onSaveProject(
                                        "$selectedLang $selectedMode",
                                        ProjectCategory.CODE,
                                        prompt,
                                        codeText,
                                        selectedModelId,
                                        2L
                                    )
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentStudioScreen(
    viewModel: CreativeStudiosViewModel,
    credits: Long,
    planName: String,
    accountSuspended: Boolean,
    onSaveProject: (String, ProjectCategory, String, String, String, Long) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val state by viewModel.documentState.collectAsStateWithLifecycle()

    val docTypes = listOf("Executive Report", "Presentation Deck", "Resume", "Project Brief")
    var selectedType by remember { mutableStateOf(docTypes.first()) }
    var topic by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf("Product & Engineering Leaders") }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar("Document & Deck Studio", "Structured JSON + Native PDF Export", credits, planName, onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassCard {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        docTypes.forEach { t ->
                            FilterChip(selected = selectedType == t, onClick = { selectedType = t }, label = { Text(t) })
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text("Document or deck topic") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("doc_topic_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = audience,
                        onValueChange = { audience = it },
                        label = { Text("Target audience") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            viewModel.generateStructuredDocument(
                                docType = selectedType,
                                topic = topic,
                                audience = audience,
                                currentCredits = credits,
                                accountSuspended = accountSuspended
                            )
                        },
                        enabled = state !is UiState.Loading,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_doc_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Text("Build $selectedType (3⚡)")
                    }
                }
            }

            item {
                when (val s = state) {
                    is UiState.Loading -> ShimmerLoadingCard("Structuring $selectedType...")
                    is UiState.Error -> StatusBanner("Document Error", s.message, isError = true)
                    is UiState.Success -> {
                        val doc = s.data
                        GlassCard(borderColor = LuminousCyan.copy(alpha = 0.45f)) {
                            Text(doc.title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                            Text(doc.subtitle, style = MaterialTheme.typography.bodyMedium, color = LuminousCyan)
                            Spacer(modifier = Modifier.height(8.dp))
                            StatusBanner("Key Takeaway", doc.summaryCallout, isOperational = true)
                            Spacer(modifier = Modifier.height(12.dp))
                            doc.sections.forEachIndexed { idx, sec ->
                                Text(
                                    text = "${idx + 1}. ${sec.heading}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                sec.bullets.forEach { b ->
                                    Text("• $b", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val res = ExportUtils.exportDocumentToPdfAndShare(
                                            context = context,
                                            title = doc.title,
                                            subtitle = doc.subtitle,
                                            sections = doc.sections.map { it.heading to it.bullets }
                                        )
                                        if (res.isFailure) {
                                            Toast.makeText(context, "PDF export failed", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export PDF")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val plain = buildString {
                                            appendLine(doc.title)
                                            appendLine(doc.subtitle)
                                            appendLine()
                                            doc.sections.forEach {
                                                appendLine(it.heading)
                                                it.bullets.forEach { b -> appendLine(" - $b") }
                                                appendLine()
                                            }
                                        }
                                        onSaveProject(
                                            doc.title,
                                            ProjectCategory.DOCUMENT,
                                            topic,
                                            plain,
                                            AiModelCatalog.DEFAULT_TEXT_MODEL.id,
                                            3L
                                        )
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("Save", color = LuminousCyan)
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

@Composable
fun VideoStudioScreen(
    viewModel: CreativeStudiosViewModel,
    credits: Long,
    planName: String,
    accountSuspended: Boolean,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val storyboardState by viewModel.storyboardState.collectAsStateWithLifecycle()

    var concept by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("30s Commercial") }
    var videoInfo by remember { mutableStateOf<String?>(null) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "?"
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "?"
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
                videoInfo = "Resolution: ${width}x${height} • Duration: ${durMs / 1000.0}s • Bitrate: ${bitrate / 1000} kbps"
            } catch (e: Exception) {
                videoInfo = "Unable to inspect video: ${e.localizedMessage}"
            } finally {
                retriever.release()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar("AI Video Studio", "Storyboard AI & Video Inspector", credits, planName, onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StatusBanner(
                    title = "Cloud Video Render & FFmpeg Status",
                    message = "AI Storyboard & Shot Scripting runs live on Gemini. Cloud Text-to-Video rendering & server FFmpeg transcoding require external backend credentials (not configured on client).",
                    isOperational = false
                )
            }

            item {
                GlassCard {
                    Text("Local Video Inspector", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.VideoLibrary, null, tint = LuminousCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Inspect Device Video Metadata", color = TextPrimary)
                    }
                    if (!videoInfo.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = videoInfo ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFont),
                            color = LuminousCyan
                        )
                    }
                }
            }

            item {
                GlassCard {
                    Text("AI Video Storyboard & Shot Generator", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("15s Reel", "30s Commercial", "60s Explainer").forEach { d ->
                            FilterChip(selected = duration == d, onClick = { duration = d }, label = { Text(d) })
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = concept,
                        onValueChange = { concept = it },
                        label = { Text("Video concept or product story") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.generateVideoStoryboard(concept, duration, credits, accountSuspended) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Text("Generate Shot-by-Shot Storyboard (3⚡)")
                    }
                }
            }

            item {
                when (val s = storyboardState) {
                    is UiState.Loading -> ShimmerLoadingCard("Directing shots & camera cues...")
                    is UiState.Error -> StatusBanner("Storyboard Error", s.message, isError = true)
                    is UiState.Success -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            s.data.forEach { shot ->
                                GlassCard {
                                    Text(
                                        text = "Shot ${shot.shotNumber} • ${shot.durationSeconds}s • ${shot.cameraMovement}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = LuminousCyan
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(shot.visualPrompt, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("VO: \"${shot.voiceoverLine}\"", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
