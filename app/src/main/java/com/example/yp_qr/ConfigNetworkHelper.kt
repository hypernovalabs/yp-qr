package com.example.yp_qr

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object ConfigNetworkHelper {

    suspend fun loginAndSaveConfig(context: Context, username: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // URL de tu servidor Flask (ajusta si cambia)
                val url = URL("http://192.168.0.154:5000/get-config")

                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.doInput = true

                // Construir JSON
                val requestBody = JSONObject()
                requestBody.put("username", username)
                requestBody.put("password", password)

                // Enviar datos
                conn.outputStream.use { outputStream ->
                    outputStream.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    // Leer respuesta
                    val response = conn.inputStream.bufferedReader().readText()
                    val responseJson = JSONObject(response)

                    val encryptedData = responseJson.getString("encrypted_data")
                    val encryptionKey = responseJson.getString("encryption_key")

                    // Desencriptar los datos
                    val decryptedJsonString = CryptoHelper.decrypt(encryptedData, encryptionKey)
                    val decryptedJson = JSONObject(decryptedJsonString)

                    // Extraer los campos y guardar
                    val body = decryptedJson.getJSONObject("body")
                    val config = decryptedJson.getJSONObject("config")

                    val deviceId = body.getJSONObject("device").getString("id")
                    val deviceName = body.getJSONObject("device").getString("name")
                    val deviceUser = body.getJSONObject("device").getString("user")
                    val groupId = body.getString("group_id")

                    val endpoint = config.getString("endpoint")
                    val apiKey = config.getString("api-key")
                    val secretKey = config.getString("secret-key")

                    // Guardar todo en LocalStorage
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
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
