package com.example.tefbanesco.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import timber.log.Timber

/**
 * Gestiona el almacenamiento seguro de la configuración y credenciales de la aplicación.
 * Utiliza EncryptedSharedPreferences para cifrar tanto las claves como los valores,
 * proporcionando una capa adicional de seguridad para información sensible.
 */
object LocalStorage {

    private const val PREFS_NAME = "yappy_secure_prefs"

    /**
     * Obtiene una instancia de EncryptedSharedPreferences para almacenamiento seguro.
     * Si hay algún error, fallback a SharedPreferences normal con un log de advertencia.
     */
    private fun getSecurePrefs(context: Context): SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Si hay un error con EncryptedSharedPreferences, usar SharedPreferences normal como fallback
            Timber.e(e, "[YAPPY] Error inicializando EncryptedSharedPreferences, usando SharedPreferences normal")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Guarda la configuración con parámetros individuales.
     * Los datos se almacenan de forma segura utilizando EncryptedSharedPreferences.
     */
    fun saveConfig(
        context: Context,
        endpoint: String,
        apiKey: String,
        secretKey: String,
        deviceId: String,
        deviceName: String,
        deviceUser: String,
        groupId: String
    ) {
        try {
            val prefs = getSecurePrefs(context)
            prefs.edit()
                .putString("endpoint", endpoint)
                .putString("api_key", apiKey)
                .putString("secret_key", secretKey)
                .putString("device.id", deviceId)
                .putString("device.name", deviceName)
                .putString("device.user", deviceUser)
                .putString("group_id", groupId)
                .apply()

            Timber.d("[YAPPY] Configuración guardada de forma segura (parámetros individuales)")
        } catch (e: Exception) {
            Timber.e(e, "[YAPPY] Error al guardar configuración")
        }
    }

    /**
     * Guarda la configuración a partir de un mapa de valores.
     * Los datos se almacenan de forma segura utilizando EncryptedSharedPreferences.
     */
    fun saveConfig(context: Context, config: Map<String, String>) {
        try {
            val prefs = getSecurePrefs(context)
            val editor = prefs.edit()

            // Guardar cada entrada del mapa
            for ((key, value) in config) {
                editor.putString(key, value)
            }

            // Aplicar cambios
            editor.apply()

            Timber.d("[YAPPY] Configuración guardada de forma segura: ${config.keys}")
        } catch (e: Exception) {
            Timber.e(e, "[YAPPY] Error al guardar configuración")
        }
    }

    /**
     * Obtiene la configuración guardada, incluyendo el token.
     * Los datos se recuperan de forma segura desde EncryptedSharedPreferences.
     */
    fun getConfig(context: Context): Map<String, String> {
        try {
            val prefs = getSecurePrefs(context)
            val config = mapOf(
                "endpoint"     to (prefs.getString("endpoint", "")     ?: ""),
                "api_key"      to (prefs.getString("api_key", "")      ?: ""),
                "secret_key"   to (prefs.getString("secret_key", "")   ?: ""),
                "device.id"    to (prefs.getString("device.id", "")    ?: ""),
                "device.name"  to (prefs.getString("device.name", "")  ?: ""),
                "device.user"  to (prefs.getString("device.user", "")  ?: ""),
                "group_id"     to (prefs.getString("group_id", "")     ?: ""),
                "device_token" to (prefs.getString("device_token", "") ?: "")
            )

            // Log para depuración (sin mostrar valores sensibles)
            Timber.d("[YAPPY] Configuración recuperada: ${config.keys}")

            return config
        } catch (e: Exception) {
            Timber.e(e, "[YAPPY] Error al obtener configuración")
            return emptyMap() // Devolver mapa vacío en caso de error
        }
    }

    /**
     * Guarda el token de sesión de forma segura.
     */
    fun saveToken(context: Context, token: String) {
        try {
            val prefs = getSecurePrefs(context)
            prefs.edit()
                .putString("device_token", token)
                .apply()

            Timber.d("[YAPPY] Token de sesión guardado de forma segura")
        } catch (e: Exception) {
            Timber.e(e, "[YAPPY] Error al guardar token de sesión")
        }
    }

    /**
     * Limpia toda la configuración, incluyendo el token.
     */
    fun clear(context: Context) {
        try {
            val prefs = getSecurePrefs(context)
            prefs.edit().clear().apply()

            Timber.d("[YAPPY] Configuración limpiada correctamente")
        } catch (e: Exception) {
            Timber.e(e, "[YAPPY] Error al limpiar configuración")
        }
    }
}