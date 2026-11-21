package com.mistysteps.app.eraser

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import java.lang.ref.WeakReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The eraser as a one-time Google Play purchase (product `magic_eraser`). Whether it is owned is read from Play on start and on
 * every resume, and cached so the tool keeps working offline once bought. Nothing here throws to the screen: when Play is out
 * of reach the answer is "not available", and an owned eraser stays owned.
 */
class PlayPurchases(private val app: Application) : EraserPurchases {
    private val prefs = app.getSharedPreferences("eraser", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val storeAvailable = true
    private val _owned = MutableStateFlow(prefs.getBoolean(KEY_OWNED, false))
    override val owned: StateFlow<Boolean> = _owned
    private val _price = MutableStateFlow<String?>(null)
    override val priceText: StateFlow<String?> = _price

    private var details: ProductDetails? = null
    private var activity = WeakReference<Activity>(null)
    private var pendingBuy: CompletableDeferred<BuyResult>? = null

    private val client: BillingClient = BillingClient.newBuilder(app)
        .setListener(PurchasesUpdatedListener { result, purchases -> onPurchasesUpdated(result, purchases) })
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { activity = WeakReference(a); scope.launch { refresh() } }
            override fun onActivityPaused(a: Activity) { if (activity.get() === a) activity.clear() }
            override fun onActivityCreated(a: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
        scope.launch { refresh() }
    }

    /** Connects (retrying a few times), then reads the price and what is owned. Never throws. */
    private suspend fun refresh() {
        if (!connect()) return
        try {
            loadDetails()
            loadOwned()
        } catch (e: Exception) {
            Log.w(TAG, "Play Billing refresh failed", e)
        }
    }

    private suspend fun connect(): Boolean {
        repeat(CONNECT_TRIES) { attempt ->
            if (client.isReady) return true
            val ready = CompletableDeferred<Boolean>()
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) { ready.complete(result.responseCode == BillingClient.BillingResponseCode.OK) }
                override fun onBillingServiceDisconnected() { ready.complete(false) }
            })
            if (withTimeoutOrNull(CONNECT_TIMEOUT_MS) { ready.await() } == true) return true
            delay(1_000L * (attempt + 1))
        }
        return client.isReady
    }

    private suspend fun loadDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()
        val result = client.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build())
        details = result.productDetailsList?.firstOrNull()
        _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
    }

    private suspend fun loadOwned() {
        val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return // keep what we knew
        handle(result.purchasesList)
    }

    /** Applies a list of purchases from Play: unlocked if the eraser is among them and paid for. */
    private suspend fun handle(purchases: List<Purchase>) {
        val mine = purchases.filter { PRODUCT_ID in it.products }
        val paid = mine.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (paid != null && !paid.isAcknowledged) {
            // Play refunds a purchase that is not acknowledged within three days.
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(paid.purchaseToken).build())
        }
        val owns = paid != null
        // A pending purchase is neither: keep the old answer until Play says paid or gone.
        if (owns || mine.none { it.purchaseState == Purchase.PurchaseState.PENDING }) setOwned(owns)
    }

    private fun setOwned(value: Boolean) {
        prefs.edit().putBoolean(KEY_OWNED, value).apply()
        _owned.value = value
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val outcome = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> BuyResult.BOUGHT
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> BuyResult.BOUGHT
            BillingClient.BillingResponseCode.USER_CANCELED -> BuyResult.CANCELLED
            else -> BuyResult.UNAVAILABLE
        }
        scope.launch {
            try {
                if (outcome == BuyResult.BOUGHT) {
                    if (purchases != null) handle(purchases) else loadOwned()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the purchase", e)
            }
            pendingBuy?.complete(if (outcome == BuyResult.BOUGHT && !_owned.value) BuyResult.UNAVAILABLE else outcome)
        }
    }

    override suspend fun buy(): BuyResult {
        if (!connect()) return BuyResult.UNAVAILABLE
        if (details == null) try { loadDetails() } catch (e: Exception) { Log.w(TAG, "No product details", e) }
        val product = details ?: return BuyResult.UNAVAILABLE
        val host = activity.get() ?: return BuyResult.UNAVAILABLE
        val wait = CompletableDeferred<BuyResult>().also { pendingBuy = it }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()))
            .build()
        val launched = client.launchBillingFlow(host, params)
        if (launched.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingBuy = null
            return if (launched.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                refresh(); if (_owned.value) BuyResult.BOUGHT else BuyResult.UNAVAILABLE
            } else BuyResult.UNAVAILABLE
        }
        return try { wait.await() } finally { pendingBuy = null }
    }

    private companion object {
        const val TAG = "PlayPurchases"
        const val PRODUCT_ID = "magic_eraser"
        const val KEY_OWNED = "owned"
        const val CONNECT_TRIES = 3
        const val CONNECT_TIMEOUT_MS = 8_000L
    }
}
