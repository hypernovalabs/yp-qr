// File: TransactionHandler.kt
package com.example.tefbanesco.intenthandlers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import com.example.tefbanesco.errors.ErrorHandler
import com.example.tefbanesco.network.ApiConfig
import com.example.tefbanesco.network.ApiService
import com.example.tefbanesco.storage.LocalStorage
import com.example.tefbanesco.utils.PaymentIntentData // Importar desde utils
import com.example.tefbanesco.utils.buildTransactionDataString
import com.example.tefbanesco.utils.bundleExtrasToString
import com.example.tefbanesco.utils.parsePaymentIntent // Importar desde utils
import com.example.tefbanesco.screens.QrResultActivity
import com.example.tefbanesco.screens.TefTransactionResults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

class TransactionHandler(
    private val activity: Activity
) {
    val isLoading = mutableStateOf(false)
    private var retryCount = 0
    private val maxRetries = 3
    private val scope = CoroutineScope(Dispatchers.Main)

    private var currentLocalOrderId: String? = null
    private var currentYappyTransactionId: String? = null
    private var originalHioPosTransactionId: Int = 0
    private var currentPaymentData: PaymentIntentData? = null

    fun handle() {
        Timber.d("🏁 Iniciando TransactionHandler.handle(), action=${activity.intent.action}")
        logIntentExtras(activity.intent, "TransactionHandler - Intent Inicial de Hiopos")

        currentPaymentData = parsePaymentIntent(activity.intent)
        if (currentPaymentData == null) {
            Timber.e("❌ TransactionHandler: Fallo al parsear datos del Intent inicial.")
            finishWithFailureEarly("Error interno: No se pudieron leer los datos de la transacción inicial.")
            return
        }
        val pd = currentPaymentData!!
        originalHioPosTransactionId = pd.posTransactionId

        Timber.i("📄 TransactionHandler: Datos parseados -> HioPosTxnId=${pd.posTransactionId}, Action=${pd.originalIntentAction}, Amount=${pd.totalAmount}, Currency=${pd.currencyISO}, Tip=${pd.tipAmount}, Tax=${pd.taxAmount}, ShopDataLen=${pd.shopDataXml?.length}, PrinterCols=${pd.receiptPrinterColumns}, OriginalTxnType=${pd.transactionType}")

        if (!ApiConfig.isBaseUrlConfigured()) {
            Timber.e("❌ TransactionHandler: BASE_URL API no configurada.")
            ErrorHandler.showConfigurationError(activity) {
                finishWithFailureEarly("Configuración: Módulo de pago Yappy no configurado.")
            }
            return
        }

        if (pd.totalAmount <= 0) {
            Timber.e("❌ TransactionHandler: Monto inválido: ${pd.totalAmount}")
            showInvalidAmountError()
            return
        }

        Timber.d("🚀 TransactionHandler: Iniciando flujo para HioPosTxnId: $originalHioPosTransactionId")
        scope.launch {
            isLoading.value = true
            try {
                Timber.d("⏳ TransactionHandler: Abriendo sesión Yappy...")
                val sessionToken = openSession(activity)
                LocalStorage.saveToken(activity, sessionToken)
                Timber.i("🔑 TransactionHandler: Token sesión Yappy obtenido y guardado.")

                Timber.d("⏳ TransactionHandler: Generando QR Yappy, monto: ${pd.totalAmount}...")
                val (localOrderId, yappyTransactionId, responseJson) = generateQr(sessionToken, pd.totalAmount)
                currentLocalOrderId = localOrderId
                currentYappyTransactionId = yappyTransactionId

                if (yappyTransactionId.isBlank()) {
                    val errorMsg = try { JSONObject(responseJson).optString("message", "Yappy no devolvió ID de transacción.") } catch (e: Exception) { "Respuesta Yappy inválida (generar QR)." }
                    Timber.e("❌ TransactionHandler: Error generando QR: $errorMsg. Respuesta Yappy: $responseJson")
                    throw Exception(errorMsg)
                }
                Timber.i("🎉 TransactionHandler: QR generado. YappyTxnId: $yappyTransactionId, LocalOrderId: $localOrderId.")
                handleQrResponse(localOrderId, yappyTransactionId, responseJson, pd)

            } catch (e: Exception) {
                Timber.e(e, "💥 TransactionHandler: Excepción general en flujo principal.")
                handleTransactionError(e)
            } finally {
                isLoading.value = false
                Timber.d("🏁 TransactionHandler: Fin bloque asíncrono.")
            }
        }
    }

    private suspend fun openSession(context: Context): String {
        val config = LocalStorage.getConfig(context)
        if (config["api_key"].isNullOrBlank() || config["secret_key"].isNullOrBlank() ||
            config["device.id"].isNullOrBlank() || config["group_id"].isNullOrBlank()) {
            Timber.e("TransactionHandler.openSession: Credenciales de configuración Yappy incompletas.")
            throw IllegalStateException("Configuración de credenciales Yappy incompleta. Por favor, configure el módulo.")
        }
        Timber.d("TransactionHandler.openSession: Solicitando token Yappy...")
        val token = ApiService.openDeviceSession(
            apiKey = config["api_key"]!!,
            secretKey = config["secret_key"]!!,
            deviceId = config["device.id"]!!,
            deviceName = config["device.name"] ?: "DefaultDeviceYappy",
            deviceUser = config["device.user"] ?: "DefaultUserYappy",
            groupId = config["group_id"]!!,
            context = context
        )
        if (token.isBlank()) {
            Timber.e("TransactionHandler.openSession: Token de sesión Yappy vacío recibido de ApiService.")
            throw Exception("Fallo al obtener token de sesión de Yappy.")
        }
        Timber.d("TransactionHandler.openSession: Token Yappy (parcial): ${token.take(15)}...")
        return token
    }

    private suspend fun generateQr(sessionToken: String, amountValue: Double): Triple<String, String, String> {
        val config = LocalStorage.getConfig(activity)
        val qrEndpoint = "${ApiConfig.BASE_URL}/qr/generate/DYN"
        Timber.d("TransactionHandler.generateQr: Endpoint: $qrEndpoint, Monto: %.2f", amountValue)
        if (config["api_key"].isNullOrBlank() || config["secret_key"].isNullOrBlank()){
            throw IllegalStateException("API Key o Secret Key no configurados para generar QR.")
        }
        return ApiService.generateQrWithToken(qrEndpoint, sessionToken, config["api_key"]!!, config["secret_key"]!!, amountValue)
    }

    private fun handleQrResponse(
        localOrderIdFromQrGeneration: String,
        yappyTransactionIdFromQrGeneration: String,
        responseJsonFromQrGeneration: String,
        paymentDataFromHioPos: PaymentIntentData
    ) {
        Timber.d("TransactionHandler.handleQrResponse: Procesando respuesta QR. YappyTxnId: $yappyTransactionIdFromQrGeneration")
        val json = try { JSONObject(responseJsonFromQrGeneration) } catch (e: Exception) {
            Timber.e(e, "❌ TransactionHandler.handleQrResponse: Respuesta QR no es JSON: $responseJsonFromQrGeneration")
            throw Exception("Error comunicación (respuesta QR Yappy inválida).")
        }

        if (json.has("body")) {
            val body = json.getJSONObject("body")
            val resultQrHash = body.optString("hash")
            val yappyDateFromQr = body.optString("date")

            if (resultQrHash.isBlank()) {
                Timber.e("❌ TransactionHandler.handleQrResponse: Hash QR vacío. Body Yappy: $body")
                throw Exception("Yappy no devolvió hash QR.")
            }
            Timber.i("✅ TransactionHandler.handleQrResponse: QR listo. Hash: ${resultQrHash.take(10)}..., YappyDate: $yappyDateFromQr")

            Timber.d("🚀 TransactionHandler.handleQrResponse: Preparando Intent para QrResultActivity...")
            val qrIntent = Intent(activity, QrResultActivity::class.java).apply {
                putExtra("qrHash", resultQrHash)
                putExtra("yappyTransactionIdFromQr", yappyTransactionIdFromQrGeneration)
                putExtra("localOrderIdFromQr", localOrderIdFromQrGeneration)
                putExtra("yappyDateFromQr", yappyDateFromQr)
                putExtra("yappyAmountFromQr", paymentDataFromHioPos.totalAmount.toString())

                putExtra("originalHioPosTransactionId", paymentDataFromHioPos.posTransactionId)
                putExtra("originalHioPosAction", paymentDataFromHioPos.originalIntentAction)
                putExtra("shopDataXmlFromHioPos", paymentDataFromHioPos.shopDataXml ?: "")
                putExtra("receiptPrinterColumnsFromHioPos", paymentDataFromHioPos.receiptPrinterColumns)
                putExtra("tipAmountFromHioPos", (paymentDataFromHioPos.tipAmount * 100).toInt().toString())
                putExtra("taxAmountFromHioPos", (paymentDataFromHioPos.taxAmount * 100).toInt().toString())
                putExtra("currencyISOFromHioPos", paymentDataFromHioPos.currencyISO ?: "")
                putExtra("transactionTypeFromHioPos", paymentDataFromHioPos.transactionType ?: TefTransactionResults.TYPE_SALE)
            }
            logIntentExtras(qrIntent, "TransactionHandler - Intent para QrResultActivity")

            Timber.d("📱 TransactionHandler.handleQrResponse: Iniciando QrResultActivity, finalizando esta Activity.")
            activity.startActivity(qrIntent)
            activity.finish()
        } else {
            val msg = json.optString("message", "Respuesta Yappy sin 'body' al generar QR.")
            Timber.e("❌ TransactionHandler.handleQrResponse: $msg JSON: $responseJsonFromQrGeneration")
            throw Exception(msg)
        }
    }

    private fun handleTransactionError(e: Exception) {
        Timber.w(e, "TransactionHandler.handleTransactionError: HioPosTxnId: $originalHioPosTransactionId. Reintentos: $retryCount")
        val errorMessage = e.message ?: "Error desconocido durante la transacción."

        if (retryCount < maxRetries) {
            retryCount++
            Timber.i("🔄 TransactionHandler.handleTransactionError: Reintento ${retryCount}/${maxRetries}. Error: $errorMessage")
            ErrorHandler.showNetworkError(activity, "$errorMessage\n¿Desea reintentar? (${retryCount}/${maxRetries})",
                onDismiss = {
                    Timber.w(" TransactionHandler.handleTransactionError: Usuario canceló reintento.")
                    finishWithFailureEarly(errorMessage)
                },
                onRetry = {
                    Timber.d(" TransactionHandler.handleTransactionError: Usuario reintenta.")
                    scope.launch {
                        handle()
                    }
                }
            )
        } else {
            Timber.e(" TransactionHandler.handleTransactionError: Max reintentos ($maxRetries). Error final: $errorMessage")
            ErrorHandler.showNetworkError(activity, "Máximos reintentos alcanzados. Transacción no completada.\nError: $errorMessage",
                onDismiss = {
                    Timber.w("TransactionHandler.handleTransactionError: Error final confirmado por usuario.")
                    finishWithFailureEarly("Máximos reintentos: $errorMessage")
                }
            )
        }
    }

    private fun finishWithFailureEarly(
        errorMessage: String
    ) {
        Timber.w("‼️ TransactionHandler.finishWithFailureEarly: HioPosTxnId=$originalHioPosTransactionId, Error='$errorMessage', LocalOrderId='$currentLocalOrderId', YappyTxnId='$currentYappyTransactionId'")
        val dataString = buildTransactionDataString(currentYappyTransactionId, currentLocalOrderId)

        val resultIntent = Intent().apply {
            currentPaymentData?.originalIntentAction?.let { action = it }

            putExtra("TransactionResult", TefTransactionResults.RESULT_FAILED)
            putExtra("TransactionType", currentPaymentData?.transactionType ?: TefTransactionResults.TYPE_SALE)
            putExtra("ErrorMessage", errorMessage)
            if (originalHioPosTransactionId != 0) {
                putExtra("TransactionId", originalHioPosTransactionId)
            }
            putExtra("TransactionData", dataString)

            currentPaymentData?.let { pd ->
                putExtra("Amount", (pd.totalAmount * 100).toInt().toString())
                putExtra("TipAmount", (pd.tipAmount * 100).toInt().toString())
                putExtra("TaxAmount", (pd.taxAmount * 100).toInt().toString())
                putExtra("CurrencyISO", pd.currencyISO ?: "")
            }
        }
        Timber.i("  📤 TransactionHandler.finishWithFailureEarly: Enviando error a Hiopos: ${bundleExtrasToString(resultIntent.extras ?: Bundle())}")
        activity.setResult(Activity.RESULT_OK, resultIntent)
        activity.finish()
    }

    private fun showInvalidAmountError() {
        val errorMsg = "Monto de transacción inválido."
        Timber.e("❌ TransactionHandler.showInvalidAmountError: $errorMsg. HioPosTxnId: $originalHioPosTransactionId")
        Toast.makeText(activity, errorMsg, Toast.LENGTH_LONG).show()
        finishWithFailureEarly(errorMsg)
    }

    private fun logIntentExtras(intent: Intent, logTagPrefix: String) {
        val extras = intent.extras
        if (extras == null || extras.isEmpty) {
            Timber.d("$logTagPrefix: No hay extras o están vacíos.")
            return
        }
        Timber.d("$logTagPrefix: Contenido Extras -> ${bundleExtrasToString(extras)}")
    }
}