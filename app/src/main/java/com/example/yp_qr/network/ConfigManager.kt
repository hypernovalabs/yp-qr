package com.example.tefbanesco.network

import android.content.Context
import com.example.tefbanesco.storage.LocalStorage

object ConfigManager {

    private var endpoint: String? = null
    private var apiKey: String? = null
    private var secretKey: String? = null
    private var deviceId: String? = null
    private var deviceName: String? = null
    private var deviceUser: String? = null
    private var groupId: String? = null

    // Función para inicializar la configuración
    fun loadConfig(context: Context) {
        val config = LocalStorage.getConfig(context)
        endpoint = config["endpoint"]
        apiKey = config["api_key"]
        secretKey = config["secret_key"]
        deviceId = config["device.id"]
        deviceName = config["device.name"]
        deviceUser = config["device.user"]
        groupId = config["group_id"]
    }

    // Función para verificar si ya hay configuración cargada
    fun isConfigured(): Boolean {
        return !endpoint.isNullOrEmpty() &&
                !apiKey.isNullOrEmpty() &&
                !secretKey.isNullOrEmpty() &&
                !deviceId.isNullOrEmpty() &&
                !deviceName.isNullOrEmpty() &&
                !deviceUser.isNullOrEmpty() &&
                !groupId.isNullOrEmpty()
    }

    // Getters seguros para usar en la app
    fun getEndpoint(): String = endpoint ?: ""
    fun getApiKey(): String = apiKey ?: ""
    fun getSecretKey(): String = secretKey ?: ""
    fun getDeviceId(): String = deviceId ?: ""
    fun getDeviceName(): String = deviceName ?: ""
    fun getDeviceUser(): String = deviceUser ?: ""
    fun getGroupId(): String = groupId ?: ""
}
