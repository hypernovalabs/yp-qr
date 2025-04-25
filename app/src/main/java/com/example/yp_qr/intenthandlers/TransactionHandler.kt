package com.example.yp_qr.intenthandlers

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.yp_qr.ApiConfig
import com.example.yp_qr.ApiService
import com.example.yp_qr.LocalStorage
import com.example.yp_qr.QrResultActivity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class TransactionHandler(private val activity: Activity) {

    private val TAG = "TransactionHandler"

    fun handle() {
        Log.i(TAG, "🟢 Iniciando manejo de transacción...")

        val intent = activity.intent
        val extras = intent.extras

        Log.d(TAG, "📦 Extras del Intent:")
        extras?.keySet()?.forEach { key ->
            val value = extras.get(key)
            Log.d(TAG, "🔑 $key = \"$value\" (${value?.javaClass?.name})")
        } ?: Log.d(TAG, "❗ El Intent no contiene extras.")

        val amountStr = extras?.getString("Amount") ?: intent.getStringExtra("Amount") ?: "0"
        var amount = amountStr.toDoubleOrNull() ?: 0.0
        Log.d(TAG, "💰 Monto recibido como string: $amountStr")

        // Si amount es cero o inválido, buscar NetAmount desde el XML DocumentData
        if (amount <= 0.0) {
            val documentData = extras?.getString("DocumentData") ?: ""
            Log.d(TAG, "📄 XML DocumentData: $documentData")

            val netAmountRegex = Regex("""<HeaderField Key="NetAmount">([\d.]+)</HeaderField>""")
            val match = netAmountRegex.find(documentData)
            val netAmount = match?.groups?.get(1)?.value?.toDoubleOrNull()
            if (netAmount != null && netAmount > 0.0) {
                amount = netAmount
                Log.w(TAG, "⚠️ Monto 'Amount' inválido, usando NetAmount del XML: $amount")
            }
        }

        if (amount <= 0.0) {
            Log.e(TAG, "❌ Monto inválido o no encontrado (ni Amount ni NetAmount válidos)")
            Toast.makeText(activity, "Monto inválido o no recibido", Toast.LENGTH_LONG).show()
            activity.setResult(Activity.RESULT_CANCELED)
            activity.finish()
            return
        }

        Log.i(TAG, "✅ Monto final para transacción: $amount")

        // Obtener configuración segura dentro de runBlocking
        val config: Map<String, String> = runBlocking {
            LocalStorage.getConfig(activity)
        }

        Log.d(TAG, "🔐 Configuración obtenida de LocalStorage:")
        config.forEach { (k, v) -> Log.d(TAG, "$k = $v") }

        val apiKey = config["api_key"] ?: ""
        val secretKey = config["secret_key"] ?: ""
        val deviceId = config["device.id"] ?: ""
        val deviceName = config["device.name"] ?: ""
        val deviceUser = config["device.user"] ?: ""
        val groupId = config["group_id"] ?: ""

        val qrEndpoint = "${ApiConfig.BASE_URL}/qr/generate/DYN"
        Log.d(TAG, "🌐 Endpoint QR: $qrEndpoint")

        runBlocking {
            try {
                Log.i(TAG, "🛠️ Iniciando apertura de sesión del dispositivo...")

                val token = ApiService.openDeviceSession(
                    apiKey = apiKey,
                    secretKey = secretKey,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    deviceUser = deviceUser,
                    groupId = groupId,
                    context = activity
                )
                Log.d(TAG, "🔑 Token recibido: $token")
                LocalStorage.saveToken(activity, token)

                Log.i(TAG, "📨 Enviando solicitud para generar QR...")
                val (_, responseJson) = ApiService.generateQrWithToken(
                    endpoint = qrEndpoint,
                    token = token,
                    apiKey = apiKey,
                    secretKey = secretKey,
                    inputValue = amount
                )

                Log.d(TAG, "📩 Respuesta JSON del QR: $responseJson")
                val json = JSONObject(responseJson)

                if (json.has("body")) {
                    val body = json.getJSONObject("body")
                    val resultDate = body.optString("date")
                    val transactionId = body.optString("transactionId")
                    val resultHash = body.optString("hash")

                    Log.i(TAG, "✅ QR generado correctamente:")
                    Log.d(TAG, "📅 Fecha: $resultDate")
                    Log.d(TAG, "🆔 TransactionId: $transactionId")
                    Log.d(TAG, "🔐 Hash: $resultHash")

                    val qrIntent = Intent(activity, QrResultActivity::class.java).apply {
                        putExtra("qrDate", resultDate)
                        putExtra("qrTransactionId", transactionId)
                        putExtra("qrHash", resultHash)
                    }

                    Log.i(TAG, "🧭 Redirigiendo a QrResultActivity...")
                    activity.startActivity(qrIntent)
                } else {
                    val msg = json.optString("message", "Error desconocido al generar QR")
                    Log.e(TAG, "⚠️ Respuesta sin 'body': $msg")
                    Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "💥 Excepción al generar QR", e)
                Toast.makeText(activity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                Log.i(TAG, "🛑 Finalizando actividad.")
                activity.finish()
            }
        }
    }
}
