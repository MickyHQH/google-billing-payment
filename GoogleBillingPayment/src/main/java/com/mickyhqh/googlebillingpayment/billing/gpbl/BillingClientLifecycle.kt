package com.mickyhqh.googlebillingpayment.billing.gpbl

import android.app.Activity
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.mickyhqh.googlebillingpayment.model.PurchaseType
import com.mickyhqh.googlebillingpayment.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow

internal class BillingClientLifecycle private constructor(
    private val applicationContext: Context,
    private val listInAppProduct: List<String>,
    private val listSubsProduct: List<String>,
    private val externalScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : DefaultLifecycleObserver {
    private val logger = Logger(TAG)

    /**
     * Track is get purchase success
     * (id, isBothOneTimeAndSubUpdateNewestValue)
     */
    private val _trackGetPurchase = MutableStateFlow(0)
    val trackGetPurchase = _trackGetPurchase.map {
        it to (it >= PurchaseType.values().size)
    }.stateIn(externalScope, SharingStarted.WhileSubscribed(), 0 to false)

    /**
     * Emit a purchaseToken of New_Acknowledge_Purchase
     */
    private val _hasNewAcknowledgePurchase = MutableSharedFlow<String>()
    val hasNewAcknowledgePurchase = _hasNewAcknowledgePurchase.asSharedFlow()

    /**
     * ProductDetails for all known products.
     */
    private val _subscriptionPurchasesProductDetails =
        MutableStateFlow<List<ProductDetails>>(emptyList())
    private val _oneTimeProductPurchasesProductDetails =
        MutableStateFlow<List<ProductDetails>>(emptyList())
    val subscriptionPurchasesProductDetails = _subscriptionPurchasesProductDetails.asStateFlow()
    val oneTimeProductPurchasesProductDetails = _oneTimeProductPurchasesProductDetails.asStateFlow()

    /**
     * Purchases are collectable. This list will be updated when the Billing Library
     * detects new or existing purchases.
     */
    private val _subscriptionPurchases = MutableStateFlow<List<Purchase>>(emptyList())
    private val _oneTimeProductPurchases = MutableStateFlow<List<Purchase>>(emptyList())
    val subscriptionProductPurchases = _subscriptionPurchases.asStateFlow()
    val oneTimeProductPurchases = _oneTimeProductPurchases.asStateFlow()

    /**
     * Instantiate a new BillingClient instance.
     */
    private lateinit var billingClient: BillingClient

    override fun onCreate(owner: LifecycleOwner) {
        logger.d("ON_CREATE")
        // Create a new BillingClient in onCreate().
        // Since the BillingClient can only be used once, we need to create a new instance
        // after ending the previous connection to the Google Play Store in onDestroy().
        billingClient = BillingClient.newBuilder(applicationContext)
            .setListener { billingResult, purchases ->
                onPurchasesUpdated(billingResult, purchases)
            }
            .enablePendingPurchases()
            .build()
        if (!billingClient.isReady) {
            logger.d("BillingClient: Start connection...")
            startConnection()
        }
    }

    private fun startConnection() {
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingServiceDisconnected() {
                    this@BillingClientLifecycle.onBillingServiceDisconnected()
                }

                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    val responseCode = billingResult.responseCode
                    val debugMessage = billingResult.debugMessage
                    logger.d("onBillingSetupFinished: $responseCode $debugMessage")
                    if (responseCode == BillingClient.BillingResponseCode.OK) {
                        // The billing client is ready.
                        // You can query product details and purchases here.
                        getProduct()
                    } else {
                        retryBillingServiceConnection()
                    }
                }
            },
        )
    }

    private fun retryBillingServiceConnection() {
        var tries = 1
        var isConnectionEstablished = false
        do {
            try {
                if (billingClient.isReady) break
                billingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingServiceDisconnected() {
                        // do nothing
                    }

                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            isConnectionEstablished = true
                            logger.d("Billing connection retry succeeded.")
                            // The billing client is ready.
                            // You can query product details and purchases here.
                            getProduct()
                        } else {
                            logger.e("Billing connection retry failed: ${billingResult.debugMessage}")
                            tries++
                        }
                    }
                })
            } catch (e: Exception) {
                e.message?.let { logger.e(it) }
                tries++
            }
        } while (tries <= MAX_RETRY_ATTEMPT && !isConnectionEstablished)
        if (!billingClient.isReady) {
            // pass for device unsupported billing
            _trackGetPurchase.value = PurchaseType.values().size
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        if (!billingClient.isReady) {
            logger.d("BillingClient: The server is not ready")
            return
        }
        if (_trackGetPurchase.value >= PurchaseType.values().size) {
            logger.d("ON_RESUME queryPurchases")
            queryPurchases()
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        logger.d("ON_DESTROY")
        if (billingClient.isReady) {
            logger.d("BillingClient can only be used once -- closing connection")
            // BillingClient can only be used once.
            // After calling endConnection(), we must create a new BillingClient.
            billingClient.endConnection()
        }
    }

    fun getProduct() {
        if (!billingClient.isReady) {
            logger.d("BillingClient: The server is not ready")
            _trackGetPurchase.value += 1
            return
        }
        queryProductDetail()
        queryPurchases()
    }

    private fun queryProductDetail() {
        querySubscriptionProductDetails()
        queryOneTimeProductDetails()
    }

    private fun queryPurchases() {
        _trackGetPurchase.value = 0
        querySubscriptionPurchases()
        queryOneTimeProductPurchases()
    }

    fun onBillingServiceDisconnected() {
        logger.d("onBillingServiceDisconnected")
        retryBillingServiceConnection()
    }

    private fun queryProductDetailsAsync(build: QueryProductDetailsParams, type: String) {
        billingClient.queryProductDetailsAsync(build) { p0, p1 ->
            onProductDetailsResponse(
                p0,
                p1,
                type,
            )
        }
    }

    private fun queryPurchasesAsync(build: QueryPurchasesParams, type: String) {
        billingClient.queryPurchasesAsync(build) { p0, p1 ->
            onQueryPurchasesResponse(
                p0,
                p1,
                type,
            )
        }
    }

    /**
     * In order to make purchases, you need the [ProductDetails] for the item or subscription.
     * This is an asynchronous call that will receive a result in [onProductDetailsResponse].
     *
     * querySubscriptionProductDetails uses method calls from GPBL 5.0.0. PBL5, released in May 2022,
     * is backwards compatible with previous versions.
     * To learn more about this you can read:
     * https://developer.android.com/google/play/billing/compatibility
     */
    private fun querySubscriptionProductDetails() {
        logger.d("querySubscriptionProductDetails listSubsProduct $listSubsProduct")
        val params = QueryProductDetailsParams.newBuilder()

        val productList: MutableList<QueryProductDetailsParams.Product> = arrayListOf()
        val type = BillingClient.ProductType.SUBS
        for (product in listSubsProduct) {
            productList.add(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(product)
                    .setProductType(type)
                    .build(),
            )
        }

        queryProductDetailsAsync(params.setProductList(productList).build(), type)

    }

    /**
     * In order to make purchases, you need the [ProductDetails] for one-time product.
     * This is an asynchronous call that will receive a result in [onProductDetailsResponse].
     *
     * queryOneTimeProductDetails uses the [BillingClient.queryProductDetailsAsync] method calls
     * from GPBL 5.0.0. PBL5, released in May 2022, is backwards compatible with previous versions.
     * To learn more about this you can read:
     * https://developer.android.com/google/play/billing/compatibility
     */
    private fun queryOneTimeProductDetails() {
        logger.d("queryOneTimeProductDetails listInAppProduct $listInAppProduct")
        val params = QueryProductDetailsParams.newBuilder()
        val type = BillingClient.ProductType.INAPP

        val productList = listInAppProduct.map { product ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(product)
                .setProductType(type)
                .build()
        }

        params.apply {
            setProductList(productList)
        }.let { productDetailsParams ->
            queryProductDetailsAsync(productDetailsParams.build(), type)
        }
    }

    /**
     * Receives the result from [querySubscriptionProductDetails].
     *
     * Store the ProductDetails and post them in the basicSubProductWithProductDetails and
     * premiumSubProductWithProductDetails. This allows other parts of the app to use the
     *  [ProductDetails] to show product information and make purchases.
     *
     * onProductDetailsResponse() uses method calls from GPBL 5.0.0. PBL5, released in May 2022,
     * is backwards compatible with previous versions.
     * To learn more about this you can read:
     * https://developer.android.com/google/play/billing/compatibility
     */
    private fun onProductDetailsResponse(
        billingResult: BillingResult,
        productDetailsList: MutableList<ProductDetails>,
        type: String,
    ) {
        val response = BillingResponse(billingResult.responseCode)
        val debugMessage = billingResult.debugMessage
        when {
            response.isOk -> {
                processProductDetails(productDetailsList, type)
            }

            response.isTerribleFailure -> {
                // These response codes are not expected.
                logger
                    .e("onProductDetailsResponse - Unexpected error: ${response.code} $debugMessage")
            }

            else -> {
                logger.e("onProductDetailsResponse: ${response.code} $debugMessage")
            }

        }
    }

    /**
     * This method is used to process the product details list returned by the [BillingClient]and
     * post the details to the basicSubProductWithProductDetails and
     * premiumSubProductWithProductDetails live data.
     *
     * @param productDetailsList The list of product details.
     *
     */
    private fun processProductDetails(
        productDetailsList: MutableList<ProductDetails>,
        type: String,
    ) {
        val expectedProductDetailsCount = listSubsProduct.size
        if (productDetailsList.isEmpty()) {
            logger
                .e(
                    "processProductDetails: Expected $expectedProductDetailsCount Found null ProductDetails. " +
                            "Check to see if the products you requested are correctly published in the Google Play Console.",
                )
            postProductDetails(emptyList(), type)
        } else {
            postProductDetails(productDetailsList, type)
        }
    }

    /**
     * This method is used to post the product details to the basicSubProductWithProductDetails
     * and premiumSubProductWithProductDetails live data.
     *
     * @param productDetailsList The list of product details.
     *
     */
    private fun postProductDetails(productDetailsList: List<ProductDetails>, type: String) {
        when (type) {
            BillingClient.ProductType.INAPP -> {
                _oneTimeProductPurchasesProductDetails.value = productDetailsList
            }

            BillingClient.ProductType.SUBS -> {
                _subscriptionPurchasesProductDetails.value = productDetailsList
            }
        }
    }

    /**
     * Query Google Play Billing for existing subscription purchases.
     *
     * New purchases will be provided to the PurchasesUpdatedListener.
     * You still need to check the Google Play Billing API to know when purchase tokens are removed.
     */
    private fun querySubscriptionPurchases() {
        val type = BillingClient.ProductType.SUBS
        queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(type)
                .build(),
            type,
        )
    }

    /**
     * Query Google Play Billing for existing one-time product purchases.
     *
     * New purchases will be provided to the PurchasesUpdatedListener.
     * You still need to check the Google Play Billing API to know when purchase tokens are removed.
     */
    private fun queryOneTimeProductPurchases() {
        val type = BillingClient.ProductType.INAPP
        queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(type)
                .build(),
            type,
        )
    }

    /**
     * Callback from the billing library when queryPurchasesAsync is called.
     */
    private fun onQueryPurchasesResponse(
        billingResult: BillingResult,
        purchasesList: MutableList<Purchase>,
        type: String,
    ) {
        logger.i("onQueryPurchasesResponse status: ${billingResult.responseCode}, message: ${billingResult.debugMessage}")
        processPurchases(purchasesList, type)
    }

    /**
     * Called by the Billing Library when new purchases are detected.
     */
    private fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?,
    ) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        logger
            .d("onPurchasesUpdated: $responseCode $debugMessage purchases: ${purchases?.size}")
        when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                getProduct()
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                logger.i("onPurchasesUpdated: User canceled the purchase")
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                logger.i("onPurchasesUpdated: The user already owns this item")
            }

            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                logger
                    .e("onPurchasesUpdated: Developer error means that Google Play does not recognize the configuration. If you are just getting started, make sure you have configured the application correctly in the Google Play Console. The product ID must match and the APK you are using must be signed with release keys.")
            }
        }
    }

    /**
     * Send purchase to StateFlow, which will trigger network call to verify the subscriptions
     * on the sever.
     */
    private fun processPurchases(purchasesList: List<Purchase>?, type: String) {
        logger.d("processPurchases: ${purchasesList?.size} purchase(s), type: $type")
        purchasesList?.let { list ->
            externalScope.launch(Dispatchers.IO) {
                val listPurchased = mutableListOf<Purchase>()
                list.forEach {
                    if (processPurchase(it)) {
                        logger
                            .d("processPurchases: purchasesList not null, add listPurchased ${it.products}")
                        listPurchased.add(it)
                    }
                }
                withContext(Dispatchers.Main) {
                    when (type) {
                        BillingClient.ProductType.INAPP -> {
                            _oneTimeProductPurchases.value = listPurchased
                        }

                        BillingClient.ProductType.SUBS -> {
                            _subscriptionPurchases.value = listPurchased
                        }
                    }
                    logAcknowledgementStatus(list)
                    delay(300)
                    _trackGetPurchase.value += 1
                }
            }
        } ?: run {
            _trackGetPurchase.value += 1
        }
    }

    private suspend fun processPurchase(purchase: Purchase): Boolean {
        logger
            .d("processPurchases: products ${purchase.products}, purchaseToken ${purchase.purchaseToken}, purchaseTime: ${purchase.purchaseTime}, purchaseState: ${purchase.purchaseState}, orderId: ${purchase.orderId}, signature: ${purchase.signature}, isAcknowledged: ${purchase.isAcknowledged}, isAutoRenewing: ${purchase.isAutoRenewing}")
        val isPurchase = purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        return if (purchase.isAcknowledged) true
        else if (isPurchase) {
            acknowledgePurchase(purchase.purchaseToken)
        } else false
    }

    /**
     * Log the number of purchases that are acknowledge and not acknowledged.
     *
     * https://developer.android.com/google/play/billing/billing_library_releases_notes#2_0_acknowledge
     *
     * When the purchase is first received, it will not be acknowledge.
     * This application sends the purchase token to the server for registration. After the
     * purchase token is registered to an account, the Android app acknowledges the purchase token.
     * The next time the purchase list is updated, it will contain acknowledged purchases.
     */
    private fun logAcknowledgementStatus(purchasesList: List<Purchase>) {
        var acknowledgedCounter = 0
        var unacknowledgedCounter = 0
        for (purchase in purchasesList) {
            if (purchase.isAcknowledged) {
                acknowledgedCounter++
            } else {
                unacknowledgedCounter++
            }
        }
        logger
            .d("logAcknowledgementStatus: acknowledged=$acknowledgedCounter unacknowledged=$unacknowledgedCounter")
    }

    /**
     * Launching the billing flow.
     *
     * Launching the UI to make a purchase requires a reference to the Activity.
     */
    fun launchBillingFlow(activity: Activity, params: BillingFlowParams): Int {
        if (!billingClient.isReady) {
            logger.e("launchBillingFlow: BillingClient is not ready")
        }
        val billingResult = billingClient.launchBillingFlow(activity, params)
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        logger.d("launchBillingFlow: BillingResponse $responseCode $debugMessage")
        return responseCode
    }

    /**
     * Acknowledge a purchase.
     *
     * https://developer.android.com/google/play/billing/billing_library_releases_notes#2_0_acknowledge
     *
     * Apps should acknowledge the purchase after confirming that the purchase token
     * has been associated with a user. This app only acknowledges purchases after
     * successfully receiving the subscription data back from the server.
     *
     * Developers can choose to acknowledge purchases from a server using the
     * Google Play Developer API. The server has direct access to the user database,
     * so using the Google Play Developer API for acknowledgement might be more reliable.
     * TODO(134506821): Acknowledge purchases on the server.
     * TODO: Remove client side purchase acknowledgement after removing the associated tests.
     * If the purchase token is not acknowledged within 3 days,
     * then Google Play will automatically refund and revoke the purchase.
     * This behavior helps ensure that users are not charged for subscriptions unless the
     * user has successfully received access to the content.
     * This eliminates a category of issues where users complain to developers
     * that they paid for something that the app is not giving to them.
     */
    private suspend fun acknowledgePurchase(purchaseToken: String): Boolean {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        for (trial in 1..MAX_RETRY_ATTEMPT) {
            val billingResult = billingClient.acknowledgePurchase(params)
            val response = BillingResponse(billingResult.responseCode)
            val debugMessage = billingResult.debugMessage
            when {
                response.isOk -> {
                    logger.i("Acknowledge success - token: $purchaseToken")
                    _hasNewAcknowledgePurchase.emit(purchaseToken)
                    return true
                }

                response.canFailGracefully -> {
                    logger.i("Token $purchaseToken is already owned.")
                }

                response.isRecoverableError -> {
                    // Retry to ack because these errors may be recoverable.
                    val duration = 500L * 2.0.pow(trial).toLong()
                    delay(duration)
                    if (trial < MAX_RETRY_ATTEMPT) {
                        logger
                            .e("Retrying($trial) to acknowledge for token $purchaseToken - code: ${billingResult.responseCode}, message: $debugMessage")
                    }
                }

                response.isNonrecoverableError || response.isTerribleFailure -> {
                    logger
                        .e("Failed to acknowledge for token $purchaseToken - code: $response, message: $debugMessage")
                    break
                }
            }
        }

        return false
    }

    companion object {
        private const val TAG = "BillingLifecycle"
        private const val MAX_RETRY_ATTEMPT = 3

        @Volatile
        private var INSTANCE: BillingClientLifecycle? = null

        fun getInstance(
            applicationContext: Context,
            listInAppProduct: List<String>,
            listSubsProduct: List<String>
        ): BillingClientLifecycle =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingClientLifecycle(
                    applicationContext,
                    listInAppProduct,
                    listSubsProduct
                ).also { INSTANCE = it }
            }
    }
}

@JvmInline
private value class BillingResponse(val code: Int) {
    val isOk: Boolean
        get() = code == BillingClient.BillingResponseCode.OK
    val canFailGracefully: Boolean
        get() = code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED
    val isRecoverableError: Boolean
        get() = code in setOf(
            BillingClient.BillingResponseCode.ERROR,
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        )
    val isNonrecoverableError: Boolean
        get() = code in setOf(
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.DEVELOPER_ERROR,
        )
    val isTerribleFailure: Boolean
        get() = code in setOf(
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED,
            BillingClient.BillingResponseCode.USER_CANCELED,
        )
}