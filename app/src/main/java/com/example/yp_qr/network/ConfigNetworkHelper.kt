package com.example.yp_qr.network

import android.content.Context
import android.util.Log
import com.example.yp_qr.storage.CryptoHelper
import com.example.yp_qr.storage.LocalStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ConfigNetworkHelper {

    private const val TAG = "ConfigNetworkHelper"

    suspend fun loginAndSaveConfig(context: Context, username: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("http://192.168.128.95:5000/get-config")

                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.doInput = true

                // Construir JSON
                val requestBody = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }

                // Enviar datos
                conn.outputStream.use { outputStream ->
                    outputStream.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                }

                Log.d(TAG, "🔵 Código de respuesta HTTP: ${conn.responseCode}")

                when (conn.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val response = conn.inputStream.bufferedReader().readText()
                        Log.d(TAG, "🔵 Respuesta recibida: $response")

                        val responseJson = JSONObject(response)

                        val encryptedData = responseJson.getString("encrypted_data")
                        val encryptionKey = responseJson.getString("encryption_key")

                        Log.d(TAG, "🔵 encryptedData: $encryptedData")
                        Log.d(TAG, "🔵 encryptionKey: $encryptionKey")

                        // Desencriptar los datos
                        val decryptedJsonString = CryptoHelper.decrypt(encryptedData, encryptionKey)
                        Log.d(TAG, "🔵 decryptedJsonString: $decryptedJsonString")

                        val decryptedJson = JSONObject(decryptedJsonString)

                        // Extraer y guardar configuración
                        val body = decryptedJson.getJSONObject("body")
                        val config = decryptedJson.getJSONObject("config")

                        val deviceId = body.getJSONObject("device").getString("id")
                        val deviceName = body.getJSONObject("device").getString("name")
                        val deviceUser = body.getJSONObject("device").getString("user")
                        val groupId = body.getString("group_id")

                        val endpoint = config.getString("endpoint")
                        val apiKey = config.getString("api-key")
                        val secretKey = config.getString("secret-key")

                        LocalStorage.saveConfig(
                            context = context,
                            endpoint = endpoint,
                            apiKey = apiKey,
                            secretKey = secretKey,
                            deviceId = deviceId,
                            deviceName = deviceName,
                            deviceUser = deviceUser,
                            groupId = groupId
                        )

                        true
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED -> {
                        Log.e(TAG, "❌ Credenciales inválidas: Usuario o contraseña incorrectos.")
                        false
                    }
                    else -> {
                        Log.e(TAG, "❌ Error inesperado del servidor: Código HTTP ${conn.responseCode}")
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Excepción durante login: ${e.message}", e)
                false
            }
        }
    }
}
