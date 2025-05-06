package com.example.tefbanesco.intenthandlers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import com.example.tefbanesco.errors.ErrorHandler
import com.example.tefbanesco.network.ApiConfig
import com.example.tefbanesco.network.ApiService
import com.example.tefbanesco.presentation.QrPresentation
import com.example.tefbanesco.screens.QrResultActivity
import com.example.tefbanesco.storage.LocalStorage
import com.example.tefbanesco.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

class TransactionHandler(
    private val activity: Activity,
    private val onSuccess: (() -> Unit)? = null
) {
    val isLoading = mutableStateOf(false)
    private var retryCount = 0
    private val maxRetries = 3
    private val scope = CoroutineScope(Dispatchers.Main)

    fun handle() {
        Timber.d("🟢 TransactionHandler.handle invoked with action=%s, extras=%s", activity.intent.action, activity.intent.extras)

        if (!ApiConfig.isBaseUrlConfigured()) {
            ErrorHandler.showConfigurationError(activity) { finishWithCancel() }
            return
        }

        val amountValue = getAmountFromIntent()
        if (amountValue <= 0) {
            showInvalidAmountError()
            return
        }

        val dateValue = activity.intent.extras?.getString("date") ?: ""
        Timber.d("📥 Parsed parameters: date=%s, amount=%s", dateValue, amountValue)

        scope.launch {
            try {
                isLoading.value = true
                val token = openSession(activity)
                LocalStorage.saveToken(activity, token)

                // CAMBIO: generar QR devuelve ahora Triple(localOrderId, yappyTransactionId, responseJson)
                val (localOrderId, yappyTransactionId, responseJson) = generateQr(token, amountValue)

                if (yappyTransactionId.isBlank()) {
                    Timber.e("TransactionHandler: No se pudo obtener el yappyTransactionId de la generación del QR.")
                    ErrorHandler.showErrorDialog(activity, "Error de Comunicación", "No se pudo obtener el identificador de la transacción de Yappy.") {
                        finishWithCancel()
                    }
                    return@launch
                }

                handleQrResponse(localOrderId, yappyTransactionId, responseJson, dateValue, amountValue)
            } catch (e: Exception) {
                handleTransactionError(e)
            } finally {
                isLoading.value = false
            }
        }
    }

    private suspend fun openSession(context: Context): String {
        val config = LocalStorage.getConfig(context)
        return ApiService.openDeviceSession(
            apiKey = config["api_key"] ?: "",
            secretKey = config["secret_key"] ?: "",
            deviceId = config["device.id"] ?: "",
            deviceName = config["device.name"] ?: "",
            deviceUser = config["device.user"] ?: "",
            groupId = config["group_id"] ?: "",
            context = context
        ).also {
            Timber.d("⚙️ Session opened, token=%s", it)
        }
    }

    private suspend fun generateQr(token: String, amountValue: Double): Triple<String, String, String> {
        val config = LocalStorage.getConfig(activity)
        val qrEndpoint = "${ApiConfig.BASE_URL}/qr/generate/DYN"
        Timber.d(
            "⚙️ Generating QR with endpoint=%s, apiKey=%s, secretKey=%s, inputValue=%s",
            qrEndpoint, config["api_key"], config["secret_key"], amountValue
        )
        return ApiService.generateQrWithToken(
            endpoint = qrEndpoint,
            token = token,
            apiKey = config["api_key"] ?: "",
            secretKey = config["secret_key"] ?: "",
            inputValue = amountValue
        )
    }

    private fun handleQrResponse(
        localOrderId: String,
        yappyTransactionId: String,
        responseJson: String,
        dateValue: String,
        amountValue: Double
    ) {
        val json = JSONObject(responseJson)
        if (json.has("body")) {
            val body = json.getJSONObject("body")
            val resultHash = body.optString("hash")
            val yappyDate = body.optString("date", dateValue)
            Timber.d("✅ QR generated for Yappy TxnID: %s, hash: %s", yappyTransactionId, resultHash)

            val dm = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)

            if (displays.isNotEmpty()) {
                Timber.d("🖥️ Displaying QR on secondary screen")
                QrPresentation(activity, resultHash).show()
                activity.finish()
            } else {
                Timber.d("📱 Displaying QR in QrResultActivity")
                val qrIntent = Intent(activity, QrResultActivity::class.java).apply {
                    putExtra("qrHash", resultHash)
                    putExtra("qrTransactionId", yappyTransactionId)
                    putExtra("qrDate", yappyDate)
                    putExtra("qrAmount", amountValue.toString())
                    putExtra("localOrderId", localOrderId)
                }
                activity.startActivity(qrIntent)
                activity.finish()
            }
        } else {
            val msg = json.optString("message", "Error desconocido al generar QR")
            Timber.e("⚠️ Response missing 'body': %s", msg)
            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
            finishWithCancel()
        }
    }

    private fun handleTransactionError(e: Exception) {
        Timber.e(e, "💥 Exception during QR generation")
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
        val amountStr = extras?.getString("Amount")
            ?: activity.intent.getStringExtra("Amount")
            ?: "0"
        var amount = amountStr.toDoubleOrNull() ?: 0.0
        if (amount >= 100) {
            Timber.d("↔️ Ajustando amount de $amount centavos a ${'$'}{amount / 100}")
            amount /= 100.0
        }
        if (amount <= 0) {
            val documentData = extras?.getString("DocumentData") ?: ""
            val netAmountRegex = Regex("""<HeaderField Key=\\"NetAmount\\">([\d.]+)</HeaderField>""")
            val match = netAmountRegex.find(documentData)
            val netAmount = match?.groups?.get(1)?.value?.toDoubleOrNull()
            if (netAmount != null && netAmount > 0) amount = netAmount
        }
        return amount
    }

    private fun showInvalidAmountError() {
        Toast.makeText(activity, "Monto inválido o no recibido", Toast.LENGTH_LONG).show()
        finishWithCancel()
    }
}
