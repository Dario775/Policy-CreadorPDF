package com.speedscan.core.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(name = "security_settings")

enum class LockType {
    NONE,       // Sin contraseña
    PIN,        // PIN de 4 dígitos
    PATTERN,    // Patrón
    BIOMETRIC   // Solo huella/cara (sin respaldo)
}

@Singleton
class SecurityManager @Inject constructor(
    private val context: Context
) {
    private val dataStore = context.securityDataStore

    companion object {
        private val PIN_KEY = stringPreferencesKey("pin")
        private val PATTERN_KEY = stringPreferencesKey("pattern")
        private val LOCK_TYPE_KEY = stringPreferencesKey("lock_type")
        private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
    }

    val lockType: Flow<LockType> = dataStore.data.map { preferences ->
        val typeString = preferences[LOCK_TYPE_KEY] ?: LockType.NONE.name
        try { LockType.valueOf(typeString) } catch (e: Exception) { LockType.NONE }
    }

    val isBiometricEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[BIOMETRIC_ENABLED_KEY] ?: false
    }

    suspend fun getLockType(): LockType {
        val typeString = dataStore.data.first()[LOCK_TYPE_KEY] ?: LockType.NONE.name
        return try { LockType.valueOf(typeString) } catch (e: Exception) { LockType.NONE }
    }

    suspend fun getBiometricEnabled(): Boolean {
        return dataStore.data.first()[BIOMETRIC_ENABLED_KEY] ?: false
    }

    // Compatibilidad con código anterior
    suspend fun getLockEnabled(): Boolean {
        return getLockType() != LockType.NONE
    }

    private fun hashString(input: String): String {
        val bytes = input.toByteArray()
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    suspend fun setPin(pin: String) {
        dataStore.edit { preferences ->
            preferences[PIN_KEY] = hashString(pin)
            preferences[LOCK_TYPE_KEY] = LockType.PIN.name
        }
    }

    suspend fun setPattern(pattern: String) {
        dataStore.edit { preferences ->
            preferences[PATTERN_KEY] = hashString(pattern)
            preferences[LOCK_TYPE_KEY] = LockType.PATTERN.name
        }
    }

    suspend fun setBiometricOnly() {
        dataStore.edit { preferences ->
            preferences[LOCK_TYPE_KEY] = LockType.BIOMETRIC.name
            preferences[BIOMETRIC_ENABLED_KEY] = true
        }
    }

    suspend fun removeLock() {
        dataStore.edit { preferences ->
            preferences.remove(PIN_KEY)
            preferences.remove(PATTERN_KEY)
            preferences[LOCK_TYPE_KEY] = LockType.NONE.name
            preferences[BIOMETRIC_ENABLED_KEY] = false
        }
    }

    // Alias para compatibilidad
    suspend fun removePin() = removeLock()

    suspend fun verifyPin(pin: String): Boolean {
        val storedPinHash = dataStore.data.first()[PIN_KEY]
        return storedPinHash == hashString(pin)
    }

    suspend fun verifyPattern(pattern: String): Boolean {
        val storedPatternHash = dataStore.data.first()[PATTERN_KEY]
        return storedPatternHash == hashString(pattern)
    }

    suspend fun hasPin(): Boolean {
        return dataStore.data.first()[PIN_KEY] != null
    }

    suspend fun hasPattern(): Boolean {
        return dataStore.data.first()[PATTERN_KEY] != null
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED_KEY] = enabled
        }
    }
}
