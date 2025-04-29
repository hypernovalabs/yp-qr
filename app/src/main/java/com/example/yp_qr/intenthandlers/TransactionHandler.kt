package com.example.yp_qr.intenthandlers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.util.Log
import android.widget.Toast
import com.example.yp_qr.ApiConfig
import com.example.yp_qr.ApiService
import com.example.yp_qr.LocalStorage
import com.example.yp_qr.QrResultActivity
import com.example.yp_qr.presentation.QrPresentation
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class TransactionHandler(private val activity: Activity) {

    private val TAG = "TransactionHandler"

    fun handle() {
        Log.i(TAG, "🟢 Iniciando manejo de transacción…")

        val intent = activity.intent
        val extras = intent.extras

        // 1) Depuración de extras
        Log.d(TAG, "📦 Extras del Intent:")
        extras?.keySet()?.forEach { key ->
            val value = extras.get(key)
            Log.d(TAG, "🔑 $key = \"$value\" (${value?.javaClass?.name})")
        } ?: Log.d(TAG, "❗ El Intent no contiene extras.")

        // 2) Obtener monto
        val amountStr = extras?.getString("Amount")
            ?: intent.getStringExtra("Amount")
            ?: "0"
        var amount = amountStr.toDoubleOrNull() ?: 0.0
        Log.d(TAG, "💰 Monto recibido como string: $amountStr")

        // Si amount inválido, buscar NetAmount dentro de DocumentData
        if (amount <= 0.0) {
            val documentData = extras?.getString("DocumentData") ?: ""
            val netAmountRegex = Regex("""<HeaderField Key="NetAmount">([\d.]+)</HeaderField>""")
            val match = netAmountRegex.find(documentData)
            val netAmount = match?.groups?.get(1)?.value?.toDoubleOrNull()
            if (netAmount != null && netAmount > 0.0) {
                amount = netAmount
                Log.w(TAG, "⚠️ Amount inválido, usando NetAmount del XML: $amount")
            }
        }

        if (amount <= 0.0) {
            Log.e(TAG, "❌ Monto inválido o no encontrado")
            Toast.makeText(activity, "Monto inválido o no recibido", Toast.LENGTH_LONG).show()
            activity.setResult(Activity.RESULT_CANCELED)
            activity.finish()
            return
        }
        Log.i(TAG, "✅ Monto final para transacción: $amount")

        // 3) Cargar configuración segura
        val config: Map<String, String> = runBlocking { LocalStorage.getConfig(activity) }
        Log.d(TAG, "🔐 Configuración obtenida de LocalStorage:")
        config.forEach { (k, v) -> Log.d(TAG, "$k = $v") }

        val apiKey     = config["api_key"]     ?: ""
        val secretKey  = config["secret_key"]  ?: ""
        val deviceId   = config["device.id"]   ?: ""
        val deviceName = config["device.name"] ?: ""
        val deviceUser = config["device.user"] ?: ""
        val groupId    = config["group_id"]    ?: ""

        val qrEndpoint = "${ApiConfig.BASE_URL}/qr/generate/DYN"
        Log.d(TAG, "🌐 Endpoint QR: $qrEndpoint")

        // 4) Ejecutar llamada de red en bloque
        runBlocking {
            try {
                // a) Abrir sesión de dispositivo
                Log.i(TAG, "🔑 Abriendo sesión del dispositivo…")
                val token = ApiService.openDeviceSession(
                    apiKey     = apiKey,
                    secretKey  = secretKey,
                    deviceId   = deviceId,
                    deviceName = deviceName,
                    deviceUser = deviceUser,
                    groupId    = groupId,
                    context    = activity
                )
                LocalStorage.saveToken(activity, token)

                // b) Generar QR
                Log.i(TAG, "📨 Solicitando generación de QR…")
                val (_, responseJson) = ApiService.generateQrWithToken(
                    endpoint   = qrEndpoint,
                    token      = token,
                    apiKey     = apiKey,
                    secretKey  = secretKey,
                    inputValue = amount
                )
                val json = JSONObject(responseJson)

                if (json.has("body")) {
                    val body       = json.getJSONObject("body")
                    val resultHash = body.optString("hash")
                    Log.i(TAG, "✅ QR generado: hash=$resultHash")

                    // 5) Mostrar QR en pantalla secundaria si hay
                    val dm       = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    val displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                    if (displays.isNotEmpty()) {
                        Log.i(TAG, "🖥️ Pantalla secundaria detectada, usando Presentation")
                        val presentation = QrPresentation(activity, resultHash)
                        presentation.show()
                    } else {
                        Log.i(TAG, "📱 Sin pantalla secundaria, abriendo Activity fallback")
                        val qrIntent = Intent(activity, QrResultActivity::class.java).apply {
                            putExtra("qrHash", resultHash)
                        }
                        activity.startActivity(qrIntent)
                    }
                } else {
                    val msg = json.optString("message", "Error desconocido al generar QR")
                    Log.e(TAG, "⚠️ Respuesta sin 'body': $msg")
                    Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e(TAG, "💥 Excepción al generar QR", e)
                Toast.makeText(activity, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                Log.i(TAG, "🛑 Finalizando Activity principal.")
                activity.finish()
            }
        }
    }
}
