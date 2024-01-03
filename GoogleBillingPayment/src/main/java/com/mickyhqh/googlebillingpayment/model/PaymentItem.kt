package com.mickyhqh.googlebillingpayment.model

import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase

enum class PurchaseType {
    OneTime,
    Sub,
}

enum class PurchaseCurrentState {
    Pending,
    Purchase,
    UnspecifiedState,
}

data class PurchasedItem(
    val purchaseState: PurchaseCurrentState,
    val token: String,
    val type: PurchaseType,
    val ref: Purchase,
    val paidStartedAt: Long = 0L,
    val paidEndAt: Long = 0L,
)

data class PaymentItem(
    val title: String?,
    val basePlanId: String?,
    val offerIdToken: String,
    val priceAmountMicros: Long?,
    val formattedPrice: String?,
    val priceCurrencyCode: String?,
    val type: PurchaseType,
    val ref: ProductDetails,
    val hasTrial2Week: Boolean = false,
)