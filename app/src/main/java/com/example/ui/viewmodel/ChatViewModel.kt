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
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.SenderRole
import com.example.data.repository.AiProviderRepository
import com.example.data.repository.CerevixFirestoreRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ChatVM"

data class ChatAttachment(
    val label: String,
    val bitmap: Bitmap? = null,
    val documentBytes: ByteArray? = null,
    val mimeType: String? = null
)

class ChatViewModel(
    private val firestoreRepo: CerevixFirestoreRepository,
    private val aiRepo: AiProviderRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedModelId = MutableStateFlow(AiModelCatalog.DEFAULT_TEXT_MODEL.id)
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _streamingState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val streamingState: StateFlow<UiState<Unit>> = _streamingState.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<ChatAttachment?>(null)
    val pendingAttachment: StateFlow<ChatAttachment?> = _pendingAttachment.asStateFlow()

    val conversationsState: StateFlow<UiState<List<Conversation>>> =
        combine(
            firestoreRepo.observeConversations(currentUserId),
            _searchQuery
        ) { list, query ->
            val filtered = if (query.isBlank()) list else {
                list.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.lastMessagePreview.contains(query, ignoreCase = true)
                }
            }
            if (_activeConversationId.value == null && list.isNotEmpty()) {
                _activeConversationId.value = list.first().id
            }
            UiState.Success(filtered) as UiState<List<Conversation>>
        }.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load conversations"))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val messagesState: StateFlow<UiState<List<ChatMessage>>> =
        _activeConversationId.flatMapLatest { convId ->
            if (convId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                firestoreRepo.observeMessages(currentUserId, convId)
                    .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load messages"))
                    }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))

    fun selectModel(modelId: String) {
        _selectedModelId.value = modelId
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectConversation(conversationId: String) {
        _activeConversationId.value = conversationId
        _streamingState.value = UiState.Idle
    }

    fun setAttachment(attachment: ChatAttachment?) {
        _pendingAttachment.value = attachment
    }

    fun createNewConversation(title: String = "New Conversation") {
        viewModelScope.launch {
            val res = firestoreRepo.createConversation(currentUserId, title, _selectedModelId.value)
            res.onSuccess { newId ->
                _activeConversationId.value = newId
                _streamingState.value = UiState.Idle
            }.onFailure { err ->
                _streamingState.value = UiState.Error(err.message ?: "Could not create chat")
            }
        }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            firestoreRepo.renameConversation(currentUserId, conversationId, newTitle)
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteConversation(currentUserId, conversationId)
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = null
            }
        }
    }

    fun sendMessage(
        prompt: String,
        currentCredits: Long,
        accountSuspended: Boolean
    ) {
        val cleanPrompt = prompt.trim()
        val attachment = _pendingAttachment.value
        if (cleanPrompt.isBlank() && attachment == null) return

        val modelOpt = AiModelCatalog.textModels.firstOrNull { it.id == _selectedModelId.value }
            ?: AiModelCatalog.DEFAULT_TEXT_MODEL

        if (accountSuspended) {
            _streamingState.value = UiState.Error("Account is suspended. Contact an administrator.")
            return
        }
        if (currentCredits < modelOpt.creditCost) {
            _streamingState.value = UiState.Error(
                "Insufficient credits (${modelOpt.creditCost} required, $currentCredits available)."
            )
            return
        }

        val finalPrompt = cleanPrompt.ifBlank { "Analyze the attached ${attachment?.label ?: "file"}." }

        viewModelScope.launch {
            _streamingState.value = UiState.Loading
            try {
                // Ensure a conversation exists
                val convId = _activeConversationId.value ?: run {
                    val created = firestoreRepo.createConversation(
                        currentUserId,
                        finalPrompt.take(42),
                        modelOpt.id
                    ).getOrThrow()
                    _activeConversationId.value = created
                    created
                }

                val existingMessages = (messagesState.value as? UiState.Success)?.data ?: emptyList()
                val isFirstTurn = existingMessages.isEmpty()

                firestoreRepo.appendChatMessage(
                    uid = currentUserId,
                    conversationId = convId,
                    senderRole = SenderRole.user,
                    content = finalPrompt,
                    modelId = modelOpt.id,
                    attachmentLabel = attachment?.label,
                    updateTitleIfFirst = if (isFirstTurn) finalPrompt.take(48) else null
                ).getOrThrow()

                _pendingAttachment.value = null

                var latestResponse = ""
                aiRepo.streamChatMessage(
                    modelId = modelOpt.id,
                    history = existingMessages,
                    userPrompt = finalPrompt,
                    attachedImage = attachment?.bitmap,
                    attachedDocumentBytes = attachment?.documentBytes,
                    attachedDocumentMime = attachment?.mimeType,
                    attachedDocumentName = attachment?.label
                ).collect { partial ->
                    latestResponse = partial
                    _streamingState.value = UiState.Streaming(partial)
                }

                firestoreRepo.appendChatMessage(
                    uid = currentUserId,
                    conversationId = convId,
                    senderRole = SenderRole.model,
                    content = latestResponse,
                    modelId = modelOpt.id
                ).getOrThrow()

                firestoreRepo.commitCreditDeduction(
                    uid = currentUserId,
                    cost = modelOpt.creditCost.toLong(),
                    toolId = "ai_chat_${modelOpt.id}",
                    description = "Chat response (${modelOpt.label})"
                ).onFailure { creditErr ->
                    Log.w(TAG, "Credit ledger deduction warning", creditErr)
                }

                _streamingState.value = UiState.Idle
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat timed out", e)
                _streamingState.value = UiState.Error("Request timed out after 60 seconds. Tap Retry below — no credits deducted.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat failed", e)
                _streamingState.value = UiState.Error(AiProviderRepository.formatUserFacingError(e))
            }
        }
    }

    fun regenerateLastResponse(currentCredits: Long, accountSuspended: Boolean) {
        val messages = (messagesState.value as? UiState.Success)?.data ?: return
        val lastUserIdx = messages.indexOfLast { it.resolvedSenderRole() == SenderRole.user }
        if (lastUserIdx < 0) return
        val lastUserMsg = messages[lastUserIdx]
        val modelOpt = AiModelCatalog.textModels.firstOrNull { it.id == _selectedModelId.value }
            ?: AiModelCatalog.DEFAULT_TEXT_MODEL
        val convId = _activeConversationId.value ?: return

        if (accountSuspended) {
            _streamingState.value = UiState.Error("Account is suspended.")
            return
        }
        if (currentCredits < modelOpt.creditCost) {
            _streamingState.value = UiState.Error("Insufficient credits (${modelOpt.creditCost} required).")
            return
        }

        viewModelScope.launch {
            _streamingState.value = UiState.Loading
            try {
                val priorHistory = messages.subList(0, lastUserIdx)
                var latestResponse = ""
                aiRepo.streamChatMessage(
                    modelId = modelOpt.id,
                    history = priorHistory,
                    userPrompt = lastUserMsg.content
                ).collect { partial ->
                    latestResponse = partial
                    _streamingState.value = UiState.Streaming(partial)
                }

                firestoreRepo.appendChatMessage(
                    uid = currentUserId,
                    conversationId = convId,
                    senderRole = SenderRole.model,
                    content = latestResponse,
                    modelId = modelOpt.id
                ).getOrThrow()

                firestoreRepo.commitCreditDeduction(
                    uid = currentUserId,
                    cost = modelOpt.creditCost.toLong(),
                    toolId = "ai_chat_regen",
                    description = "Regenerated response (${modelOpt.label})"
                ).onFailure { creditErr ->
                    Log.w(TAG, "Regen credit ledger warning", creditErr)
                }
                _streamingState.value = UiState.Idle
            } catch (e: TimeoutCancellationException) {
                _streamingState.value = UiState.Error("Regeneration timed out after 60 seconds. Tap Retry to try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Regenerate failed", e)
                _streamingState.value = UiState.Error(AiProviderRepository.formatUserFacingError(e))
            }
        }
    }

    fun dismissError() {
        if (_streamingState.value is UiState.Error) {
            _streamingState.value = UiState.Idle
        }
    }

    companion object {
        fun provideFactory(
            db: FirebaseFirestore,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    CerevixFirestoreRepository(db),
                    AiProviderRepository(),
                    currentUserId
                )
            }
        }
    }
}
