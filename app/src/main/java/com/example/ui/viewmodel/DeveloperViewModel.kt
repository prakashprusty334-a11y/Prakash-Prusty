package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.UiState
import com.example.data.model.ApiKeyRecord
import com.example.data.model.CreatedApiKeyResponse
import com.example.data.model.CreditWallet
import com.example.data.model.DeveloperAccount
import com.example.data.model.DeveloperAnalytics
import com.example.data.model.PlaygroundHttpResult
import com.example.data.repository.CerevixGatewayClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeveloperViewModel(
    application: Application,
    private val gateway: CerevixGatewayClient = CerevixGatewayClient(application)
) : AndroidViewModel(application) {

    private val _gatewayUrl = MutableStateFlow(gateway.getGatewayBaseUrl())
    val gatewayUrl: StateFlow<String> = _gatewayUrl.asStateFlow()

    private val _developerState = MutableStateFlow<UiState<Pair<DeveloperAccount?, CreditWallet>>>(UiState.Loading)
    val developerState: StateFlow<UiState<Pair<DeveloperAccount?, CreditWallet>>> = _developerState.asStateFlow()

    private val _keysState = MutableStateFlow<UiState<List<ApiKeyRecord>>>(UiState.Loading)
    val keysState: StateFlow<UiState<List<ApiKeyRecord>>> = _keysState.asStateFlow()

    private val _analyticsState = MutableStateFlow<UiState<DeveloperAnalytics>>(UiState.Loading)
    val analyticsState: StateFlow<UiState<DeveloperAnalytics>> = _analyticsState.asStateFlow()

    private val _oneTimeCreatedKey = MutableStateFlow<CreatedApiKeyResponse?>(null)
    val oneTimeCreatedKey: StateFlow<CreatedApiKeyResponse?> = _oneTimeCreatedKey.asStateFlow()

    private val _playgroundState = MutableStateFlow<UiState<PlaygroundHttpResult>>(UiState.Idle)
    val playgroundState: StateFlow<UiState<PlaygroundHttpResult>> = _playgroundState.asStateFlow()

    private val _devMessage = MutableStateFlow<String?>(null)
    val devMessage: StateFlow<String?> = _devMessage.asStateFlow()

    init {
        refreshAll()
    }

    fun updateGatewayUrl(url: String) {
        gateway.setGatewayBaseUrl(url)
        _gatewayUrl.value = gateway.getGatewayBaseUrl()
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _developerState.value = UiState.Loading
            _keysState.value = UiState.Loading
            _analyticsState.value = UiState.Loading

            gateway.fetchDeveloperProfile().fold(
                onSuccess = { _developerState.value = UiState.Success(it) },
                onFailure = {
                    if (it is CancellationException) throw it
                    _developerState.value = UiState.Error(it.message ?: "Backend gateway unreachable")
                }
            )
            gateway.fetchApiKeys().fold(
                onSuccess = { _keysState.value = UiState.Success(it) },
                onFailure = {
                    if (it is CancellationException) throw it
                    _keysState.value = UiState.Error(it.message ?: "Unable to fetch API keys")
                }
            )
            gateway.fetchDeveloperAnalytics().fold(
                onSuccess = { _analyticsState.value = UiState.Success(it) },
                onFailure = {
                    if (it is CancellationException) throw it
                    _analyticsState.value = UiState.Error(it.message ?: "Unable to load analytics")
                }
            )
        }
    }

    fun registerDeveloper(organizationName: String) {
        viewModelScope.launch {
            gateway.registerDeveloper(organizationName).fold(
                onSuccess = {
                    _devMessage.value = "Developer account registered (${it.developerPlanId})"
                    refreshAll()
                },
                onFailure = {
                    if (it is CancellationException) throw it
                    _devMessage.value = it.message ?: "Developer registration failed"
                }
            )
        }
    }

    fun createApiKey(name: String, scopes: List<String>) {
        viewModelScope.launch {
            gateway.createApiKey(name.ifBlank { "CEREVIX Key" }, scopes).fold(
                onSuccess = { created ->
                    _oneTimeCreatedKey.value = created
                    refreshAll()
                },
                onFailure = {
                    if (it is CancellationException) throw it
                    _devMessage.value = it.message ?: "Key creation failed"
                }
            )
        }
    }

    fun revokeKey(keyId: String) {
        viewModelScope.launch {
            gateway.revokeApiKey(keyId).fold(
                onSuccess = {
                    _devMessage.value = "API key revoked"
                    refreshAll()
                },
                onFailure = {
                    if (it is CancellationException) throw it
                    _devMessage.value = it.message ?: "Revoke failed"
                }
            )
        }
    }

    fun regenerateKey(keyId: String) {
        viewModelScope.launch {
            gateway.regenerateApiKey(keyId).fold(
                onSuccess = { created ->
                    _oneTimeCreatedKey.value = created
                    refreshAll()
                },
                onFailure = {
                    if (it is CancellationException) throw it
                    _devMessage.value = it.message ?: "Regenerate failed"
                }
            )
        }
    }

    fun runPlayground(method: String, endpoint: String, apiKey: String, jsonBody: String) {
        if (apiKey.isBlank()) {
            _playgroundState.value = UiState.Error("Enter your crvx_live_... API key to run a live request.")
            return
        }
        viewModelScope.launch {
            _playgroundState.value = UiState.Loading
            gateway.executeApiPlaygroundRequest(method, endpoint, apiKey, jsonBody).fold(
                onSuccess = {
                    _playgroundState.value = UiState.Success(it)
                    refreshAll()
                },
                onFailure = {
                    if (it is CancellationException) throw it
                    _playgroundState.value = UiState.Error(it.message ?: "Request failed")
                }
            )
        }
    }

    fun dismissOneTimeKey() {
        _oneTimeCreatedKey.value = null
    }

    fun clearMessage() {
        _devMessage.value = null
    }

    companion object {
        fun provideFactory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { DeveloperViewModel(app) }
        }
    }
}
