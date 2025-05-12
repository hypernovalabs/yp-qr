// TransactionHandler.kt
package com.example.tefbanesco.intenthandlers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import androidx.compose.runtime.mutableStateOf
import com.example.tefbanesco.errors.ErrorHandler
import com.example.tefbanesco.network.ApiConfig
import com.example.tefbanesco.network.ApiService
import com.example.tefbanesco.presentation.QrPresentation
import com.example.tefbanesco.screens.QrResultActivity
import com.example.tefbanesco.utils.PaymentIntentData
import com.example.tefbanesco.utils.parsePaymentIntent
import com.example.tefbanesco.utils.TransactionResultHelper.finishWithFailureEarly
import com.example.tefbanesco.utils.TransactionResultHelper.prepareSuccessIntent
import com.example.tefbanesco.storage.LocalStorage
import com.example.tefbanesco.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.text.DecimalFormat

/**
 * Manejador principal de la transacción desde HioPOS hasta Yappy.
 */
class TransactionHandler(
    private val activity: Activity
) {
    val isLoading = mutableStateOf(false)
    private var retryCount = 0
    private val maxRetries = 3
    private val scope = CoroutineScope(Dispatchers.Main)

    private var currentLocalOrderId: String? = null
    private var currentYappyTransactionId: String? = null
    private var originalHioPosTransactionId: String? = null
    private var currentPaymentData: PaymentIntentData? = null

    fun handle() {
        Timber.d("🏁 Iniciando handle(). Action: ${activity.intent.action}")
        logIntentExtras(activity.intent)

        currentPaymentData = parsePaymentIntent(activity.intent)
        if (currentPaymentData == null) {
            Timber.e("❌ Fallo al parsear datos del Intent inicial.")
            finishWithFailureEarly(
                activity,
                currentPaymentData,
                errorMessage = "Error interno: no se pudieron leer los datos de la transacción inicial."
            )
            return
        }
        val pd = currentPaymentData!!
        originalHioPosTransactionId = pd.transactionId  // guardamos el ID original como String

        if (!ApiConfig.isBaseUrlConfigured()) {
            ErrorHandler.showConfigurationError(activity) {
                finishWithFailureEarly(activity, pd, errorMessage = "Configuración: módulo de pago no configurado.")
            }
            return
        }

        if (pd.amount <= 0.0) {
            finishWithFailureEarly(activity, pd, errorMessage = "Monto inválido.")
            return
        }

        scope.launch {
            isLoading.value = true
            try {
                val token = openSession(activity)
                if (token.isBlank()) {
                    finishWithFailureEarly(activity, pd, errorMessage = "Fallo al abrir sesión Yappy.")
                    return@launch
                }
                LocalStorage.saveToken(activity, token)

                val (localId, yappyId, response) = generateQr(token, pd.amount)
                currentLocalOrderId = localId
                currentYappyTransactionId = yappyId

                if (yappyId.isBlank()) {
                    val msg = JSONObject(response).optString("message", "Yappy no devolvió ID.")
                    finishWithFailureEarly(
                        activity, pd,
                        errorMessage = msg,
                        localOrderId = localId,
                        yappyTransactionId = yappyId
                    )
                    return@launch
                }

                handleQrResponse(localId, yappyId, response, pd)

            } catch (e: Exception) {
                handleError(e)
            } finally {
                isLoading.value = false
            }
        }
    }

    private suspend fun openSession(context: Context): String = withContext(Dispatchers.IO) {
        val cfg = LocalStorage.getConfig(context)
        if (cfg["api_key"].isNullOrBlank() || cfg["secret_key"].isNullOrBlank() ||
            cfg["device.id"].isNullOrBlank() || cfg["group_id"].isNullOrBlank()
        ) {
            throw IllegalStateException("Credenciales incompletas para sesión Yappy.")
        }
        ApiService.openDeviceSession(
            apiKey     = cfg["api_key"]!!,
            secretKey  = cfg["secret_key"]!!,
            deviceId   = cfg["device.id"]!!,
            deviceName = cfg["device.name"] ?: "DefaultDevice",
            deviceUser = cfg["device.user"] ?: "DefaultUser",
            groupId    = cfg["group_id"]!!,
            context    = context
        )
    }

    private suspend fun generateQr(
        sessionToken: String,
        amountValue: Double
    ): Triple<String, String, String> = withContext(Dispatchers.IO) {
        val cfg = LocalStorage.getConfig(activity)
        val endpoint = "${ApiConfig.BASE_URL}/qr/generate/DYN"
        ApiService.generateQrWithToken(
            endpoint,
            sessionToken,
            cfg["api_key"] ?: "",
            cfg["secret_key"] ?: "",
            amountValue
        )
    }

    private fun handleError(e: Exception) {
        if (retryCount++ < maxRetries) {
            ErrorHandler.showNetworkError(activity, "Reintentando...") { handle() }
        } else {
            finishWithFailureEarly(
                activity,
                currentPaymentData,
                errorMessage = ErrorUtils.getErrorMessageFromException(e),
                localOrderId = currentLocalOrderId,
                yappyTransactionId = currentYappyTransactionId
            )
        }
    }

    private fun handleQrResponse(
        localOrderId: String,
        yappyTransactionId: String,
        responseJson: String,
        pd: PaymentIntentData
    ) {
        val json = JSONObject(responseJson)
        if (!json.has("body")) {
            finishWithFailureEarly(
                activity, pd,
                errorMessage = "Respuesta inválida de Yappy",
                localOrderId = localOrderId,
                yappyTransactionId = yappyTransactionId
            )
            return
        }
        val body = json.getJSONObject("body")
        val hash = body.optString("hash").takeIf { it.isNotBlank() }
            ?: run {
                finishWithFailureEarly(
                    activity, pd,
                    errorMessage = "Hash vacío.",
                    localOrderId = localOrderId,
                    yappyTransactionId = yappyTransactionId
                )
                return
            }

        // Preparamos el Intent para lanzar QrResultActivity
        val qrIntent = Intent(activity, QrResultActivity::class.java).apply {
            // Este helper añade por defecto los extras basicos para la pantalla de resultado
            prepareSuccessIntent(this, pd, localOrderId, yappyTransactionId, hash)

            // ────────────────────────────────────────────────────────────────
            // CAMBIO: Sobrescribimos y pasamos el ID original como String
            putExtra("originalHioPosTransactionIdString", originalHioPosTransactionId.orEmpty())

            // CAMBIO: Añadimos el monto real procesado por Yappy (formateado sin puntos)
            val df = DecimalFormat("0.00")
            val processed = df.format(pd.amount)         // ej. "12.34"
            putExtra("qrAmount", processed)

            // CAMBIO: Añadimos la fecha de Yappy extraída del JSON (o cadena vacía si no existe)
            putExtra("qrDate", body.optString("date", ""))

            // Nota: si 'prepareSuccessIntent' ya mete campos como
            // "TransactionId", "Amount", etc., estos valores se sobrescriben aquí.
            // ────────────────────────────────────────────────────────────────
        }

        showQrPresentation(hash)
        activity.startActivity(qrIntent)
        activity.finish()
    }

    private fun showQrPresentation(hash: String) {
        val dm = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        if (dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).isNotEmpty()) {
            QrPresentation(activity, hash).show()
        }
    }

    private fun logIntentExtras(intent: Intent) {
        intent.extras?.keySet()?.forEach { key ->
            Timber.d("Intent Extra > $key=${intent.extras?.get(key)}")
        }
    }
}
