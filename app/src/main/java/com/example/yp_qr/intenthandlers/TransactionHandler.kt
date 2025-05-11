//// package com.example.yappy.intenthandlers // AJUSTA TU PAQUETE REAL
//package com.example.tefbanesco.intenthandlers
//
//import android.app.Activity
//import android.content.Context
//import android.content.Intent
//import android.hardware.display.DisplayManager
//import android.os.Bundle // Necesario para el helper extrasToString
//import android.widget.Toast
//import androidx.compose.runtime.mutableStateOf
//import com.example.tefbanesco.errors.ErrorHandler
//import com.example.tefbanesco.network.ApiConfig
//import com.example.tefbanesco.network.ApiService
//import com.example.tefbanesco.presentation.QrPresentation
//import com.example.tefbanesco.screens.QrResultActivity
//import com.example.tefbanesco.screens.TefTransactionResults // Asumiendo que está en screens
//import com.example.tefbanesco.storage.LocalStorage
//import com.example.tefbanesco.utils.ErrorUtils
//import com.example.tefbanesco.utils.PaymentIntentData
//import com.example.tefbanesco.utils.parsePaymentIntent
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import org.json.JSONObject
//import timber.log.Timber
//
//class TransactionHandler(
//    private val activity: Activity,
//    private val onSuccess: (() -> Unit)? = null // No se usa, considerar eliminar
//) {
//    val isLoading = mutableStateOf(false)
//    private var retryCount = 0
//    private val maxRetries = 3
//    private val scope = CoroutineScope(Dispatchers.Main)
//
//    private var currentLocalOrderId: String? = null
//    private var currentYappyTransactionId: String? = null
//    private var originalHioPosTransactionId: Int = 0
//    private var currentPaymentData: PaymentIntentData? = null
//
//    fun handle() {
//        Timber.d("🏁 TransactionHandler: Iniciando handle(). Action: ${activity.intent.action}")
//        logIntentExtras(activity.intent, "TransactionHandler - Intent Inicial de Hiopos")
//
//        currentPaymentData = parsePaymentIntent(activity.intent)
//        if (currentPaymentData == null) {
//            Timber.e("❌ TransactionHandler: Fallo al parsear datos del Intent inicial.")
//            finishWithFailureEarly("Error interno: No se pudieron leer los datos de la transacción inicial.")
//            return
//        }
//        val paymentData = currentPaymentData!!
//
//        originalHioPosTransactionId = paymentData.posTransactionId
//        Timber.i("📄 TransactionHandler: Datos parseados -> HioPosTxnId=${originalHioPosTransactionId}, Amount=${paymentData.totalAmount}, Currency=${paymentData.currencyISO}, Tip=${paymentData.tipAmount}, Tax=${paymentData.taxAmount}, ShopDataLen=${paymentData.shopDataXml?.length}, PrinterCols=${paymentData.receiptPrinterColumns}, OriginalTxnType=${paymentData.transactionType}")
//
//        if (!ApiConfig.isBaseUrlConfigured()) {
//            Timber.e("❌ TransactionHandler: BASE_URL API no configurada.")
//            ErrorHandler.showConfigurationError(activity) {
//                finishWithFailureEarly("Configuración: Módulo de pago Yappy no configurado.")
//            }
//            return
//        }
//
//        if (paymentData.totalAmount <= 0) {
//            Timber.e("❌ TransactionHandler: Monto inválido: ${paymentData.totalAmount}")
//            showInvalidAmountError()
//            return
//        }
//
//        Timber.d("🚀 TransactionHandler: Iniciando flujo para HioPosTxnId: $originalHioPosTransactionId")
//        scope.launch {
//            try {
//                isLoading.value = true
//                Timber.d("⏳ TransactionHandler: Abriendo sesión Yappy...")
//                val sessionToken = openSession(activity)
//                if (sessionToken.isBlank()) {
//                    Timber.e("❌ TransactionHandler: Token de sesión Yappy vacío.")
//                    ErrorHandler.showErrorDialog(activity, "Error Autenticación Yappy", "Fallo al iniciar sesión con Yappy.") {
//                        finishWithFailureEarly("Fallo al abrir sesión Yappy.")
//                    }
//                    return@launch
//                }
//                LocalStorage.saveToken(activity, sessionToken)
//                Timber.i("🔑 TransactionHandler: Token sesión Yappy obtenido.")
//
//                Timber.d("⏳ TransactionHandler: Generando QR Yappy, monto: ${paymentData.totalAmount}...")
//                val (localOrderId, yappyTransactionId, responseJson) = generateQr(sessionToken, paymentData.totalAmount)
//                currentLocalOrderId = localOrderId
//                currentYappyTransactionId = yappyTransactionId
//
//                if (yappyTransactionId.isBlank()) {
//                    val errorMsg = try { JSONObject(responseJson).optString("message", "Yappy no devolvió ID de transacción.") } catch (e: Exception) { "Respuesta Yappy inválida (generar QR)." }
//                    Timber.e("❌ TransactionHandler: Error generando QR: $errorMsg. Respuesta Yappy: $responseJson")
//                    ErrorHandler.showErrorDialog(activity, "Error Comunicación Yappy", errorMsg) {
//                        finishWithFailureEarly(errorMsg, localOrderId)
//                    }
//                    return@launch
//                }
//                Timber.i("🎉 TransactionHandler: QR generado. YappyTxnId: $yappyTransactionId, LocalOrderId: $localOrderId.")
//                handleQrResponse(localOrderId, yappyTransactionId, responseJson, paymentData)
//            } catch (e: Exception) {
//                Timber.e(e, "💥 TransactionHandler: Excepción general en flujo.")
//                handleTransactionError(e)
//            } finally {
//                isLoading.value = false
//                Timber.d("🏁 TransactionHandler: Fin bloque asíncrono.")
//            }
//        }
//    }
//
//    private suspend fun openSession(context: Context): String {
//        val config = LocalStorage.getConfig(context)
//        if (config["api_key"].isNullOrBlank() || config["secret_key"].isNullOrBlank() ||
//            config["device.id"].isNullOrBlank() || config["group_id"].isNullOrBlank()) {
//            Timber.e("TransactionHandler.openSession: Credenciales config incompletas.")
//            throw IllegalStateException("Configuración credenciales Yappy incompleta.")
//        }
//        Timber.d("TransactionHandler.openSession: Solicitando token Yappy...")
//        return ApiService.openDeviceSession(
//            apiKey     = config["api_key"]!!, secretKey  = config["secret_key"]!!,
//            deviceId   = config["device.id"]!!, deviceName = config["device.name"] ?: "DefaultDeviceYappy",
//            deviceUser = config["device.user"] ?: "DefaultUserYappy", groupId = config["group_id"]!!,
//            context    = context
//        ).also {
//            if (it.isNotBlank()) Timber.d("TransactionHandler.openSession: Token Yappy (parcial): ${it.take(15)}...")
//            else Timber.w("TransactionHandler.openSession: Token Yappy vacío de ApiService.")
//        }
//    }
//
//    private suspend fun generateQr(sessionToken: String, amountValue: Double): Triple<String, String, String> {
//        val config = LocalStorage.getConfig(activity)
//        val qrEndpoint = "${ApiConfig.BASE_URL}/qr/generate/DYN"
//        Timber.d("TransactionHandler.generateQr: Endpoint: $qrEndpoint, Monto: %.2f", amountValue)
//        return ApiService.generateQrWithToken(qrEndpoint, sessionToken, config["api_key"] ?: "", config["secret_key"] ?: "", amountValue)
//    }
//
//    private fun handleQrResponse(
//        localOrderId: String, yappyTransactionId: String, responseJson: String, paymentData: PaymentIntentData
//    ) {
//        Timber.d("TransactionHandler.handleQrResponse: Procesando respuesta QR. YappyTxnId: $yappyTransactionId")
//        val json = try { JSONObject(responseJson) } catch (e: Exception) {
//            Timber.e(e, "❌ TransactionHandler.handleQrResponse: Respuesta QR no es JSON: $responseJson")
//            finishWithFailureEarly("Error comunicación (respuesta QR Yappy inválida).", localOrderId, yappyTransactionId)
//            return
//        }
//
//        if (json.has("body")) {
//            val body = json.getJSONObject("body")
//            val resultHash = body.optString("hash")
//            val yappyDate  = body.optString("date")
//
//            if (resultHash.isBlank()) {
//                Timber.e("❌ TransactionHandler.handleQrResponse: Hash QR vacío. Body Yappy: $body")
//                finishWithFailureEarly("Yappy no devolvió hash QR.", localOrderId, yappyTransactionId)
//                return
//            }
//            Timber.i("✅ TransactionHandler.handleQrResponse: QR listo. Hash: ${resultHash.take(10)}..., YappyDate: $yappyDate")
//
//            val dm = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
//            val displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
//
//            Timber.d("🚀 TransactionHandler.handleQrResponse: Preparando Intent para QrResultActivity...")
//            val qrIntent = Intent(activity, QrResultActivity::class.java).apply {
//                putExtra("qrHash", resultHash)
//                putExtra("qrTransactionId", yappyTransactionId)
//                putExtra("qrDate", yappyDate)
//                putExtra("qrAmount", paymentData.totalAmount.toString())
//                putExtra("localOrderId", localOrderId)
//                putExtra("originalHioPosTransactionId", originalHioPosTransactionId)
//                putExtra("ShopData", paymentData.shopDataXml ?: "")
//                putExtra("ReceiptPrinterColumns", paymentData.receiptPrinterColumns)
//                putExtra("TipAmount", (paymentData.tipAmount * 100).toInt().toString())
//                putExtra("TaxAmount", (paymentData.taxAmount * 100).toInt().toString())
//                putExtra("CurrencyISO", paymentData.currencyISO ?: "")
//                putExtra("TransactionType", paymentData.transactionType ?: TefTransactionResults.TYPE_SALE)
//            }
//            logIntentExtras(qrIntent, "TransactionHandler - Intent para QrResultActivity")
//
//            if (displays.isNotEmpty()) {
//                Timber.d("🖥️ TransactionHandler.handleQrResponse: Mostrando QR pantalla secundaria.")
//                try { QrPresentation(activity, resultHash).show() }
//                catch (e: Exception) { Timber.e(e, "⚠️ TransactionHandler.handleQrResponse: Error QrPresentation.") }
//            }
//            Timber.d("📱 TransactionHandler.handleQrResponse: Iniciando QrResultActivity, finalizando MainActivity.")
//            activity.startActivity(qrIntent)
//            activity.finish()
//        } else {
//            val msg = json.optString("message", "Respuesta Yappy sin 'body'.")
//            Timber.e("❌ TransactionHandler.handleQrResponse: Respuesta Yappy sin 'body'. JSON: $responseJson")
//            Toast.makeText(activity, "Error Yappy: $msg", Toast.LENGTH_LONG).show()
//            finishWithFailureEarly(msg, localOrderId, yappyTransactionId)
//        }
//    }
//
//    private fun handleTransactionError(e: Exception) {
//        Timber.w(e, "TransactionHandler.handleTransactionError: HioPosTxnId: $originalHioPosTransactionId")
//        val errorMessage = ErrorUtils.getErrorMessageFromException(e)
//        if (retryCount < maxRetries) {
//            retryCount++
//            Timber.i("🔄 TransactionHandler.handleTransactionError: Reintento ${retryCount}/${maxRetries}. Error: $errorMessage")
//            ErrorHandler.showNetworkError(activity, "$errorMessage\nReintentando (${retryCount}/${maxRetries})...",
//                onDismiss = { Timber.w(" TransactionHandler.handleTransactionError: Usuario canceló reintento."); finishWithFailureEarly(errorMessage, currentLocalOrderId, currentYappyTransactionId) },
//                onRetry = { Timber.d(" TransactionHandler.handleTransactionError: Usuario reintenta."); handle() }
//            )
//        } else {
//            Timber.e(" TransactionHandler.handleTransactionError: Max reintentos ($maxRetries). Error final: $errorMessage")
//            ErrorHandler.showNetworkError(activity, "Max reintentos. Transacción no completada.\nError: $errorMessage",
//                onDismiss = { Timber.w("TransactionHandler.handleTransactionError: Error final confirmado."); finishWithFailureEarly("Max reintentos: $errorMessage", currentLocalOrderId, currentYappyTransactionId) }
//            )
//        }
//    }
//
//    private fun finishWithFailureEarly(
//        errorMessage: String, localOrderId: String? = currentLocalOrderId, yappyTransactionId: String? = currentYappyTransactionId
//    ) {
//        Timber.w("‼️ TransactionHandler.finishWithFailureEarly: HioPosTxnId=$originalHioPosTransactionId, Error='$errorMessage', LocalOrderId='$localOrderId', YappyTxnId='$yappyTransactionId'")
//        val resultIntent = Intent().apply {
//            putExtra("TransactionResult", TefTransactionResults.RESULT_FAILED)
//            putExtra("TransactionType", currentPaymentData?.transactionType ?: TefTransactionResults.TYPE_SALE)
//            putExtra("ErrorMessage", errorMessage)
//            if (originalHioPosTransactionId != 0) putExtra("TransactionId", originalHioPosTransactionId)
//
//            val txDataJson = JSONObject()
//            yappyTransactionId?.takeIf { it.isNotBlank() }?.let { txDataJson.put("yappyTransactionId", it) }
//            localOrderId?.takeIf { it.isNotBlank() }?.let { txDataJson.put("localOrderId", it) }
//            putExtra("TransactionData", txDataJson.toString().take(250))
//
//            currentPaymentData?.let { pd ->
//                putExtra("Amount", (pd.totalAmount * 100).toInt().toString())
//                putExtra("TipAmount", (pd.tipAmount * 100).toInt().toString())
//                putExtra("TaxAmount", (pd.taxAmount * 100).toInt().toString())
//                putExtra("CurrencyISO", pd.currencyISO ?: "")
//            }
//        }
//        Timber.i("  📤 TransactionHandler.finishWithFailureEarly: Enviando error a Hiopos: ${extrasToString(resultIntent.extras!!)}")
//        activity.setResult(Activity.RESULT_OK, resultIntent)
//        activity.finish()
//    }
//
//    private fun showInvalidAmountError() {
//        val errorMsg = "Monto transacción inválido."
//        Timber.e("❌ TransactionHandler.showInvalidAmountError: $errorMsg. HioPosTxnId: $originalHioPosTransactionId")
//        Toast.makeText(activity, errorMsg, Toast.LENGTH_LONG).show()
//        finishWithFailureEarly(errorMsg)
//    }
//
//    private fun logIntentExtras(intent: Intent, logTagPrefix: String) {
//        val extras = intent.extras
//        if (extras == null) { Timber.d("$logTagPrefix: No hay extras."); return }
//        Timber.d("$logTagPrefix: Contenido Extras ->")
//        extras.keySet().forEach { key ->
//            val value = extras.get(key)
//            Timber.d("  > $key: $value (Tipo: ${value?.javaClass?.simpleName ?: "null"})")
//        }
//    }
//}
//
//// Helper fuera de la clase o en utils.kt
//fun extrasToString(bundle: Bundle): String {
//    return bundle.keySet().joinToString(prefix = "Bundle[", postfix = "]") { key ->
//        "$key=${bundle.get(key)}"
//    }
//}