package com.mickyhqh.googlebillingpayment

import android.app.Activity
import com.mickyhqh.googlebillingpayment.model.PaymentItem
import com.mickyhqh.googlebillingpayment.model.PurchasedItem
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface PaymentClient {
    val purchaseOneTimeItem: StateFlow<List<PurchasedItem>>
    val purchaseSubItem: StateFlow<List<PurchasedItem>>
    val paymentOneTimeItem: StateFlow<List<PaymentItem>>
    val paymentSubItem: StateFlow<List<List<PaymentItem>>>
    val trackGetPurchase: StateFlow<Pair<Int, Boolean>>
    val hasNewAcknowledgePurchase: SharedFlow<String>

    fun launchPurchase(activity: Activity, paymentItem: PaymentItem)
    fun restore()
}
