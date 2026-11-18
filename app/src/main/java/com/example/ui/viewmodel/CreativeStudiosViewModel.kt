package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.UiState
import com.example.data.model.AiModelCatalog
import com.example.data.model.ProjectCategory
import com.example.data.model.ProviderCapabilityStatus
import com.example.data.model.SfxDesignCue
import com.example.data.model.StructuredDocumentResult
import com.example.data.model.VideoStoryboardShot
import com.example.data.repository.AiProviderRepository
import com.example.data.repository.CerevixFirestoreRepository
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "StudiosVM"

data class ImageHistoryItem(
    val prompt: String,
    val modelId: String,
    val bitmap: Bitmap
)

class CreativeStudiosViewModel(
    private val firestoreRepo: CerevixFirestoreRepository,
    private val aiRepo: AiProviderRepository,
    private val currentUserId: String
) : ViewModel() {

    val providerStatuses: List<ProviderCapabilityStatus> = aiRepo.getProviderStatuses()

    // Image Studio
    private val _imageState = MutableStateFlow<UiState<Bitmap>>(UiState.Idle)
    val imageState: StateFlow<UiState<Bitmap>> = _imageState.asStateFlow()

    private val _imageHistory = MutableStateFlow<List<ImageHistoryItem>>(emptyList())
    val imageHistory: StateFlow<List<ImageHistoryItem>> = _imageHistory.asStateFlow()

    // Writing Studio
    private val _writingState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val writingState: StateFlow<UiState<String>> = _writingState.asStateFlow()

    // Coding Studio
    private val _codingState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val codingState: StateFlow<UiState<String>> = _codingState.asStateFlow()

    // Document Studio
    private val _documentState = MutableStateFlow<UiState<StructuredDocumentResult>>(UiState.Idle)
    val documentState: StateFlow<UiState<StructuredDocumentResult>> = _documentState.asStateFlow()

    // Audio Lab SFX Brief
    private val _sfxState = MutableStateFlow<UiState<SfxDesignCue>>(UiState.Idle)
    val sfxState: StateFlow<UiState<SfxDesignCue>> = _sfxState.asStateFlow()

    // Video Storyboard
    private val _storyboardState = MutableStateFlow<UiState<List<VideoStoryboardShot>>>(UiState.Idle)
    val storyboardState: StateFlow<UiState<List<VideoStoryboardShot>>> = _storyboardState.asStateFlow()

    fun generateOrEditImage(
        prompt: String,
        modelId: String,
        aspectRatio: AspectRatio?,
        sourceBitmap: Bitmap?,
        currentCredits: Long,
        accountSuspended: Boolean
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            _imageState.value = UiState.Error("Please enter an image prompt.")
            return
        }
        val modelOpt = AiModelCatalog.imageModels.firstOrNull { it.id == modelId }
            ?: AiModelCatalog.DEFAULT_IMAGE_MODEL

        if (accountSuspended) {
            _imageState.value = UiState.Error("Account is suspended.")
            return
        }
        if (currentCredits < modelOpt.creditCost) {
            _imageState.value = UiState.Error(
                "Insufficient credits (${modelOpt.creditCost} required, $currentCredits available)."
            )
            return
        }

        viewModelScope.launch {
            _imageState.value = UiState.Loading
            try {
                val bitmap = aiRepo.generateImage(
                    modelId = modelOpt.id,
                    prompt = cleanPrompt,
                    aspectRatio = aspectRatio,
                    sourceImage = sourceBitmap
                )
                firestoreRepo.commitCreditDeduction(
                    uid = currentUserId,
                    cost = modelOpt.creditCost.toLong(),
                    toolId = "image_studio_${modelOpt.id}",
                    description = "${if (sourceBitmap != null) "Edited" else "Generated"} image (${modelOpt.label})"
                )
                firestoreRepo.saveProject(
                    uid = currentUserId,
                    title = cleanPrompt.take(64),
                    category = ProjectCategory.IMAGE,
                    prompt = cleanPrompt,
                    outputText = "Generated via ${modelOpt.label} (${bitmap.width}x${bitmap.height})",
                    modelId = modelOpt.id,
                    creditsCost = modelOpt.creditCost.toLong()
                )
                _imageHistory.value = listOf(ImageHistoryItem(cleanPrompt, modelOpt.id, bitmap)) +
                    _imageHistory.value.take(7)
                _imageState.value = UiState.Success(bitmap)
            } catch (e: TimeoutCancellationException) {
                _imageState.value = UiState.Error("Image generation timed out after 90 seconds. Please try again.")
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Image quota exceeded", e)
                _imageState.value = UiState.Error(AiProviderRepository.formatUserFacingError(e, isImageStudio = true))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _imageState.value = UiState.Error(AiProviderRepository.formatUserFacingError(e, isImageStudio = true))
            }
        }
    }

    fun selectHistoryImage(item: ImageHistoryItem) {
        _imageState.value = UiState.Success(item.bitmap)
    }

    fun generateWriting(
        toolTitle: String,
        tone: String,
        targetLanguage: String,
        prompt: String,
        modelId: String,
        currentCredits: Long,
        accountSuspended: Boolean
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            _writingState.value = UiState.Error("Please enter a topic or text.")
            return
        }
        val modelOpt = AiModelCatalog.textModels.firstOrNull { it.id == modelId }
            ?: AiModelCatalog.DEFAULT_TEXT_MODEL
        if (accountSuspended) {
            _writingState.value = UiState.Error("Account is suspended.")
            return
        }
        if (currentCredits < modelOpt.creditCost) {
            _writingState.value = UiState.Error("Insufficient credits (${modelOpt.creditCost} required).")
            return
        }

        viewModelScope.launch {
            _writingState.value = UiState.Loading
            try {
                val systemPrompt = buildString {
                    append("You are CEREVIX Writing Studio. Task: $toolTitle. ")
                    append("Tone: $tone. ")
                    if (targetLanguage.isNotBlank()) append("Output language: $targetLanguage. ")
                    append("Produce polished, publication-ready formatting without unnecessary fluff.")
                }
                var finalOutput = ""
                aiRepo.streamStudioText(modelOpt.id, systemPrompt, cleanPrompt).collect { partial ->
                    finalOutput = partial
                    _writingState.value = UiState.Streaming(partial)
                }
                firestoreRepo.commitCreditDeduction(
                    uid = currentUserId,
                    cost = modelOpt.creditCost.toLong(),
                    toolId = "writing_$toolTitle",
                    description = "Writing Studio: $toolTitle"
                )
                _writingState.value = UiState.Success(finalOutput)
            } catch (e: TimeoutCancellationException) {
                _writingState.value = UiState.Error("Writing generation timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Writing studio failed", e)
                _writingState.value = UiState.Error(AiProviderRepository.formatUserFacingError(e))
            }
        }
    }

    fun generateCode(
        mode: String,
        language: String,
        prompt: String,
        modelId: String,
        currentCredits: Long,
        accountSuspended: Boolean
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            _codingState.value = UiState.Error("Please enter a coding prompt or paste code.")
            return
        }
        val modelOpt = AiModelCatalog.textModels.firstOrNull { it.id == modelId }
            ?: AiModelCatalog.DEFAULT_TEXT_MODEL
        if (accountSuspended) {
            _codingState.value = UiState.Error("Account is suspended.")
            return
        }
        if (currentCredits < modelOpt.creditCost) {
            _codingState.value = UiState.Error("Insufficient credits (${modelOpt.creditCost} required).")
            return
        }

        viewModelScope.launch {
            _codingState.value = UiState.Loading
            try {
                val systemPrompt = "You are CEREVIX Coding Studio. Mode: $mode. Language: $language. " +
                    "Provide clean, production-grade code, concise technical reasoning, and edge-case safety."
                var finalCode = ""
                aiRepo.streamStudioText(modelOpt.id, systemPrompt, cleanPrompt).collect { partial ->
                    finalCode = partial
                    _codingState.value = UiState.Streaming(partial)
                }
                firestoreRepo.commitCreditDeduction(
                    uid = currentUserId,
                    cost = modelOpt.creditCost.toLong(),
                    toolId = "coding_$mode",
                    description = "Coding Studio: $mode ($language)"
                )
                _codingState.value = UiState.Success(finalCode)
            } catch (e: TimeoutCancellationException) {
                _codingState.value = UiState.Error("Coding generation timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Coding studio failed", e)
                _codingState.value = UiState.Error(AiProviderRepository.formatUserFacingError(e))
            }
        }
    }

    fun generateStructuredDocument(
        docType: String,
        topic: String,
        audience: String,
        currentCredits: Long,
        accountSuspended: Boolean
    ) {
        val cleanTopic = topic.trim()
        if (cleanTopic.isBlank()) {
            _documentState.value = UiState.Error("Please enter a topic or prompt.")
            return
        }
        val cost = 3L
        if (accountSuspended) {
            _documentState.value = UiState.Error("Account is suspended.")
            return
        }
        if (currentCredits < cost) {
            _documentState.value = UiState.Error("Insufficient credits ($cost required).")
            return
        }

        viewModelScope.launch {
            _documentState.value = UiState.Loading
            try {
                val result = aiRepo.generateStructuredDocument(
                    docType = docType,
                    topic = cleanTopic,
                    audience = audience.ifBlank { "Executive & Technical Stakeholders" }
                )
                firestoreRepo.commitCreditDeduction(
                    uid = currentUserId,
                    cost = cost,
                    toolId = "doc_studio_$docType",
                    description = "Document Studio: $docType"
                )
                _documentState.value = UiState.Success(result)
            } catch (e: TimeoutCancellationException) {
                _documentState.value = UiState.Error("Document generation timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Document studio failed", e)
                _documentState.value = UiState.Error(AiProviderRepository.formatUserFacingError(e))
            }
        }
    }

    fun generateSfxBrief(
        prompt: String,
        currentCredits: Long,
        accountSuspended: Boolean
    ) {
        val clean = prompt.trim()
        if (clean.isBlank()) {
            _sfxState.value = UiState.Error("Enter an SFX prompt (e.g. Cinematic explosion).")
            return
        }
        val cost = 2L
        if (accountSuspended) {
            _sfxState.value = UiState.Error("Account is suspended.")
            return
        }
        if (currentCredits < cost) {
            _sfxState.value = UiState.Error("Insufficient credits ($cost required).")
            return
        }
        viewModelScope.launch {
            _sfxState.value = UiState.Loading
            try {
                val cue = aiRepo.generateSfxBrief(clean)
                firestoreRepo.commitCreditDeduction(
                    uid = currentUserId,
                    cost = cost,
                    toolId = "audio_sfx_brief",
                    description = "Audio Lab SFX Brief: ${clean.take(40)}"
                )
                _sfxState.value = UiState.Success(cue)
            } catch (e: TimeoutCancellationException) {
                _sfxState.value = UiState.Error("SFX blueprint generation timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "SFX brief failed", e)
                _sfxState.value = UiState.Error(AiProviderRepository.formatUserFacingError(e))
            }
        }
    }

    fun generateVideoStoryboard(
        concept: String,
        durationLabel: String,
        currentCredits: Long,
        accountSuspended: Boolean
    ) {
        val clean = concept.trim()
        if (clean.isBlank()) {
            _storyboardState.value = UiState.Error("Enter a video concept.")
            return
        }
        val cost = 3L
        if (accountSuspended) {
            _storyboardState.value = UiState.Error("Account is suspended.")
            return
        }
        if (currentCredits < cost) {
            _storyboardState.value = UiState.Error("Insufficient credits ($cost required).")
            return
        }
        viewModelScope.launch {
            _storyboardState.value = UiState.Loading
            try {
                val shots = aiRepo.generateVideoStoryboard(clean, durationLabel)
                firestoreRepo.commitCreditDeduction(
                    uid = currentUserId,
                    cost = cost,
                    toolId = "video_storyboard",
                    description = "Video Storyboard: ${clean.take(40)}"
                )
                _storyboardState.value = UiState.Success(shots)
            } catch (e: TimeoutCancellationException) {
                _storyboardState.value = UiState.Error("Storyboard generation timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Video storyboard failed", e)
                _storyboardState.value = UiState.Error(AiProviderRepository.formatUserFacingError(e))
            }
        }
    }

    companion object {
        fun provideFactory(
            db: FirebaseFirestore,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CreativeStudiosViewModel(
                    CerevixFirestoreRepository(db),
                    AiProviderRepository(),
                    currentUserId
                )
            }
        }
    }
}
