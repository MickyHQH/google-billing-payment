package com.mickyhqh.paymentmoduleproject

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.mickyhqh.googlebillingpayment.model.PaymentItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel: ViewModel() {

    private val _isSplashPending = MutableStateFlow(true)
    val isSplashPending = _isSplashPending.asStateFlow()

    val onTimeItems = Injection.googleBillingPayment.paymentOneTimeItem.stateIn(
        viewModelScope,
        initialValue = null,
        started = SharingStarted.WhileSubscribed(1_000),
    )

    val subsItems = Injection.googleBillingPayment.paymentSubItem.stateIn(
        viewModelScope,
        initialValue = null,
        started = SharingStarted.WhileSubscribed(1_000),
    )

    val purchaseOneTimeItems = Injection.googleBillingPayment.purchaseOneTimeItem.stateIn(
        viewModelScope,
        initialValue = null,
        started = SharingStarted.WhileSubscribed(1_000),
    )

    val purchaseSubItems = Injection.googleBillingPayment.purchaseSubItem.stateIn(
        viewModelScope,
        initialValue = null,
        started = SharingStarted.WhileSubscribed(1_000),
    )

    fun launchPurchase(activity: Activity, paymentItem: PaymentItem) {
        Injection.googleBillingPayment.launchPurchase(activity, paymentItem)
    }

    init {
        viewModelScope.launch {
            Injection.googleBillingPayment.trackGetPurchase.collect { (_, isNeedHandle) ->
                if (isNeedHandle) {
                    _isSplashPending.value = false
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                return MainViewModel() as T
            }
        }
    }
}