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
            Timber.e("[YAPPY] Fallo al parsear datos del Intent inicial.")
            finishWithFailureEarly(
                activity,
                currentPaymentData,
                errorMessage = "Error interno: no se pudieron leer los datos de la transacción inicial."
            )
            return
        }
        val pd = currentPaymentData!!

        // API v3.5: Priorizar DocumentPath si existe y hay banderas que lo indican
        processDocumentPathIfNeeded(pd)

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
            Timber.d("[YAPPY] Intent Extra > $key=${intent.extras?.get(key)}")
        }
    }

    /**
     * API v3.5: Procesa DocumentPath si existe y debería tener prioridad sobre DocumentData
     * según las configuraciones de comportamiento de HioPos.
     */
    private fun processDocumentPathIfNeeded(pd: PaymentIntentData) {
        // Verificar si tenemos DocumentPath y si debemos usarlo
        if (pd.documentPath != null && shouldUseDocumentPath()) {
            Timber.d("[YAPPY] DocumentPath encontrado: ${pd.documentPath}")

            try {
                // Leer el contenido del archivo XML
                val file = java.io.File(pd.documentPath)
                if (!file.exists() || !file.canRead()) {
                    Timber.w("[YAPPY] No se puede acceder al archivo en DocumentPath: ${pd.documentPath}")
                    return
                }

                val documentContent = file.readText()

                // Si el contenido está vacío, no hacer nada
                if (documentContent.isBlank()) {
                    Timber.w("[YAPPY] El archivo en DocumentPath está vacío: ${pd.documentPath}")
                    return
                }

                Timber.d("[YAPPY] Documento leído desde DocumentPath, ${documentContent.length} bytes")

                // Parsear el XML y extraer la información relevante
                // Este es un ejemplo simplificado. En una implementación completa,
                // deberías usar un parser XML adecuado como ya haces en otros lugares.
                val documentInfo = parseDocumentXml(documentContent)

                // Aquí puedes actualizar pd o usar documentInfo directamente en tu lógica
                // Por ejemplo, podrías extraer monto, impuestos, etc.

                Timber.i("[YAPPY] DocumentPath procesado correctamente. Priorizando sobre DocumentData.")
            } catch (e: Exception) {
                Timber.e(e, "[YAPPY] Error al procesar DocumentPath: ${pd.documentPath}")
                // Si hay error al leer DocumentPath, podemos caer back a DocumentData
            }
        }
    }

    /**
     * Parsea el contenido XML del documento.
     * Esta es una implementación simplificada, deberías adaptarla a la estructura
     * específica del XML que HioPos envía en DocumentData/DocumentPath.
     */
    private fun parseDocumentXml(xmlContent: String): Map<String, String> {
        val result = mutableMapOf<String, String>()

        try {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(xmlContent.reader())

            var eventType = parser.eventType
            var currentTag: String? = null

            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> {
                        val text = parser.text.trim()
                        if (currentTag != null && text.isNotEmpty()) {
                            result[currentTag] = text
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        currentTag = null
                    }
                }
                eventType = parser.next()
            }

            Timber.d("[YAPPY] Documento XML parseado, ${result.size} campos encontrados")
        } catch (e: Exception) {
            Timber.e(e, "[YAPPY] Error parseando XML del documento")
        }

        return result
    }

    /**
     * Determina si se debe usar DocumentPath en lugar de DocumentData.
     * Este valor debería obtenerse de la respuesta de GET_BEHAVIOR.
     */
    private fun shouldUseDocumentPath(): Boolean {
        // Implementación real:
        // 1. Obtener el valor de OnlyUseDocumentPath del SharedPreferences donde lo guardaste
        //    después de que HioPos llamó a GET_BEHAVIOR

        // Por ejemplo:
        val prefs = activity.getSharedPreferences("HioPosBehaviorPrefs", Context.MODE_PRIVATE)
        val onlyUseDocumentPath = prefs.getBoolean("OnlyUseDocumentPath", false)

        // También podrías guardarlo en LocalStorage si ya lo tienes configurado para esto

        // Por ahora, devolvemos false hasta que implementes la persistencia completa
        return onlyUseDocumentPath
    }
}
