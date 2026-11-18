package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.UiState
import com.example.data.model.AccountStatus
import com.example.data.model.AdminGatewayOverview
import com.example.data.model.AiModelRouting
import com.example.data.model.AiProviderConfig
import com.example.data.model.AuditLog
import com.example.data.model.PlanTier
import com.example.data.model.SubscriptionPlan
import com.example.data.model.SystemAnnouncement
import com.example.data.model.UserProfile
import com.example.data.repository.CerevixFirestoreRepository
import com.example.data.repository.CerevixGatewayClient
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

private const val TAG = "AdminVM"

class AdminViewModel(
    application: Application,
    private val firestoreRepo: CerevixFirestoreRepository,
    private val gateway: CerevixGatewayClient = CerevixGatewayClient(application)
) : AndroidViewModel(application) {

    private val _adminToast = MutableStateFlow<String?>(null)
    val adminToast: StateFlow<String?> = _adminToast.asStateFlow()

    private val _gatewayOverview = MutableStateFlow<UiState<AdminGatewayOverview>>(UiState.Loading)
    val gatewayOverview: StateFlow<UiState<AdminGatewayOverview>> = _gatewayOverview.asStateFlow()

    private val _providersState = MutableStateFlow<UiState<List<AiProviderConfig>>>(UiState.Loading)
    val providersState: StateFlow<UiState<List<AiProviderConfig>>> = _providersState.asStateFlow()

    val usersState: StateFlow<UiState<List<UserProfile>>> =
        firestoreRepo.observeAllUsersAdmin()
            .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Admin users stream error", root)
                emit(UiState.Error(root.message ?: "Admin access denied or unavailable"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val announcementsState: StateFlow<UiState<List<SystemAnnouncement>>> =
        firestoreRepo.observeAllAnnouncementsAdmin()
            .map<List<SystemAnnouncement>, UiState<List<SystemAnnouncement>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load announcements"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val auditLogsState: StateFlow<UiState<List<AuditLog>>> =
        firestoreRepo.observeAuditLogsAdmin()
            .map<List<AuditLog>, UiState<List<AuditLog>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                emit(UiState.Error((firestoreError ?: error).message ?: "Failed to load audit logs"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    init {
        refreshGatewayAdminData()
    }

    fun refreshGatewayAdminData() {
        viewModelScope.launch {
            _gatewayOverview.value = UiState.Loading
            _providersState.value = UiState.Loading
            gateway.fetchAdminOverview().fold(
                onSuccess = { _gatewayOverview.value = UiState.Success(it) },
                onFailure = {
                    if (it is CancellationException) throw it
                    _gatewayOverview.value = UiState.Error(it.message ?: "Gateway overview unavailable")
                }
            )
            gateway.fetchAdminProviders().fold(
                onSuccess = { _providersState.value = UiState.Success(it) },
                onFailure = {
                    if (it is CancellationException) throw it
                    _providersState.value = UiState.Error(it.message ?: "Provider status unavailable")
                }
            )
        }
    }

    fun upsertProvider(provider: AiProviderConfig, newSecretValue: String) {
        viewModelScope.launch {
            gateway.upsertAdminProvider(provider, newSecretValue).fold(
                onSuccess = {
                    _adminToast.value = "Provider ${it.name} saved (secret kept server-side only)"
                    refreshGatewayAdminData()
                },
                onFailure = {
                    if (it is CancellationException) throw it
                    _adminToast.value = it.message ?: "Provider update failed"
                }
            )
        }
    }

    fun upsertModelRouting(model: AiModelRouting) {
        viewModelScope.launch {
            gateway.upsertAdminModelRouting(model).fold(
                onSuccess = {
                    _adminToast.value = "Updated routing for ${model.modelId}"
                    refreshGatewayAdminData()
                },
                onFailure = {
                    if (it is CancellationException) throw it
                    _adminToast.value = it.message ?: "Model routing update failed"
                }
            )
        }
    }

    fun refundPayment(paymentId: String, reason: String) {
        viewModelScope.launch {
            gateway.refundPaymentAdmin(paymentId, reason).fold(
                onSuccess = {
                    _adminToast.value = "Refund processed (${it.refundId})"
                    refreshGatewayAdminData()
                },
                onFailure = {
                    if (it is CancellationException) throw it
                    _adminToast.value = it.message ?: "Refund failed"
                }
            )
        }
    }

    fun revokeDeveloperKey(keyId: String) {
        viewModelScope.launch {
            gateway.revokeApiKeyAdmin(keyId).fold(
                onSuccess = {
                    _adminToast.value = "API key revoked"
                    refreshGatewayAdminData()
                },
                onFailure = {
                    if (it is CancellationException) throw it
                    _adminToast.value = it.message ?: "Key revocation failed"
                }
            )
        }
    }

    fun seedDefaultPlans() {
        viewModelScope.launch {
            val res = firestoreRepo.adminSeedDefaultPlans()
            _adminToast.value = if (res.isSuccess) "Synced consumer & developer plans to Firestore"
            else res.exceptionOrNull()?.message ?: "Seed failed"
        }
    }

    fun grantCredits(targetUid: String, delta: Long, reason: String) {
        viewModelScope.launch {
            val res = firestoreRepo.adminGrantCredits(targetUid, delta, reason)
            _adminToast.value = if (res.isSuccess) "Adjusted credits ($delta)"
            else res.exceptionOrNull()?.message ?: "Credit adjustment failed"
        }
    }

    fun updateUserPlanAndStatus(targetUid: String, plan: PlanTier, status: AccountStatus) {
        viewModelScope.launch {
            val res = firestoreRepo.adminUpdateUserStatusAndPlan(targetUid, plan, status)
            _adminToast.value = if (res.isSuccess) "Updated user to ${plan.name} / ${status.name}"
            else res.exceptionOrNull()?.message ?: "Update failed"
        }
    }

    fun upsertPlan(plan: SubscriptionPlan) {
        viewModelScope.launch {
            val res = firestoreRepo.adminUpsertPlan(plan)
            gateway.upsertAdminPlan(plan) // sync to backend if reachable
            _adminToast.value = if (res.isSuccess) "Saved plan ${plan.name}"
            else res.exceptionOrNull()?.message ?: "Plan update failed"
        }
    }

    fun publishAnnouncement(title: String, message: String, severity: String) {
        if (title.isBlank() || message.isBlank()) {
            _adminToast.value = "Title and message are required"
            return
        }
        viewModelScope.launch {
            val res = firestoreRepo.adminPublishAnnouncement(title, message, severity)
            _adminToast.value = if (res.isSuccess) "Announcement published"
            else res.exceptionOrNull()?.message ?: "Publish failed"
        }
    }

    fun toggleAnnouncement(id: String, active: Boolean) {
        viewModelScope.launch {
            firestoreRepo.adminToggleAnnouncement(id, active)
        }
    }

    fun clearToast() {
        _adminToast.value = null
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    companion object {
        fun provideFactory(application: Application, db: FirebaseFirestore): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { AdminViewModel(application, CerevixFirestoreRepository(db)) }
            }
    }
}
