package com.example.yappy.network

//    const val BASE_URL = "https://api-checkout.yappy.cloud/v1"
// const val BASE_URL = " https://api-integrationcheckout-uat.yappycloud.com/v1"


object ApiConfig {

    val BASE_URL: String
        get() {
            val endpoint = ConfigManager.getEndpoint()
            if (endpoint.isBlank()) {
                throw IllegalStateException("Error: BASE_URL no está configurado. Debes obtener la configuración primero.")
            }
            return endpoint
        }

    fun isBaseUrlConfigured(): Boolean {
        val endpoint = ConfigManager.getEndpoint()
        return endpoint.isNotBlank()
    }
}