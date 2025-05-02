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

    // Estado de carga para mostrar LoadingDialog
    val isLoading = mutableStateOf(false)
    private var retryCount = 0
    private val maxRetries = 3
    private val scope = CoroutineScope(Dispatchers.Main)

    fun handle() {
        // 📥 Log de parámetros entrantes
        Timber.d("🟢 TransactionHandler.handle invoked with action=%s, extras=%s", activity.intent.action, activity.intent.extras)

        if (!ApiConfig.isBaseUrlConfigured()) {
            ErrorHandler.showConfigurationError(activity) {
                finishWithCancel()
            }
            return
        }

        // Extraer y validar monto
        val amountValue = getAmountFromIntent()
        if (amountValue <= 0) {
            showInvalidAmountError()
            return
        }

        // Extraer fecha si viene en el Intent
        val dateValue = activity.intent.extras?.getString("date") ?: ""

        // 📥 Log de parámetros procesados
        Timber.d("📥 Parsed parameters: date=%s, amount=%s", dateValue, amountValue)

        scope.launch {
            try {
                isLoading.value = true

                // Abrir sesión y guardar token
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

                // 📥 Log del token de sesión
                Timber.d("⚙️ Session opened, token=%s", token)

                // Construir endpoint de QR
                val qrEndpoint = "${ApiConfig.BASE_URL}/qr/generate/DYN"
                // 📥 Log de parámetros para generar QR
                Timber.d(
                    "⚙️ Generating QR with endpoint=%s, apiKey=%s, secretKey=%s, inputValue=%s",
                    qrEndpoint,
                    config["api_key"],
                    config["secret_key"],
                    amountValue
                )

                val (orderId, responseJson) = ApiService.generateQrWithToken(
                    endpoint = qrEndpoint,
                    token = token,
                    apiKey = config["api_key"] ?: "",
                    secretKey = config["secret_key"] ?: "",
                    inputValue = amountValue
                )

                // Manejar la respuesta y lanzar la Activity
                handleQrResponse(orderId, responseJson, dateValue, amountValue)

            } catch (e: Exception) {
                handleTransactionError(e)
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun handleQrResponse(
        orderId: String,
        responseJson: String,
        dateValue: String,
        amountValue: Double
    ) {
        val json = JSONObject(responseJson)

        if (json.has("body")) {
            val body = json.getJSONObject("body")
            val resultHash = body.optString("hash")
            Timber.d("✅ QR generated: hash=%s", resultHash)

            val dm = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)

            if (displays.isNotEmpty()) {
                Timber.d("🖥️ Displaying QR on secondary screen")
                QrPresentation(activity, resultHash).show()
            } else {
                Timber.d("📱 Displaying QR in QrResultActivity")
                val qrIntent = Intent(activity, QrResultActivity::class.java).apply {
                    putExtra("qrHash", resultHash)
                    putExtra("qrTransactionId", orderId)
                    putExtra("qrDate", dateValue)
                    putExtra("qrAmount", amountValue.toString())
                }
                activity.startActivity(qrIntent)
            }

            onSuccess?.invoke()

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

        if (amount <= 0) {
            val documentData = extras?.getString("DocumentData") ?: ""
            val netAmountRegex = Regex("""<HeaderField Key=\"NetAmount\">([\d.]+)</HeaderField>""")
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
