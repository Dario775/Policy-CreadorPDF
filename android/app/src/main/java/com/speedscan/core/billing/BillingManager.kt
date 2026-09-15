package com.speedscan.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) : PurchasesUpdatedListener {

    companion object {
        const val PREMIUM_PRODUCT_ID = "premium_upgrade"
    }

    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null

    private val _isPremiumPurchased = MutableStateFlow(false)
    val isPremiumPurchased: StateFlow<Boolean> = _isPremiumPurchased

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Callback para notificar resultados de compra
    var onPurchaseResult: ((Boolean, String) -> Unit)? = null

    fun initialize() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()

        startConnection()
    }

    private fun startConnection() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails()
                    checkExistingPurchases()
                } else {
                    // Error en la conexión inicial
                    onPurchaseResult?.invoke(false, "No se pudo conectar con Google Play: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK ||
                result.productDetailsList.isEmpty()
            ) {
                android.util.Log.e("BillingManager", "Producto no encontrado: $PREMIUM_PRODUCT_ID")
                onPurchaseResult?.invoke(false, "La compra Pro todavía no está disponible en Google Play.")
            } else {
                productDetails = result.productDetailsList.firstOrNull()
                android.util.Log.d("BillingManager", "Producto cargado: ${productDetails?.title}")
            }
        }
    }

    private fun checkExistingPurchases(onComplete: (() -> Unit)? = null) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient?.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _isPremiumPurchased.value = false
                purchases.forEach { purchase ->
                    if (purchase.products.contains(PREMIUM_PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    ) {
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                        _isPremiumPurchased.value = true
                    }
                }
            }
            onComplete?.invoke()
        }
    }

    fun launchPurchaseFlow(activity: Activity) {
        val details = productDetails
        if (details == null) {
            onPurchaseResult?.invoke(false, "Producto no disponible. Intenta más tarde.")
            return
        }

        _isLoading.value = true

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = billingClient?.launchBillingFlow(activity, billingFlowParams)
        if (result?.responseCode != BillingClient.BillingResponseCode.OK) {
            _isLoading.value = false
            onPurchaseResult?.invoke(false, "No se pudo abrir Google Play: ${result?.debugMessage.orEmpty()}")
        }
    }

    fun restorePurchases() {
        val client = billingClient
        if (client?.isReady != true) {
            onPurchaseResult?.invoke(false, "Google Play no está listo. Intenta nuevamente.")
            startConnection()
            return
        }

        _isLoading.value = true
        checkExistingPurchases {
            _isLoading.value = false
            if (_isPremiumPurchased.value) {
                onPurchaseResult?.invoke(true, "Compra restaurada correctamente.")
            } else {
                onPurchaseResult?.invoke(false, "No se encontró una compra Pro activa.")
            }
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        _isLoading.value = false

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    handlePurchase(purchase)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                onPurchaseResult?.invoke(false, "Compra cancelada")
            }
            else -> {
                onPurchaseResult?.invoke(false, "Error en la compra: ${billingResult.debugMessage}")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Verificar que la compra incluye nuestro producto
            if (purchase.products.contains(PREMIUM_PRODUCT_ID)) {
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
                _isPremiumPurchased.value = true
                onPurchaseResult?.invoke(true, "¡Compra exitosa! Disfruta de Pro.")
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            onPurchaseResult?.invoke(false, "Pago pendiente de confirmación")
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient?.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                android.util.Log.e("BillingManager", "No se pudo confirmar la compra: ${billingResult.debugMessage}")
            }
        }
    }

    fun getProductPrice(): String {
        return productDetails?.oneTimePurchaseOfferDetails?.formattedPrice ?: "---"
    }

    fun getProductTitle(): String {
        return productDetails?.title ?: "Premium"
    }

    fun getProductDescription(): String {
        return productDetails?.description ?: "Desbloquea todas las funciones"
    }

    fun isReady(): Boolean {
        return billingClient?.isReady == true && productDetails != null
    }

    fun disconnect() {
        billingClient?.endConnection()
        billingClient = null
    }
}
