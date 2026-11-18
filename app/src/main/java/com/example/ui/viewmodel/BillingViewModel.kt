package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.UiState
import com.example.data.model.BillingSummary
import com.example.data.model.CheckoutLaunchData
import com.example.data.repository.CerevixGatewayClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BillingViewModel(
    application: Application,
    private val gateway: CerevixGatewayClient = CerevixGatewayClient(application)
) : AndroidViewModel(application) {

    private val _summaryState = MutableStateFlow<UiState<BillingSummary>>(UiState.Loading)
    val summaryState: StateFlow<UiState<BillingSummary>> = _summaryState.asStateFlow()

    private val _checkoutSession = MutableStateFlow<CheckoutLaunchData?>(null)
    val checkoutSession: StateFlow<CheckoutLaunchData?> = _checkoutSession.asStateFlow()

    private val _billingNotice = MutableStateFlow<String?>(null)
    val billingNotice: StateFlow<String?> = _billingNotice.asStateFlow()

    private val _receiptHtml = MutableStateFlow<String?>(null)
    val receiptHtml: StateFlow<String?> = _receiptHtml.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        refreshBillingSummary()
    }

    fun refreshBillingSummary() {
        viewModelScope.launch {
            _summaryState.value = UiState.Loading
            val res = gateway.fetchBillingSummary()
            _summaryState.value = res.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { err ->
                    if (err is CancellationException) throw err
                    UiState.Error(err.message ?: "Failed to load billing summary from backend")
                }
            )
        }
    }

    fun startSubscriptionCheckout(planId: String, billingInterval: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = gateway.initiateSubscriptionCheckout(planId, billingInterval)
            _isBusy.value = false
            res.fold(
                onSuccess = { (_, checkout) ->
                    _checkoutSession.value = checkout
                    _billingNotice.value = "Razorpay subscription created. Complete payment in Checkout and verify below."
                },
                onFailure = { err ->
                    if (err is CancellationException) throw err
                    _billingNotice.value = err.message ?: "Unable to initiate Razorpay subscription"
                }
            )
        }
    }

    fun startCreditPackOrder(packId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = gateway.initiateCreditPackOrder(packId)
            _isBusy.value = false
            res.fold(
                onSuccess = { (_, checkout) ->
                    _checkoutSession.value = checkout
                    _billingNotice.value = "Razorpay order created. Complete payment in Checkout and verify below."
                },
                onFailure = { err ->
                    if (err is CancellationException) throw err
                    _billingNotice.value = err.message ?: "Unable to create credit pack order"
                }
            )
        }
    }

    fun verifyPaymentWithBackend(
        paymentId: String,
        subscriptionId: String,
        orderId: String,
        signature: String
    ) {
        if (paymentId.isBlank() || signature.isBlank()) {
            _billingNotice.value = "Payment ID and Razorpay Signature are required for backend verification."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val res = gateway.verifyRazorpayPayment(
                razorpayPaymentId = paymentId.trim(),
                razorpaySubscriptionId = subscriptionId.trim(),
                razorpayOrderId = orderId.trim(),
                razorpaySignature = signature.trim()
            )
            _isBusy.value = false
            res.fold(
                onSuccess = {
                    _checkoutSession.value = null
                    _billingNotice.value = "Payment verified by backend! Subscription & credits updated."
                    refreshBillingSummary()
                },
                onFailure = { err ->
                    if (err is CancellationException) throw err
                    _billingNotice.value = "Verification rejected: ${err.message}"
                }
            )
        }
    }

    fun cancelSubscription(subscriptionId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = gateway.cancelSubscription(subscriptionId, cancelAtCycleEnd = true)
            _isBusy.value = false
            res.fold(
                onSuccess = {
                    _billingNotice.value = "Subscription scheduled for cancellation at period end."
                    refreshBillingSummary()
                },
                onFailure = { err ->
                    if (err is CancellationException) throw err
                    _billingNotice.value = err.message ?: "Cancellation failed"
                }
            )
        }
    }

    fun loadReceipt(paymentId: String) {
        viewModelScope.launch {
            val res = gateway.fetchPaymentReceiptHtml(paymentId)
            res.fold(
                onSuccess = { _receiptHtml.value = it },
                onFailure = { _billingNotice.value = it.message ?: "Unable to load receipt" }
            )
        }
    }

    fun dismissCheckout() {
        _checkoutSession.value = null
    }

    fun dismissReceipt() {
        _receiptHtml.value = null
    }

    fun clearNotice() {
        _billingNotice.value = null
    }

    companion object {
        fun provideFactory(app: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer { BillingViewModel(app) }
        }
    }
}
