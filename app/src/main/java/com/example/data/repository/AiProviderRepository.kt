package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AiModelCatalog
import com.example.data.model.ChatMessage
import com.example.data.model.ProviderCapabilityStatus
import com.example.data.model.SenderRole
import com.example.data.model.SfxDesignCue
import com.example.data.model.StructuredDocSection
import com.example.data.model.StructuredDocumentResult
import com.example.data.model.VideoStoryboardShot
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "AiProviderRepo"

class AiProviderRepository {

    private val cerevixSystemContent by lazy {
        content("system") {
            text(CEREVIX_SYSTEM_INSTRUCTION)
        }
    }

    private val defaultChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelCatalog.DEFAULT_TEXT_MODEL.id,
            systemInstruction = cerevixSystemContent
        )
    }

    private fun getTextModel(modelId: String) = when (modelId) {
        AiModelCatalog.DEFAULT_TEXT_MODEL.id -> defaultChatModel
        else -> Firebase.ai.generativeModel(
            modelName = modelId,
            systemInstruction = cerevixSystemContent
        )
    }

    fun getProviderStatuses(): List<ProviderCapabilityStatus> = listOf(
        ProviderCapabilityStatus(
            name = "Text, Chat, Code, Writing & Vision",
            provider = "Firebase AI Logic (Gemini 3.8 Flash / 3.5 Lite / 4 Argon)",
            operational = true,
            detail = "Connected via Firebase App Check with automatic Flash-Lite fallback"
        ),
        ProviderCapabilityStatus(
            name = "AI Image Generation & Editing",
            provider = "Firebase AI Logic (Nano Banana 2.1 / Flash-Lite Image / Pro Image)",
            operational = true,
            detail = "Requires Firebase Blaze billing plan on project gen-lang-client-0458088105"
        ),
        ProviderCapabilityStatus(
            name = "On-Device Photo Editor & PDF Export",
            provider = "Android Graphics Engine + PdfDocument",
            operational = true,
            detail = "100% local processing with MediaStore & Share export"
        ),
        ProviderCapabilityStatus(
            name = "On-Device Voiceover (TTS) & Dictation (STT)",
            provider = "Android TextToSpeech & SpeechRecognizer",
            operational = true,
            detail = "Real device speech synthesis, WAV export & mic dictation"
        ),
        ProviderCapabilityStatus(
            name = "CEREVIX API Gateway & Razorpay Billing",
            provider = "CEREVIX Backend Server (https://api.cerevix.ai)",
            operational = true,
            detail = "Developer keys, Razorpay Checkout & webhooks wired (configure backend URL/secrets for live traffic)"
        ),
        ProviderCapabilityStatus(
            name = "Cloud Video Render & FFmpeg Pipeline",
            provider = "External Cloud Run / Veo Video Backend",
            operational = false,
            detail = "AI Storyboard active; raw video render requires server-side video provider credentials"
        ),
        ProviderCapabilityStatus(
            name = "Cloud SFX & Music Audio Synthesis",
            provider = "External Audio Generation API",
            operational = false,
            detail = "AI SFX acoustic brief active; raw WAV SFX API requires server-side audio provider key"
        )
    )

    fun streamChatMessage(
        modelId: String,
        history: List<ChatMessage>,
        userPrompt: String,
        attachedImage: Bitmap? = null,
        attachedDocumentBytes: ByteArray? = null,
        attachedDocumentMime: String? = null,
        attachedDocumentName: String? = null
    ): Flow<String> = flow {
        if (attachedDocumentBytes != null && attachedDocumentBytes.size > MAX_INLINE_MEDIA_BYTES) {
            throw IllegalArgumentException("Attached file exceeds the 18 MB inline AI payload limit.")
        }
        val preparedImage = attachedImage?.let { prepareBitmapForAi(it, maxDimension = 1536) }
        val cleanHistory = sanitizeChatHistory(history.takeLast(20))
        val sdkHistory: List<Content> = cleanHistory.map { msg ->
            val role = if (msg.resolvedSenderRole() == SenderRole.user) "user" else "model"
            content(role) { text(msg.content) }
        }

        val textDocumentSnippet: String? = if (
            attachedDocumentBytes != null &&
            !attachedDocumentMime.isNullOrBlank() &&
            isTextLikeMime(attachedDocumentMime)
        ) {
            attachedDocumentBytes.toString(Charsets.UTF_8).take(120_000)
        } else {
            null
        }

        if (attachedDocumentBytes != null &&
            !attachedDocumentMime.isNullOrBlank() &&
            textDocumentSnippet == null &&
            !isBinaryInlineSupportedMime(attachedDocumentMime)
        ) {
            throw IllegalArgumentException(
                "Unsupported file format ($attachedDocumentMime). Please attach a PDF, image, or plain text/code file."
            )
        }

        val messageContent = content("user") {
            if (preparedImage != null) {
                image(preparedImage)
            }
            if (attachedDocumentBytes != null &&
                !attachedDocumentMime.isNullOrBlank() &&
                textDocumentSnippet == null
            ) {
                inlineData(attachedDocumentBytes, attachedDocumentMime)
            }
            val promptWithContext = buildString {
                if (!attachedDocumentName.isNullOrBlank()) {
                    append("[Attached file: $attachedDocumentName]\n")
                }
                if (!textDocumentSnippet.isNullOrBlank()) {
                    append("```text\n")
                    append(textDocumentSnippet)
                    append("\n```\n\n")
                }
                append(userPrompt)
            }
            text(promptWithContext)
        }

        val candidates = textModelFallbackChain(modelId)
        var lastError: Throwable? = null

        for (candidateModelId in candidates) {
            val accumulated = StringBuilder()
            try {
                val model = getTextModel(candidateModelId)
                val chat = model.startChat(history = sdkHistory)
                withTimeout(60_000L) {
                    chat.sendMessageStream(messageContent).collect { chunk ->
                        val delta = chunk.text.orEmpty()
                        if (delta.isNotEmpty()) {
                            accumulated.append(delta)
                            emit(accumulated.toString())
                        }
                    }
                }
                if (accumulated.isNotBlank()) {
                    return@flow
                }
                // Streaming returned 0 tokens; attempt unary generation on the same model
                val unary = withTimeout(45_000L) {
                    model.startChat(history = sdkHistory).sendMessage(messageContent)
                }
                val unaryText = unary.text.orEmpty()
                if (unaryText.isNotBlank()) {
                    emit(unaryText)
                    return@flow
                }
                lastError = IllegalStateException("Model $candidateModelId returned an empty response.")
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    Log.w(TAG, "Chat stream timed out on $candidateModelId", e)
                    lastError = e
                    if (accumulated.isNotBlank()) {
                        // Partial content already streamed; finish cleanly rather than failing whole turn
                        return@flow
                    }
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.w(TAG, "Chat failed on $candidateModelId: ${e.message}", e)
                if (accumulated.isNotBlank()) {
                    return@flow
                }
                lastError = e
            }
        }

        throw (lastError ?: IllegalStateException("Unable to generate AI chat response."))
    }

    fun streamStudioText(
        modelId: String,
        systemInstruction: String,
        prompt: String
    ): Flow<String> = flow {
        val fullPrompt = "$systemInstruction\n\n---\n\n$prompt"
        val candidates = textModelFallbackChain(modelId)
        var lastError: Throwable? = null

        for (candidateModelId in candidates) {
            val accumulated = StringBuilder()
            try {
                val model = getTextModel(candidateModelId)
                withTimeout(60_000L) {
                    model.generateContentStream(fullPrompt).collect { chunk ->
                        val delta = chunk.text.orEmpty()
                        if (delta.isNotEmpty()) {
                            accumulated.append(delta)
                            emit(accumulated.toString())
                        }
                    }
                }
                if (accumulated.isNotBlank()) {
                    return@flow
                }
                val unary = withTimeout(45_000L) {
                    model.generateContent(fullPrompt)
                }
                val unaryText = unary.text.orEmpty()
                if (unaryText.isNotBlank()) {
                    emit(unaryText)
                    return@flow
                }
                lastError = IllegalStateException("Model $candidateModelId returned an empty response.")
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    Log.w(TAG, "Studio stream timed out on $candidateModelId", e)
                    lastError = e
                    if (accumulated.isNotBlank()) return@flow
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.w(TAG, "Studio text failed on $candidateModelId: ${e.message}", e)
                if (accumulated.isNotBlank()) return@flow
                lastError = e
            }
        }

        throw (lastError ?: IllegalStateException("The AI model returned an empty response."))
    }

    suspend fun generateImage(
        modelId: String,
        prompt: String,
        aspectRatio: AspectRatio? = null,
        sourceImage: Bitmap? = null
    ): Bitmap {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            if (aspectRatio != null && aspectRatio != AspectRatio.SQUARE_1x1) {
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(aspectRatio)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
        }
        val scaledSource = sourceImage?.let { prepareBitmapForAi(it, maxDimension = 1024) }
        val candidates = imageModelFallbackChain(modelId)
        var lastError: Throwable? = null

        for (candidateModelId in candidates) {
            try {
                val model = Firebase.ai.generativeModel(
                    modelName = candidateModelId,
                    generationConfig = config
                )
                return withTimeout(90_000L) {
                    val response = if (scaledSource != null) {
                        model.generateContent(
                            content {
                                image(scaledSource)
                                text(prompt)
                            }
                        )
                    } else {
                        model.generateContent(prompt)
                    }
                    val firstCandidate = response.candidates.firstOrNull()
                    val imagePart = firstCandidate
                        ?.content?.parts?.filterIsInstance<ImagePart>()
                        ?.firstOrNull()
                    if (imagePart?.image != null) {
                        return@withTimeout imagePart.image
                    }
                    val textReply = response.text?.take(200)
                    val finishReason = firstCandidate?.finishReason?.toString()
                    throw IllegalStateException(
                        buildString {
                            append("No image returned by $candidateModelId.")
                            if (!finishReason.isNullOrBlank()) append(" Reason: $finishReason.")
                            if (!textReply.isNullOrBlank()) append(" Note: $textReply")
                        }
                    )
                }
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    Log.w(TAG, "Image generation timed out on $candidateModelId", e)
                    lastError = e
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.w(TAG, "Image generation failed on $candidateModelId: ${e.message}", e)
                lastError = e
            }
        }

        throw (lastError ?: IllegalStateException("Image generation failed."))
    }

    suspend fun generateStructuredDocument(
        docType: String,
        topic: String,
        audience: String
    ): StructuredDocumentResult {
        val docSchema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Document or deck title"),
                "subtitle" to Schema.string(description = "Subtitle or executive framing"),
                "summaryCallout" to Schema.string(description = "Key takeaway or executive summary"),
                "sections" to Schema.array(
                    description = "Sections or presentation slides",
                    items = Schema.obj(
                        properties = mapOf(
                            "heading" to Schema.string(description = "Section or slide heading"),
                            "bullets" to Schema.array(
                                items = Schema.string(),
                                description = "3 to 5 concise bullet points"
                            )
                        )
                    )
                )
            )
        )
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = docSchema
        }
        val jsonStr = generateJsonWithFallback(
            config = config,
            prompt = "Create a structured $docType on '$topic' tailored for '$audience'. Provide 4 to 6 high-value sections.",
            timeoutMs = 60_000L
        )
        val json = JSONObject(jsonStr)
        val sectionsArr = json.optJSONArray("sections") ?: JSONArray()
        val sections = (0 until sectionsArr.length()).mapNotNull { i ->
            val obj = sectionsArr.optJSONObject(i) ?: return@mapNotNull null
            val bulletsArr = obj.optJSONArray("bullets") ?: JSONArray()
            val bullets = (0 until bulletsArr.length()).mapNotNull { b ->
                bulletsArr.optString(b).takeIf { it.isNotBlank() }
            }
            StructuredDocSection(
                heading = obj.optString("heading", "Section ${i + 1}"),
                bullets = bullets.ifEmpty { listOf("Key insights for $topic") }
            )
        }
        return StructuredDocumentResult(
            title = json.optString("title", "$docType: $topic").ifBlank { "$docType: $topic" },
            subtitle = json.optString("subtitle", "Tailored for $audience"),
            sections = sections.ifEmpty {
                listOf(StructuredDocSection("Executive Overview", listOf("Overview of $topic for $audience.")))
            },
            summaryCallout = json.optString("summaryCallout", "Prepared by CEREVIX AI Document Studio.")
        )
    }

    suspend fun generateSfxBrief(prompt: String): SfxDesignCue {
        val sfxSchema = Schema.obj(
            properties = mapOf(
                "cueTitle" to Schema.string(description = "Title of the sound effect cue"),
                "acousticTexture" to Schema.string(description = "Transient, body, and tail acoustic layers"),
                "frequencyProfile" to Schema.string(description = "Sub, mid, and high frequency shaping"),
                "durationSeconds" to Schema.double(description = "Suggested duration in seconds"),
                "productionNotes" to Schema.string(description = "FOLEY / DSP / synthesis chain instructions")
            )
        )
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = sfxSchema
        }
        val jsonStr = generateJsonWithFallback(
            config = config,
            prompt = "Design a detailed sound effect production blueprint for: $prompt",
            timeoutMs = 45_000L
        )
        val json = JSONObject(jsonStr)
        return SfxDesignCue(
            cueTitle = json.optString("cueTitle", prompt.take(48)).ifBlank { prompt.take(48) },
            acousticTexture = json.optString("acousticTexture", "Layered transient impact with spatial tail."),
            frequencyProfile = json.optString("frequencyProfile", "Balanced sub, mid presence, and air sheen."),
            durationSeconds = json.optDouble("durationSeconds", 3.0),
            productionNotes = json.optString("productionNotes", "Apply subtle compression and stereo reverb.")
        )
    }

    suspend fun generateVideoStoryboard(concept: String, durationLabel: String): List<VideoStoryboardShot> {
        val shotSchema = Schema.obj(
            properties = mapOf(
                "shots" to Schema.array(
                    description = "4 to 6 cinematic shots",
                    items = Schema.obj(
                        properties = mapOf(
                            "shotNumber" to Schema.integer(description = "Sequential shot number"),
                            "durationSeconds" to Schema.integer(description = "Shot length in seconds"),
                            "visualPrompt" to Schema.string(description = "Detailed visual generation prompt"),
                            "cameraMovement" to Schema.string(description = "Camera angle and motion"),
                            "voiceoverLine" to Schema.string(description = "Spoken voiceover or caption")
                        )
                    )
                )
            )
        )
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = shotSchema
        }
        val jsonStr = generateJsonWithFallback(
            config = config,
            prompt = "Create a cinematic video storyboard ($durationLabel) for: $concept",
            timeoutMs = 60_000L
        )
        val arr = JSONObject(jsonStr).optJSONArray("shots") ?: JSONArray()
        val shots = (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            VideoStoryboardShot(
                shotNumber = obj.optInt("shotNumber", i + 1),
                durationSeconds = obj.optInt("durationSeconds", 5),
                visualPrompt = obj.optString("visualPrompt", concept),
                cameraMovement = obj.optString("cameraMovement", "Smooth dolly push-in"),
                voiceoverLine = obj.optString("voiceoverLine", "")
            )
        }
        if (shots.isEmpty()) {
            throw IllegalStateException("Model returned an empty storyboard.")
        }
        return shots
    }

    private suspend fun generateJsonWithFallback(
        config: com.google.firebase.ai.type.GenerationConfig,
        prompt: String,
        timeoutMs: Long
    ): String {
        val candidates = listOf(
            AiModelCatalog.DEFAULT_TEXT_MODEL.id,
            AiModelCatalog.FAST_TEXT_MODEL.id
        )
        var lastError: Throwable? = null
        for (modelId in candidates) {
            try {
                val model = Firebase.ai.generativeModel(
                    modelName = modelId,
                    generationConfig = config,
                    systemInstruction = cerevixSystemContent
                )
                return withTimeout(timeoutMs) {
                    val response = model.generateContent(prompt)
                    response.text?.takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("Empty structured response from $modelId")
                }
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    Log.w(TAG, "Structured JSON timed out on $modelId", e)
                    lastError = e
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.w(TAG, "Structured JSON failed on $modelId: ${e.message}", e)
                lastError = e
            }
        }
        throw (lastError ?: IllegalStateException("Structured generation failed."))
    }

    fun isCancellation(t: Throwable): Boolean = t is CancellationException

    companion object {
        private const val MAX_INLINE_MEDIA_BYTES = 18 * 1024 * 1024

        const val CEREVIX_SYSTEM_INSTRUCTION = """You are CEREVIX AI, the intelligent assistant of the CEREVIX AI platform ("One Platform. Infinite Intelligence.").
Identity rules:
- When anyone asks who you are, what model or AI you are, or who created you, always introduce yourself as CEREVIX AI from the CEREVIX AI platform.
- Never say "I am a large language model, trained by Google" and never refer to yourself as Gemini or a Google model.
- Stay truthful about capabilities: never claim to have generated raw audio files or video renders unless those tools actually produced them.

Language rules:
- Your primary default language is English.
- Always detect the language, script, or conversational style used by the user (such as English, Hindi, Hinglish, Spanish, French, Tamil, Bengali, etc.) and reply naturally in that same language unless the user asks for another language."""

        fun isTextLikeMime(mime: String): Boolean {
            val m = mime.lowercase()
            return m.startsWith("text/") ||
                m == "application/json" ||
                m == "application/xml" ||
                m == "application/javascript" ||
                m == "application/x-yaml"
        }

        fun isBinaryInlineSupportedMime(mime: String): Boolean {
            val m = mime.lowercase()
            return m == "application/pdf" || m.startsWith("image/")
        }

        fun textModelFallbackChain(requestedModelId: String): List<String> = when (requestedModelId) {
            AiModelCatalog.PRO_REASONING_MODEL.id -> listOf(
                AiModelCatalog.PRO_REASONING_MODEL.id,
                AiModelCatalog.DEFAULT_TEXT_MODEL.id,
                AiModelCatalog.FAST_TEXT_MODEL.id
            )
            AiModelCatalog.FAST_TEXT_MODEL.id -> listOf(
                AiModelCatalog.FAST_TEXT_MODEL.id,
                AiModelCatalog.DEFAULT_TEXT_MODEL.id
            )
            else -> listOf(
                AiModelCatalog.DEFAULT_TEXT_MODEL.id,
                AiModelCatalog.FAST_TEXT_MODEL.id
            )
        }

        fun imageModelFallbackChain(requestedModelId: String): List<String> {
            val all = AiModelCatalog.imageModels.map { it.id }
            return if (requestedModelId in all) {
                listOf(requestedModelId) + all.filter { it != requestedModelId }
            } else {
                all
            }
        }

        /**
         * Ensures chat history sent to Gemini `startChat()` strictly alternates `user` -> `model`,
         * starts with `user`, ends with `model`, and drops empty or orphaned user turns left behind
         * by any previously failed API call.
         */
        fun sanitizeChatHistory(messages: List<ChatMessage>): List<ChatMessage> {
            val nonBlank = messages.filter { it.content.isNotBlank() }
            if (nonBlank.isEmpty()) return emptyList()

            val merged = mutableListOf<ChatMessage>()
            for (msg in nonBlank) {
                val role = msg.resolvedSenderRole()
                if (merged.isEmpty()) {
                    if (role == SenderRole.user) {
                        merged.add(msg)
                    }
                    // Skip leading model turn
                } else {
                    val prev = merged.last()
                    if (prev.resolvedSenderRole() == role) {
                        merged[merged.lastIndex] = prev.copy(
                            content = "${prev.content}\n\n${msg.content}"
                        )
                    } else {
                        merged.add(msg)
                    }
                }
            }

            // History passed to startChat() before a new sendMessage() must end on a model turn
            while (merged.isNotEmpty() && merged.last().resolvedSenderRole() == SenderRole.user) {
                merged.removeAt(merged.lastIndex)
            }
            return merged
        }

        fun prepareBitmapForAi(source: Bitmap, maxDimension: Int): Bitmap {
            val softwareBmp = if (source.config == Bitmap.Config.ARGB_8888) {
                source
            } else {
                source.copy(Bitmap.Config.ARGB_8888, false) ?: source
            }
            val scale = minOf(
                maxDimension.toFloat() / softwareBmp.width.coerceAtLeast(1),
                maxDimension.toFloat() / softwareBmp.height.coerceAtLeast(1),
                1f
            )
            return if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    softwareBmp,
                    (softwareBmp.width * scale).toInt().coerceAtLeast(1),
                    (softwareBmp.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                softwareBmp
            }
        }

        fun formatUserFacingError(error: Throwable, isImageStudio: Boolean = false): String {
            if (error is TimeoutCancellationException) {
                return "Request timed out while waiting for Gemini. Please try again."
            }
            val raw = error.localizedMessage.orEmpty()
            val lower = raw.lowercase()
            return when {
                error is QuotaExceededException ||
                    lower.contains("limit: 0") ||
                    lower.contains("quota") ||
                    lower.contains("free_tier") ||
                    lower.contains("billing") -> {
                    if (isImageStudio) {
                        "Image generation requires the Firebase Blaze (pay-as-you-go) billing plan on project gen-lang-client-0458088105 (free-tier image quota is 0). Text Chat, Writing, Coding, Document Studio, Audio Lab, and Photo Editor work without Blaze."
                    } else {
                        "AI quota or rate limit reached for this model. Try again in a few seconds or switch to Gemini 3.5 Flash-Lite."
                    }
                }
                lower.contains("app check") || lower.contains("403") || lower.contains("permission_denied") ->
                    "Firebase verification (App Check / permissions) rejected the request. Please ensure you are signed in and retry."
                lower.contains("unable to resolve host") || lower.contains("network") || lower.contains("unavailable") ->
                    "Network connection unavailable. Check your internet connection and retry."
                raw.isNotBlank() ->
                    raw.lineSequence().firstOrNull { it.isNotBlank() }?.take(220) ?: "AI request failed."
                else -> "AI request failed (${error::class.simpleName ?: "Unknown error"})."
            }
        }
    }
}
