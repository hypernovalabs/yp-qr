package com.example.tefbanesco.storage

import android.content.Context

object LocalStorage {

    private const val PREFS_NAME = "config_prefs"

    // Guardar la configuración
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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("endpoint", endpoint)
            .putString("api_key", apiKey)
            .putString("secret_key", secretKey)
            .putString("device.id", deviceId)
            .putString("device.name", deviceName)
            .putString("device.user", deviceUser)
            .putString("group_id", groupId)
            .apply()
    }

    // Obtener configuración guardada
    fun getConfig(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return mapOf(
            "endpoint" to (prefs.getString("endpoint", "") ?: ""),
            "api_key" to (prefs.getString("api_key", "") ?: ""),
            "secret_key" to (prefs.getString("secret_key", "") ?: ""),
            "device.id" to (prefs.getString("device.id", "") ?: ""),
            "device.name" to (prefs.getString("device.name", "") ?: ""),
            "device.user" to (prefs.getString("device.user", "") ?: ""),
            "group_id" to (prefs.getString("group_id", "") ?: "")
        )
    }

    // ❗✅ Nueva función para guardar el token
    fun saveToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("device_token", token)
            .apply()
    }

    // Limpiar todo
    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
