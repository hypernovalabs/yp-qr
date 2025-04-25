package com.example.yp_qr

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.content.Context

object ApiService {

    suspend fun openDeviceSession(
        apiKey: String,
        secretKey: String,
        deviceId: String,
        deviceName: String,
        deviceUser: String,
        groupId: String,
        context: Context    // Nuevo parámetro agregado
    ): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("${ApiConfig.BASE_URL}/session/device")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("api-key", apiKey)
            conn.setRequestProperty("secret-key", secretKey)
            conn.doOutput = true

            val body = JSONObject().apply {
                put("device", JSONObject().apply {
                    put("id", deviceId)
                    put("name", deviceName)
                    put("user", deviceUser)
                })
                put("group_id", groupId)
            }
            val fullBody = JSONObject().put("body", body)
            conn.outputStream.use { it.write(fullBody.toString().toByteArray()) }

            val responseCode = conn.responseCode
            val responseBody = try {
                conn.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                conn.errorStream?.bufferedReader()?.readText() ?: "No response body"
            }

            Log.d("ApiService", "[openDeviceSession] Código $responseCode\n$responseBody")

            return@withContext if (responseCode in 200..299) {
                val json = JSONObject(responseBody)
                val bodyObj = json.optJSONObject("body")
                val token = bodyObj?.optString("token", "") ?: ""
                Log.d("ApiService", "🔑 Token recibido: $token")
                token
            } else {
                "Error $responseCode: $responseBody"
            }

        } catch (e: Exception) {
            Log.e("ApiService", "Excepción en openDeviceSession", e)
            "Excepción: ${e.localizedMessage ?: e.message}"
        }
    }

    suspend fun generateQrWithToken(
        endpoint: String,
        token: String,
        apiKey: String,
        secretKey: String,
        inputValue: Double
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        try {
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", token) // Sin "Bearer"
            conn.setRequestProperty("api-key", apiKey)
            conn.setRequestProperty("secret-key", secretKey)
            conn.doOutput = true

            val subTotal = inputValue
            val tax = (subTotal * 0.07 * 100).toInt() / 100.0
            val total = subTotal + tax
            val orderId = "ORD-${System.currentTimeMillis()}"

            val chargeAmount = JSONObject().apply {
                put("sub_total", subTotal)
                put("tax", tax)
                put("tip", 0)
                put("discount", 0)
                put("total", total)
            }
            val body = JSONObject().apply {
                put("charge_amount", chargeAmount)
                put("order_id", orderId)
                put("description", "Pago generado desde app")
            }
            val fullBody = JSONObject().put("body", body)
            conn.outputStream.use { it.write(fullBody.toString().toByteArray()) }

            val responseCode = conn.responseCode
            val response = try {
                conn.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                conn.errorStream?.bufferedReader()?.readText() ?: "No response"
            }

            Log.d("ApiService", "[generateQrWithToken] Código $responseCode\n$response")

            if (responseCode in 200..299) {
                Pair(orderId, response)
            } else {
                Pair("ERROR_$responseCode", response)
            }
        } catch (e: Exception) {
            Log.e("ApiService", "Excepción en generateQrWithToken", e)
            Pair("ERROR_EXCEPTION", e.localizedMessage ?: "Excepción desconocida")
        }
    }

    suspend fun cancelTransaction(
        transactionId: String,
        token: String,
        apiKey: String,
        secretKey: String
    ): String = withContext(Dispatchers.IO) {
        try {
            // Construimos la URL usando el transactionId
            val url = URL("${ApiConfig.BASE_URL}/transaction/$transactionId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", token)
            conn.setRequestProperty("api-key", apiKey)
            conn.setRequestProperty("secret-key", secretKey)
            conn.doOutput = true

            val responseCode = conn.responseCode
            val responseBody = try {
                conn.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                conn.errorStream?.bufferedReader()?.readText() ?: "No response"
            }

            Log.d("ApiService", "[cancelTransaction] Código $responseCode\n$responseBody")

            if (responseCode in 200..299) {
                responseBody
            } else {
                "Error $responseCode: $responseBody"
            }
        } catch (e: Exception) {
            Log.e("ApiService", "Excepción en cancelTransaction", e)
            "Excepción: ${e.localizedMessage ?: e.message}"
        }
    }

    suspend fun getTransactionStatus(
        transactionId: String,  // El transaction_id recibido del creador del QR
        token: String,
        apiKey: String,
        secretKey: String
    ): String = withContext(Dispatchers.IO) {
        try {
            // Construye el endpoint usando el transactionId
            val url = URL("${ApiConfig.BASE_URL}/transaction/$transactionId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", token)
            conn.setRequestProperty("api-key", apiKey)
            conn.setRequestProperty("secret-key", secretKey)
            conn.doOutput = false

            val responseCode = conn.responseCode
            val responseBody = try {
                conn.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                conn.errorStream?.bufferedReader()?.readText() ?: "No response"
            }
            Log.d("ApiService", "[getTransactionStatus] Código $responseCode\n$responseBody")
            if (responseCode in 200..299) {
                responseBody
            } else {
                "Error $responseCode: $responseBody"
            }
        } catch (e: Exception) {
            Log.e("ApiService", "Excepción en getTransactionStatus", e)
            "Excepción: ${e.localizedMessage ?: e.message}"
        }
    }

}
