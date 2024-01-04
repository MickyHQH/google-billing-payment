package com.mickyhqh.googlebillingpayment

import android.app.Activity
import androidx.lifecycle.Lifecycle
import com.mickyhqh.googlebillingpayment.model.PaymentItem
import com.mickyhqh.googlebillingpayment.model.PurchasedItem
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface PaymentClient {

    /*
    * This flow emit list purchaseOneTimeItem
    * */
    val purchaseOneTimeItem: StateFlow<List<PurchasedItem>>

    /*
    * This flow emit list purchaseSubItem
    * */
    val purchaseSubItem: StateFlow<List<PurchasedItem>>

    /*
    * This flow emit list paymentOneTimeItem (in-app product)
    * */
    val paymentOneTimeItem: StateFlow<List<PaymentItem>>

    /*
    * This flow emit list paymentSubItem ([subs-group (subs, subs, ...), subs-group (subs, subs, ...)]
    * */
    val paymentSubItem: StateFlow<List<List<PaymentItem>>>

    /*
    * Emit when get purchase (both in-app and subs) success
    * Pair value:
    *    - First: current trace count (don't care it)
    *    - Second: is both in-app and subs updated success
    * */
    val trackGetPurchase: StateFlow<Pair<Int, Boolean>>

    /*
    * Emit when has new purchase acknowledged
    * value: purchase token
    * */
    val hasNewAcknowledgePurchase: SharedFlow<String>

    /*
    * Launch a transaction (prepare by Google Play Billing Service)
    * */
    fun launchPurchase(activity: Activity, paymentItem: PaymentItem)

    /*
    * Re-get data flow, update purchaseOneTimeItem, purchaseSubItem, paymentOneTimeItem, paymentSubItem as new state
    * */
    fun restore()

    /*
    * observe billing client as activity lifecycle
    * */
    fun addObserver(lifecycle: Lifecycle)
}
