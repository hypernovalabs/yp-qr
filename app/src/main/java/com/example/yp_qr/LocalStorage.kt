package com.example.yp_qr

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

val Context.dataStore by preferencesDataStore(name = "config_data")

object LocalStorage {
    private val API_KEY = stringPreferencesKey("api_key")
    private val SECRET_KEY = stringPreferencesKey("secret_key")
    private val DEVICE_ID = stringPreferencesKey("device_id")
    private val DEVICE_NAME = stringPreferencesKey("device_name")
    private val DEVICE_USER = stringPreferencesKey("device_user")
    private val GROUP_ID = stringPreferencesKey("group_id")
    private val TOKEN = stringPreferencesKey("token") // Nueva clave

    suspend fun saveConfig(
        context: Context,
        apiKey: String,
        secretKey: String,
        deviceId: String,
        deviceName: String,
        deviceUser: String,
        groupId: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[API_KEY] = apiKey
            prefs[SECRET_KEY] = secretKey
            prefs[DEVICE_ID] = deviceId
            prefs[DEVICE_NAME] = deviceName
            prefs[DEVICE_USER] = deviceUser
            prefs[GROUP_ID] = groupId
        }
    }

    suspend fun saveToken(context: Context, token: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN] = token
        }
    }

    suspend fun getConfig(context: Context): Map<String, String> {
        val prefs = context.dataStore.data.first()
        return mapOf(
            "api_key"     to (prefs[API_KEY] ?: ""),
            "secret_key"  to (prefs[SECRET_KEY] ?: ""),
            "device.id"   to (prefs[DEVICE_ID] ?: ""),
            "device.name" to (prefs[DEVICE_NAME] ?: ""),
            "device.user" to (prefs[DEVICE_USER] ?: ""),
            "group_id"    to (prefs[GROUP_ID] ?: "")
        )
    }

    suspend fun getToken(context: Context): String? {
        val prefs = context.dataStore.data.first()
        return prefs[TOKEN]
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { it.clear() }
    }
}
