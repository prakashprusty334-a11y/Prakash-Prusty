package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.UiState
import com.example.data.model.AiModelCatalog
import com.example.data.model.AiProject
import com.example.data.model.CreditTransaction
import com.example.data.model.ProjectCategory
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SystemAnnouncement
import com.example.data.model.UserProfile
import com.example.data.repository.CerevixFirestoreRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "WorkspaceVM"

class WorkspaceViewModel(
    private val firestoreRepo: CerevixFirestoreRepository,
    val currentUserId: String
) : ViewModel() {

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        firestoreRepo.observeUserProfile(currentUserId)
            .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing profile", root)
                emit(UiState.Error(root.message ?: "Failed to load user profile"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val projectsState: StateFlow<UiState<List<AiProject>>> =
        firestoreRepo.observeProjects(currentUserId)
            .map<List<AiProject>, UiState<List<AiProject>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing projects", root)
                emit(UiState.Error(root.message ?: "Failed to load saved projects"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val transactionsState: StateFlow<UiState<List<CreditTransaction>>> =
        firestoreRepo.observeCreditTransactions(currentUserId)
            .map<List<CreditTransaction>, UiState<List<CreditTransaction>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing credit transactions", root)
                emit(UiState.Error(root.message ?: "Failed to load credit ledger"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val plansState: StateFlow<List<SubscriptionPlan>> =
        firestoreRepo.observeSubscriptionPlans()
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                emit(AiModelCatalog.defaultPlans)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000L),
                AiModelCatalog.defaultPlans
            )

    val announcementsState: StateFlow<List<SystemAnnouncement>> =
        firestoreRepo.observeAnnouncements()
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            firestoreRepo.ensureUserProfileInitialized(currentUserId)
            _isAdmin.value = firestoreRepo.isUserAdmin(currentUserId)
        }
    }

    fun updatePreferences(displayName: String, preferredModel: String) {
        viewModelScope.launch {
            val res = firestoreRepo.updateUserPreferences(currentUserId, displayName, preferredModel)
            _actionMessage.value = if (res.isSuccess) {
                "Preferences saved"
            } else {
                res.exceptionOrNull()?.message ?: "Failed to update preferences"
            }
        }
    }

    fun saveProject(
        title: String,
        category: ProjectCategory,
        prompt: String,
        outputText: String,
        modelId: String,
        creditsCost: Long
    ) {
        viewModelScope.launch {
            val res = firestoreRepo.saveProject(
                uid = currentUserId,
                title = title,
                category = category,
                prompt = prompt,
                outputText = outputText,
                modelId = modelId,
                creditsCost = creditsCost
            )
            _actionMessage.value = if (res.isSuccess) {
                "Saved to Projects"
            } else {
                res.exceptionOrNull()?.message ?: "Could not save project"
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            val res = firestoreRepo.deleteProject(currentUserId, projectId)
            if (res.isFailure) {
                _actionMessage.value = res.exceptionOrNull()?.message ?: "Delete failed"
            }
        }
    }

    fun clearWorkspaceData() {
        viewModelScope.launch {
            val res = firestoreRepo.clearUserWorkspaceData(currentUserId)
            _actionMessage.value = if (res.isSuccess) {
                "Workspace history cleared"
            } else {
                res.exceptionOrNull()?.message ?: "Failed to clear data"
            }
        }
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    companion object {
        fun provideFactory(
            db: FirebaseFirestore,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WorkspaceViewModel(CerevixFirestoreRepository(db), currentUserId)
            }
        }
    }
}
