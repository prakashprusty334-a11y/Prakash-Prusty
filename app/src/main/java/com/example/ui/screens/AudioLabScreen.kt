package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.ui.components.GlassCard
import com.example.ui.components.ShimmerLoadingCard
import com.example.ui.components.StatusBanner
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.CreativeStudiosViewModel
import java.io.File
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioLabScreen(
    viewModel: CreativeStudiosViewModel,
    credits: Long,
    planName: String,
    accountSuspended: Boolean,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val sfxState by viewModel.sfxState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = TTS Voiceover, 1 = STT Dictation, 2 = SFX Designer
    var ttsText by remember { mutableStateOf("Welcome to Cerevix AI. One platform, infinite intelligence.") }
    var speechRate by remember { mutableFloatStateOf(1.0f) }
    var speechPitch by remember { mutableFloatStateOf(1.0f) }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsError by remember { mutableStateOf<String?>(null) }

    val tts = remember {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langRes = instance?.setLanguage(Locale.US)
                if (langRes == TextToSpeech.LANG_MISSING_DATA || langRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                    instance?.language = Locale.getDefault()
                }
                ttsError = null
                ttsReady = true
            } else {
                ttsReady = false
                ttsError = "No Text-to-Speech engine is available on this device/emulator. Enable Google Speech Services in Android Settings."
            }
        }
        instance
    }

    var transcriptText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var sttError by remember { mutableStateOf<String?>(null) }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(Unit) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true; sttError = null }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                isListening = false
                sttError = "Speech recognition error code: $error"
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    transcriptText = matches.first()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partial.isNullOrEmpty()) {
                    transcriptText = partial.first()
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            tts.stop()
            tts.shutdown()
            speechRecognizer?.destroy()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && speechRecognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer.startListening(intent)
        } else {
            sttError = "Microphone permission is required for Speech-to-Text."
        }
    }

    var sfxPrompt by remember { mutableStateOf("") }
    val sfxPresets = listOf(
        "Cinematic explosion",
        "Thunderstorm",
        "Rain ambience",
        "Car engine",
        "Motorcycle engine",
        "Futuristic interface sound",
        "Game notification",
        "Whoosh transition",
        "Dramatic impact",
        "Nature ambience"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "AI Audio Lab",
            subtitle = "Voiceover, Speech-to-Text & SFX Lab",
            credits = credits,
            planName = planName,
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("Text-to-Speech") }
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("Speech-to-Text") }
                    )
                    FilterChip(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        label = { Text("SFX Designer") }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    item {
                        GlassCard {
                            Text("Voiceover & WAV Synthesis", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Uses Android's native TextToSpeech synthesizer for real playback and exportable WAV files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            if (!ttsError.isNullOrBlank()) {
                                StatusBanner("TTS Engine Notice", ttsError ?: "", isError = true)
                                Spacer(modifier = Modifier.height(10.dp))
                            } else {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            OutlinedTextField(
                                value = ttsText,
                                onValueChange = { ttsText = it },
                                label = { Text("Voiceover script") },
                                minLines = 4,
                                modifier = Modifier.fillMaxWidth().testTag("tts_input")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Speed (${"%.2f".format(speechRate)}x)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Slider(
                                value = speechRate,
                                onValueChange = { speechRate = it },
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(thumbColor = LuminousCyan, activeTrackColor = ElectricBlue)
                            )
                            Text("Pitch (${"%.2f".format(speechPitch)}x)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Slider(
                                value = speechPitch,
                                onValueChange = { speechPitch = it },
                                valueRange = 0.5f..1.8f,
                                colors = SliderDefaults.colors(thumbColor = LuminousCyan, activeTrackColor = ElectricBlue)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (ttsReady) {
                                            tts.setSpeechRate(speechRate)
                                            tts.setPitch(speechPitch)
                                            tts.speak(ttsText, TextToSpeech.QUEUE_FLUSH, null, "cerevix_tts")
                                        }
                                    },
                                    enabled = ttsReady && ttsText.isNotBlank(),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("tts_speak_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Speak")
                                }
                                OutlinedButton(
                                    onClick = { tts.stop() },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Filled.Stop, null, tint = RoseError)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Stop", color = TextPrimary)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    if (ttsReady && ttsText.isNotBlank()) {
                                        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                                        val wav = File(dir, "cerevix_voiceover_${System.currentTimeMillis()}.wav")
                                        tts.setSpeechRate(speechRate)
                                        tts.setPitch(speechPitch)
                                        val res = tts.synthesizeToFile(ttsText, null, wav, "cerevix_wav")
                                        if (res == TextToSpeech.SUCCESS) {
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                wav
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "audio/wav"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Export Voiceover WAV"))
                                        } else {
                                            Toast.makeText(context, "WAV synthesis unavailable on device", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = ttsReady && ttsText.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Filled.Download, null, tint = LuminousCyan)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export Voiceover (.wav)", color = LuminousCyan)
                            }
                        }
                    }
                }

                1 -> {
                    item {
                        GlassCard {
                            Text("Speech-to-Text Dictation", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Uses Android SpeechRecognizer with runtime microphone permission.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            if (!sttError.isNullOrBlank()) {
                                StatusBanner("Microphone / STT Notice", sttError ?: "", isError = true)
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        if (speechRecognizer == null) {
                                            sttError = "Speech recognition service is not installed on this device/emulator."
                                            return@Button
                                        }
                                        val hasPerm = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                            }
                                            speechRecognizer.startListening(intent)
                                        } else {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    enabled = !isListening,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isListening) EmeraldSuccess else ElectricBlue)
                                ) {
                                    Icon(Icons.Filled.Mic, null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isListening) "Listening..." else "Start Dictation")
                                }
                                OutlinedButton(
                                    onClick = {
                                        speechRecognizer?.stopListening()
                                        isListening = false
                                    },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Stop", color = TextPrimary)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = transcriptText,
                                onValueChange = { transcriptText = it },
                                label = { Text("Transcribed text") },
                                minLines = 5,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                else -> {
                    item {
                        StatusBanner(
                            title = "Cloud SFX & Music Waveform Status",
                            message = "AI Sound Design Blueprints are active via Gemini. Raw cloud WAV SFX/Music synthesis requires server-side ElevenLabs/Lyria keys (not configured; no fake audio is played).",
                            isOperational = false
                        )
                    }
                    item {
                        GlassCard {
                            Text("SFX Blueprint Designer", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                sfxPresets.forEach { preset ->
                                    OutlinedButton(
                                        onClick = { sfxPrompt = preset },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, BorderGlass)
                                    ) {
                                        Text(preset, style = MaterialTheme.typography.labelMedium, color = LuminousCyan)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = sfxPrompt,
                                onValueChange = { sfxPrompt = it },
                                label = { Text("Describe sound effect") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.generateSfxBrief(sfxPrompt, credits, accountSuspended) },
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_sfx_brief_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                            ) {
                                Text("Generate Acoustic SFX Blueprint (2⚡)")
                            }
                        }
                    }
                    item {
                        when (val s = sfxState) {
                            is UiState.Loading -> ShimmerLoadingCard("Designing acoustic layers...")
                            is UiState.Error -> StatusBanner("SFX Error", s.message, isError = true)
                            is UiState.Success -> {
                                val cue = s.data
                                GlassCard(borderColor = LuminousCyan.copy(alpha = 0.45f)) {
                                    Text(cue.cueTitle, style = MaterialTheme.typography.titleLarge, color = LuminousCyan)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Duration: ${cue.durationSeconds}s", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Acoustic Texture", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                    Text(cue.acousticTexture, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Frequency Profile", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                    Text(cue.frequencyProfile, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Production Chain", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                    Text(cue.productionNotes, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}
