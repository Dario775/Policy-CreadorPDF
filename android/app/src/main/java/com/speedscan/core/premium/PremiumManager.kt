package com.speedscan.core.premium

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.speedscan.core.billing.BillingManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

import dagger.hilt.android.qualifiers.ApplicationContext

private val Context.premiumDataStore: DataStore<Preferences> by preferencesDataStore(name = "premium_settings")

@Singleton
class PremiumManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billingManager: BillingManager
) {
    private val dataStore = context.premiumDataStore

    companion object {
        private val SCAN_COUNT_KEY = intPreferencesKey("scan_count")
        const val FREE_SCAN_LIMIT = 5
    }

    val isPremium: Flow<Boolean> = billingManager.isPremiumPurchased

    val scanCount: Flow<Int> = dataStore.data.map { preferences ->
        preferences[SCAN_COUNT_KEY] ?: 0
    }

    suspend fun checkIsPremium(): Boolean {
        return billingManager.isPremiumPurchased.value
    }

    suspend fun incrementScanCount() {
        if (!checkIsPremium()) {
            dataStore.edit { preferences ->
                val current = preferences[SCAN_COUNT_KEY] ?: 0
                preferences[SCAN_COUNT_KEY] = current + 1
            }
        }
    }

    suspend fun resetScanCount() {
        dataStore.edit { preferences ->
            preferences[SCAN_COUNT_KEY] = 0
        }
    }

    suspend fun canScan(): Boolean {
        if (checkIsPremium()) return true
        val count = dataStore.data.first()[SCAN_COUNT_KEY] ?: 0
        return count < FREE_SCAN_LIMIT
    }

    fun getProductPrice(): String = billingManager.getProductPrice()
    fun getProductTitle(): String = billingManager.getProductTitle()
    fun getProductDescription(): String = billingManager.getProductDescription()
    fun isBillingReady(): Boolean = billingManager.isReady()
}
