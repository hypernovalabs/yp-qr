package com.example.tefbanesco.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

object ApiService {

    private suspend fun makeRequest(
        urlString: String,
        method: String,
        headers: Map<String, String>,
        body: JSONObject? = null
    ): String = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                doInput = true
                doOutput = (body != null)
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")

                headers.forEach { (key, value) ->
                    setRequestProperty(key, value)
                }
            }

            body?.let {
                connection.outputStream.use { os ->
                    os.write(it.toString().toByteArray())
                }
            }

            val responseCode = connection.responseCode
            val responseBody = try {
                connection.inputStream.bufferedReader().use(BufferedReader::readText)
            } catch (e: Exception) {
                connection.errorStream?.bufferedReader()?.use(BufferedReader::readText)
                    ?: "No response body"
            }

            Log.d("ApiService", "🔵 [$method] $urlString")
            Log.d("ApiService", "🛡️ Headers: $headers")
            body?.let { Log.d("ApiService", "📦 Body: ${it.toString(4)}") }
            Log.d("ApiService", "🔵 Code: $responseCode")
            Log.d("ApiService", "📜 Resp: $responseBody")

            if (responseCode in 200..299) {
                responseBody
            } else {
                throw Exception("HTTP $responseCode: $responseBody")
            }
        } catch (e: Exception) {
            Log.e("ApiService", "💥 Error in [$method] $urlString", e)
            throw Exception("Excepción en [$method] $urlString: ${e.localizedMessage}")
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun openDeviceSession(
        apiKey: String,
        secretKey: String,
        deviceId: String,
        deviceName: String,
        deviceUser: String,
        groupId: String,
        context: Context
    ): String {
        val body = JSONObject().apply {
            put("body", JSONObject().apply {
                put("device", JSONObject().apply {
                    put("id", deviceId)
                    put("name", deviceName)
                    put("user", deviceUser)
                })
                put("group_id", groupId)
            })
        }

        val response = makeRequest(
            urlString = "${ApiConfig.BASE_URL}/session/device",
            method = "POST",
            headers = mapOf(
                "Content-Type" to "application/json",
                "api-key" to apiKey,
                "secret-key" to secretKey
            ),
            body = body
        )

        return try {
            val json = JSONObject(response)
            json.optJSONObject("body")?.optString("token") ?: ""
        } catch (e: Exception) {
            Log.e("ApiService", "💥 Error parsing token", e)
            ""
        }
    }

    suspend fun generateQrWithToken(
        endpoint: String,
        token: String,
        apiKey: String,
        secretKey: String,
        inputValue: Double
    ): Pair<String, String> {
        val orderId = "ORD-${System.currentTimeMillis()}"
        val subTotal = inputValue
        val tax = 0.0
        val total = inputValue

        val body = JSONObject().apply {
            put("body", JSONObject().apply {
                put("charge_amount", JSONObject().apply {
                    put("sub_total", subTotal)
                    put("tax", tax)
                    put("tip", 0)
                    put("discount", 0)
                    put("total", total)
                })
                put("order_id", orderId)
                put("description", "Pago generado desde app")
            })
        }

        val response = makeRequest(
            urlString = endpoint,
            method = "POST",
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer $token",
                "api-key" to apiKey,
                "secret-key" to secretKey
            ),
            body = body
        )

        return Pair(orderId, response)
    }

    suspend fun cancelTransaction(
        transactionId: String,
        token: String,
        apiKey: String,
        secretKey: String
    ): String {
        return makeRequest(
            urlString = "${ApiConfig.BASE_URL}/transaction/$transactionId",
            method = "PUT",
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer $token",
                "api-key" to apiKey,
                "secret-key" to secretKey
            )
        )
    }

    suspend fun getTransactionStatus(
        transactionId: String,
        token: String,
        apiKey: String,
        secretKey: String
    ): String {
        Log.d("ApiService", "📡 Llamando getTransactionStatus con:")
        Log.d("ApiService", "🧾 txnId=$transactionId")
        Log.d("ApiService", "🔑 token=$token")
        Log.d("ApiService", "🔐 apiKey=$apiKey")
        Log.d("ApiService", "🔐 secretKey=$secretKey")

        val response = makeRequest(
            urlString = "${ApiConfig.BASE_URL}/transaction/$transactionId",
            method = "GET",
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer $token",
                "api-key" to apiKey,
                "secret-key" to secretKey
            )
        )

        Log.d("ApiService", "📥 Respuesta bruta de getTransactionStatus:\n$response")

        return response
    }

    suspend fun closeDeviceSession(
        token: String,
        apiKey: String,
        secretKey: String
    ): String {
        return makeRequest(
            urlString = "${ApiConfig.BASE_URL}/session/device",
            method = "DELETE",
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer $token",
                "api-key" to apiKey,
                "secret-key" to secretKey
            )
        )
    }
}
