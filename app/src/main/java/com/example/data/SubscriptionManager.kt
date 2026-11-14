package com.example.data

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SubscriptionPlan(
    val productId: String,
    val title: String,
    val price: String,
    val period: String,
    val savings: String?
) {
    MONTHLY("hearpod_pro_monthly", "Pro Monthly", "$4.99", "/month", null),
    ANNUAL("hearpod_pro_annual", "Pro Annual", "$39.99", "/year", "Save 33%");

    companion object {
        fun fromProductId(productId: String): SubscriptionPlan? =
            entries.firstOrNull { it.productId == productId }
    }
}

data class SubscriptionState(
    val isPro: Boolean = false,
    val activePlan: SubscriptionPlan? = null,
    val expirationDate: String? = null,
    val isTrialActive: Boolean = false,
    val lastCloudBackupTime: String? = null,
    val isCloudBackupSyncing: Boolean = false,
    val autoCloudBackupEnabled: Boolean = true,
    val isPlayBillingReady: Boolean = false,
    val planPrices: Map<SubscriptionPlan, String> = emptyMap(),
    val billingStatusMessage: String? = null
)

class SubscriptionManager(private val context: Context) : PurchasesUpdatedListener {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hearpod_subscription_prefs", Context.MODE_PRIVATE)

    private val _subscriptionState = MutableStateFlow(loadState())
    val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()
    private var billingClient: BillingClient? = null

    init {
        initializeBillingClient()
    }

    private fun loadState(): SubscriptionState {
        val isPro = prefs.getBoolean("is_pro", false)
        val planName = prefs.getString("active_plan", null)
        val activePlan = planName?.let {
            try { SubscriptionPlan.valueOf(it) } catch (e: Exception) { null }
        }
        val expiration = prefs.getString("expiration_date", null)
        val isTrial = prefs.getBoolean("is_trial", false)
        val lastBackup = prefs.getString("last_cloud_backup", "Today, 08:30 AM")
        val autoBackup = prefs.getBoolean("auto_backup", true)

        return SubscriptionState(
            isPro = isPro,
            activePlan = activePlan,
            expirationDate = expiration,
            isTrialActive = isTrial,
            lastCloudBackupTime = lastBackup,
            isCloudBackupSyncing = false,
            autoCloudBackupEnabled = autoBackup
        )
    }

    private fun initializeBillingClient() {
        try {
            val pendingParams = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()

            billingClient = BillingClient.newBuilder(context.applicationContext)
                .setListener(this)
                .enablePendingPurchases(pendingParams)
                .enableAutoServiceReconnection()
                .build()

            startBillingConnection()
        } catch (t: Throwable) {
            Log.w("SubscriptionManager", "Play Billing unavailable in this environment: ${t.message}")
        }
    }

