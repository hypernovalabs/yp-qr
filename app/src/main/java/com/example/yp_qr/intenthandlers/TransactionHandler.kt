package com.example.yp_qr.intenthandlers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import com.example.yp_qr.errors.ErrorHandler
import com.example.yp_qr.network.ApiConfig
import com.example.yp_qr.network.ApiService
import com.example.yp_qr.presentation.QrPresentation
import com.example.yp_qr.screens.QrResultActivity
import com.example.yp_qr.storage.LocalStorage
import com.example.yp_qr.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class TransactionHandler(
    private val activity: Activity,
    private val onSuccess: (() -> Unit)? = null // 🔵 Callback para éxito
) {

    private val TAG = "TransactionHandler"
    private var retryCount = 0
    private val maxRetries = 3
    private val scope = CoroutineScope(Dispatchers.Main)

    val isLoading = mutableStateOf(false)

    fun handle() {
        Log.i(TAG, "🟢 Iniciando manejo de transacción… (retry=$retryCount)")

        if (!ApiConfig.isBaseUrlConfigured()) {
            ErrorHandler.showConfigurationError(activity) {
                finishWithCancel()
            }
            return
        }

        val amount = getAmountFromIntent()
        if (amount <= 0) {
            showInvalidAmountError()
            return
        }

        scope.launch {
            try {
                isLoading.value = true

                val config = LocalStorage.getConfig(activity)
                val token = ApiService.openDeviceSession(
                    apiKey = config["api_key"] ?: "",
                    secretKey = config["secret_key"] ?: "",
                    deviceId = config["device.id"] ?: "",
                    deviceName = config["device.name"] ?: "",
                    deviceUser = config["device.user"] ?: "",
                    groupId = config["group_id"] ?: "",
                    context = activity
                )
                LocalStorage.saveToken(activity, token)

                val qrEndpoint = "${ApiConfig.BASE_URL}/qr/generate/DYN"
                val (orderId, responseJson) = ApiService.generateQrWithToken(
                    endpoint = qrEndpoint,
                    token = token,
                    apiKey = config["api_key"] ?: "",
                    secretKey = config["secret_key"] ?: "",
                    inputValue = amount
                )

                handleQrResponse(responseJson)

            } catch (e: Exception) {
                handleTransactionError(e)
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun handleQrResponse(responseJson: String) {
        val json = JSONObject(responseJson)

        if (json.has("body")) {
            val body = json.getJSONObject("body")
            val resultHash = body.optString("hash")
            Log.i(TAG, "✅ QR generado: hash=$resultHash")

            // 🔵 Mostrar en pantalla secundaria si existe
            val dm = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)

            if (displays.isNotEmpty()) {
                Log.i(TAG, "🖥️ Mostrando QR en segunda pantalla")
                val presentation = QrPresentation(activity, resultHash)
                presentation.show()
            } else {
                Log.i(TAG, "📱 Mostrando QR en Activity normal")
                val qrIntent = Intent(activity, QrResultActivity::class.java).apply {
                    putExtra("qrHash", resultHash)
                }
                activity.startActivity(qrIntent)
            }

            // 🔵 Llamar al callback de éxito
            onSuccess?.invoke()

        } else {
            val msg = json.optString("message", "Error desconocido al generar QR")
            Log.e(TAG, "⚠️ Respuesta sin 'body': $msg")
            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
            finishWithCancel()
        }
    }

    private fun handleTransactionError(e: Exception) {
        Log.e(TAG, "💥 Excepción durante la generación del QR", e)
        val errorMessage = ErrorUtils.getErrorMessageFromException(e)

        if (retryCount < maxRetries) {
            retryCount++
            ErrorHandler.showNetworkError(
                activity = activity,
                message = "$errorMessage\n\nIntento $retryCount de $maxRetries.",
                onDismiss = { finishWithCancel() },
                onRetry = { handle() }
            )
        } else {
            ErrorHandler.showNetworkError(
                activity = activity,
                message = "Se ha alcanzado el número máximo de reintentos. Por favor, intenta más tarde.",
                onDismiss = { finishWithCancel() }
            )
        }
    }

    private fun finishWithCancel() {
        activity.setResult(Activity.RESULT_CANCELED)
        activity.finish()
    }

    private fun getAmountFromIntent(): Double {
        val extras = activity.intent.extras
        val amountStr = extras?.getString("Amount") ?: activity.intent.getStringExtra("Amount") ?: "0"
        var amount = amountStr.toDoubleOrNull() ?: 0.0

        if (amount <= 0) {
            val documentData = extras?.getString("DocumentData") ?: ""
            val netAmountRegex = Regex("""<HeaderField Key="NetAmount">([\d.]+)</HeaderField>""")
            val match = netAmountRegex.find(documentData)
            val netAmount = match?.groups?.get(1)?.value?.toDoubleOrNull()
            if (netAmount != null && netAmount > 0) {
                amount = netAmount
            }
        }

        return amount
    }

    private fun showInvalidAmountError() {
        Toast.makeText(activity, "Monto inválido o no recibido", Toast.LENGTH_LONG).show()
        finishWithCancel()
    }
}
