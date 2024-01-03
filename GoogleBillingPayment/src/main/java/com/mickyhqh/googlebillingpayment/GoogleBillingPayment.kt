package com.mickyhqh.googlebillingpayment

import android.app.Activity
import android.content.Context
import androidx.lifecycle.Lifecycle
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.mickyhqh.googlebillingpayment.billing.gpbl.BillingClientLifecycle
import com.mickyhqh.googlebillingpayment.model.PaymentItem
import com.mickyhqh.googlebillingpayment.model.PurchaseCurrentState
import com.mickyhqh.googlebillingpayment.model.PurchaseType
import com.mickyhqh.googlebillingpayment.model.PurchasedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Singleton

@Singleton
class GoogleBillingPayment private constructor(
    lifecycle: Lifecycle,
    applicationContext: Context,
    listInAppProduct: List<String>,
    listSubsProduct: List<String>,
    externalScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : PaymentClient {
    private val billingClientLifecycle: BillingClientLifecycle
    init {
        billingClientLifecycle = BillingClientLifecycle.getInstance(applicationContext, listInAppProduct, listSubsProduct)
        lifecycle.addObserver(billingClientLifecycle)
    }
    companion object {

        @Volatile
        private var INSTANCE: GoogleBillingPayment? = null

        fun getInstance(
            lifecycle: Lifecycle,
            applicationContext: Context,
            listInAppProduct: List<String>,
            listSubsProduct: List<String>
        ): GoogleBillingPayment =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: GoogleBillingPayment(
                    lifecycle,
                    applicationContext,
                    listInAppProduct,
                    listSubsProduct
                ).also { INSTANCE = it }
            }
    }

    override val purchaseOneTimeItem: StateFlow<List<PurchasedItem>> =
        billingClientLifecycle.oneTimeProductPurchases.map { purchaseList ->
            purchaseList.map { purchase ->
                PurchasedItem(
                    ref = purchase,
                    purchaseState = mapPurchaseState(purchase.purchaseState),
                    token = purchase.purchaseToken,
                    type = PurchaseType.OneTime,
                    paidStartedAt = purchase.purchaseTime,
                )
            }
        }.stateIn(externalScope, SharingStarted.Eagerly, emptyList())

    override val purchaseSubItem: StateFlow<List<PurchasedItem>> =
        billingClientLifecycle.subscriptionProductPurchases.map { purchaseList ->
            purchaseList.map { purchase ->
                PurchasedItem(
                    ref = purchase,
                    purchaseState = mapPurchaseState(purchase.purchaseState),
                    token = purchase.purchaseToken,
                    type = PurchaseType.Sub,
                    paidStartedAt = purchase.purchaseTime,
                )
            }
        }.stateIn(externalScope, SharingStarted.Eagerly, emptyList())

    override val paymentOneTimeItem: StateFlow<List<PaymentItem>> =
        billingClientLifecycle.oneTimeProductPurchasesProductDetails.map { purchaseList ->
            purchaseList.map {
                PaymentItem(
                    ref = it,
                    title = it.name,
                    basePlanId = it.productId,
                    offerIdToken = "",
                    priceAmountMicros = it.oneTimePurchaseOfferDetails?.priceAmountMicros,
                    formattedPrice = it.oneTimePurchaseOfferDetails?.formattedPrice,
                    priceCurrencyCode = it.oneTimePurchaseOfferDetails?.priceCurrencyCode,
                    type = PurchaseType.OneTime,
                )
            }
        }.stateIn(externalScope, SharingStarted.WhileSubscribed(), emptyList())

    override val paymentSubItem: StateFlow<List<List<PaymentItem>>> =
        billingClientLifecycle.subscriptionPurchasesProductDetails.map { purchaseList ->
            purchaseList.map { purchaseItem ->
                purchaseItem.subscriptionOfferDetails?.groupBy { it.basePlanId }
                    ?.filter { it.key.isNotEmpty() && it.value.isNotEmpty() }
                    ?.map { map ->
                        val sub = map.value.find { it.offerToken.isNotEmpty() } ?: map.value.first()
                        val pricingPhases = sub.pricingPhases.pricingPhaseList
                        val hasTrial2Week =
                            pricingPhases.firstOrNull { it.priceAmountMicros == 0L } != null
                        val price = pricingPhases.firstOrNull { it.priceAmountMicros > 0L }
                        PaymentItem(
                            ref = purchaseItem,
                            title = purchaseItem.name,
                            basePlanId = sub.basePlanId,
                            offerIdToken = sub.offerToken,
                            priceAmountMicros = price?.priceAmountMicros,
                            formattedPrice = price?.formattedPrice,
                            priceCurrencyCode = price?.priceCurrencyCode,
                            hasTrial2Week = hasTrial2Week,
                            type = PurchaseType.Sub,
                        )
                    } ?: emptyList()
            }
        }.stateIn(externalScope, SharingStarted.WhileSubscribed(), emptyList())

    override val trackGetPurchase: StateFlow<Pair<Int, Boolean>> = billingClientLifecycle.trackGetPurchase
    override val hasNewAcknowledgePurchase: SharedFlow<String> = billingClientLifecycle.hasNewAcknowledgePurchase

    override fun launchPurchase(activity: Activity, paymentItem: PaymentItem) {
        val productDetails = paymentItem.ref as? ProductDetails ?: return
        val upDowngrade = paymentItem.type == PurchaseType.Sub && purchaseSubItem.value.isNotEmpty()
        val billingParams = if (upDowngrade) {
            val oldToken = purchaseSubItem.value.firstOrNull()?.token ?: ""
            upDowngradeBillingFlowParamsBuilder(productDetails, paymentItem.offerIdToken, oldToken)
        } else {
            if (paymentItem.type == PurchaseType.Sub) subBillingFlowParamsBuilder(
                productDetails,
                paymentItem.offerIdToken,
            )
            else oneTimeBillingFlowParamsBuilder(productDetails)
        }
        billingClientLifecycle.launchBillingFlow(activity, billingParams)
    }

    override fun restore() {
        billingClientLifecycle.getProduct()
    }

    private fun mapPurchaseState(purchaseState: Int): PurchaseCurrentState {
        return when (purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PurchaseCurrentState.Purchase
            Purchase.PurchaseState.PENDING -> PurchaseCurrentState.Pending
            else -> PurchaseCurrentState.UnspecifiedState
        }
    }

    private fun upDowngradeBillingFlowParamsBuilder(
        productDetails: ProductDetails, offerToken: String, oldToken: String,
    ): BillingFlowParams {
        return BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build(),
            ),
        ).setSubscriptionUpdateParams(
            BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                .setOldPurchaseToken(oldToken)
                .setSubscriptionReplacementMode(
                    BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.CHARGE_FULL_PRICE,
                ).build(),
        ).build()
    }

    private fun subBillingFlowParamsBuilder(productDetails: ProductDetails, offerToken: String):
            BillingFlowParams {
        return BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build(),
            ),
        ).build()
    }

    private fun oneTimeBillingFlowParamsBuilder(productDetails: ProductDetails):
            BillingFlowParams {
        return BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build(),
            ),
        ).build()
    }

}