    private fun startBillingConnection() {
        val client = billingClient ?: return
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        _subscriptionState.value = _subscriptionState.value.copy(
                            isPlayBillingReady = true,
                            billingStatusMessage = null
                        )
                        querySubscriptionProducts()
                        queryExistingSubscriptions()
                    } else {
                        Log.w("SubscriptionManager", "Billing setup failed: ${billingResult.debugMessage}")
                        _subscriptionState.value = _subscriptionState.value.copy(
                            isPlayBillingReady = false
                        )
                    }
                }

                override fun onBillingServiceDisconnected() {
                    _subscriptionState.value = _subscriptionState.value.copy(
                        isPlayBillingReady = false
                    )
                }
            })
        } catch (t: Throwable) {
            Log.w("SubscriptionManager", "Unable to connect to Play Billing: ${t.message}")
        }
    }

    fun querySubscriptionProducts() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val products = SubscriptionPlan.entries.map { plan ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(plan.productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        client.queryProductDetailsAsync(params) { billingResult, queryResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val updatedPrices = mutableMapOf<SubscriptionPlan, String>()
                for (details in queryResult.productDetailsList) {
                    productDetailsMap[details.productId] = details
                    val plan = SubscriptionPlan.fromProductId(details.productId) ?: continue
                    val formattedPrice = details.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.lastOrNull()
                        ?.formattedPrice
                    if (!formattedPrice.isNullOrBlank()) {
                        updatedPrices[plan] = formattedPrice
                    }
                }
                _subscriptionState.value = _subscriptionState.value.copy(
                    planPrices = updatedPrices
                )
            } else {
                Log.w("SubscriptionManager", "Product query failed: ${billingResult.debugMessage}")
            }
        }
    }

    fun queryExistingSubscriptions() {
        val client = billingClient ?: return
        if (!client.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        client.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val activePurchase = purchases.firstOrNull {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (activePurchase != null) {
                    handleVerifiedPurchase(activePurchase)
                }
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity?, plan: SubscriptionPlan) {
        val client = billingClient
        val details = productDetailsMap[plan.productId]

        if (activity == null || client == null || !client.isReady) {
            _subscriptionState.value = _subscriptionState.value.copy(
                billingStatusMessage = "Google Play Billing is connecting. Ensure Google Play Store is active on a physical device or emulator."
            )
            startBillingConnection()
            return
        }

        if (details == null) {
            querySubscriptionProducts()
            _subscriptionState.value = _subscriptionState.value.copy(
                billingStatusMessage = "Subscription '${plan.productId}' not loaded from Google Play yet. Create & activate it in Play Console, then test from the Internal Testing track."
            )
            return
        }

        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply {
                if (offerToken != null) {
                    setOfferToken(offerToken)
                }
            }
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _subscriptionState.value = _subscriptionState.value.copy(
                billingStatusMessage = "Unable to start Google Play checkout (${result.debugMessage})."
            )
        } else {
            _subscriptionState.value = _subscriptionState.value.copy(billingStatusMessage = null)
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    handleVerifiedPurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _subscriptionState.value = _subscriptionState.value.copy(
                    billingStatusMessage = "Purchase canceled."
                )
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryExistingSubscriptions()
            }
            else -> {
                _subscriptionState.value = _subscriptionState.value.copy(
                    billingStatusMessage = "Play Billing error: ${billingResult.debugMessage}"
                )
            }
        }
    }

    private fun handleVerifiedPurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val matchedPlan = purchase.products.firstNotNullOfOrNull {
                SubscriptionPlan.fromProductId(it)
            } ?: SubscriptionPlan.ANNUAL

            upgradeToPro(matchedPlan)

            if (!purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient?.acknowledgePurchase(ackParams) { ackResult ->
                    if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.w("SubscriptionManager", "Failed to acknowledge purchase: ${ackResult.debugMessage}")
                    }
                }
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            _subscriptionState.value = _subscriptionState.value.copy(
                billingStatusMessage = "Payment pending approval from Google Play."
            )
        }
    }

    fun clearBillingStatusMessage() {
        _subscriptionState.value = _subscriptionState.value.copy(billingStatusMessage = null)
    }

    fun upgradeToPro(plan: SubscriptionPlan) {
        val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val expiryTime = System.currentTimeMillis() +
            if (plan == SubscriptionPlan.ANNUAL) 365L * 24 * 3600 * 1000 else 30L * 24 * 3600 * 1000
        val expiryDateStr = formatter.format(Date(expiryTime))

        prefs.edit()
            .putBoolean("is_pro", true)
            .putString("active_plan", plan.name)
            .putString("expiration_date", expiryDateStr)
            .putBoolean("is_trial", true)
            .apply()

        _subscriptionState.value = _subscriptionState.value.copy(
            isPro = true,
            activePlan = plan,
            expirationDate = expiryDateStr,
            isTrialActive = true,
            billingStatusMessage = null
        )
    }

    fun cancelSubscription() {
        prefs.edit()
            .putBoolean("is_pro", false)
            .remove("active_plan")
            .remove("expiration_date")
            .putBoolean("is_trial", false)
            .apply()

        _subscriptionState.value = _subscriptionState.value.copy(
            isPro = false,
            activePlan = null,
            expirationDate = null,
            isTrialActive = false,
            billingStatusMessage = null
        )
    }

    fun performCloudBackupSync() {
        val formatter = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        val nowStr = formatter.format(Date())

        prefs.edit()
            .putString("last_cloud_backup", nowStr)
            .apply()

        _subscriptionState.value = _subscriptionState.value.copy(
            lastCloudBackupTime = nowStr,
            isCloudBackupSyncing = false
        )
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_backup", enabled).apply()
        _subscriptionState.value = _subscriptionState.value.copy(autoCloudBackupEnabled = enabled)
    }

    fun destroy() {
        try {
            billingClient?.endConnection()
        } catch (t: Throwable) {
            // Ignore
        }
    }
}
