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
import com.example.tefbanesco.screens.QrResultActivity // Asegúrate que esta clase tenga las constantes
import com.example.tefbanesco.screens.TefTransactionResults // Importar las constantes
import com.example.tefbanesco.storage.LocalStorage
import com.example.tefbanesco.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

class TransactionHandler(
    private val activity: Activity,
    // onSuccess ya no es realmente necesario aquí si el resultado se maneja en QrResultActivity
    // o se devuelve directamente desde aquí en caso de fallo temprano.
    // Considera eliminarlo si no tiene otro propósito.
    private val onSuccess: (() -> Unit)? = null
) {
    val isLoading = mutableStateOf(false)
    private var retryCount = 0
    private val maxRetries = 3
    private val scope = CoroutineScope(Dispatchers.Main)

    // Para mantener una referencia al localOrderId si se necesita en finishWithFailureEarly
    private var currentLocalOrderId: String? = null
    private var currentYappyTransactionId: String? = null // Para errores después de obtenerlo

    fun handle() {
        Timber.d("🟢 TransactionHandler.handle invoked with action=%s, extras=%s", activity.intent.action, activity.intent.extras)

        if (!ApiConfig.isBaseUrlConfigured()) {
            ErrorHandler.showConfigurationError(activity) {
                finishWithFailureEarly("Error de Configuración: El módulo no está configurado.")
            }
            return
        }

        val amountValue = getAmountFromIntent()
        if (amountValue <= 0) {
            showInvalidAmountError() // Esta ya llama a finishWithFailureEarly
            return
        }

        val dateValue = activity.intent.extras?.getString("date") ?: "" // Este es el date del intent original
        Timber.d("📥 Parsed parameters: dateFromIntent=%s, amount=%s", dateValue, amountValue)

        scope.launch {
            try {
                isLoading.value = true
                val sessionToken = openSession(activity) // Este es el Bearer token
                if (sessionToken.isBlank()) {
                    Timber.e("TransactionHandler: No se pudo obtener el token de sesión.")
                    ErrorHandler.showErrorDialog(activity, "Error de Autenticación", "No se pudo iniciar la sesión con el servicio de pago.") {
                        finishWithFailureEarly("Fallo al abrir sesión con Yappy.")
                    }
                    return@launch
                }
                LocalStorage.saveToken(activity, sessionToken) // Guardar el Bearer token

                val (localOrderId, yappyTransactionId, responseJson) = generateQr(sessionToken, amountValue)
                currentLocalOrderId = localOrderId // Guardar por si se necesita en un error posterior
                currentYappyTransactionId = yappyTransactionId

                if (yappyTransactionId.isBlank()) {
                    Timber.e("TransactionHandler: No se pudo obtener el yappyTransactionId de la generación del QR. Respuesta: $responseJson")
                    val errorMsgFromApi = try { JSONObject(responseJson).optString("message", "Yappy no devolvió un ID de transacción.") } catch (e: Exception) { "Respuesta inválida de Yappy al generar QR." }
                    ErrorHandler.showErrorDialog(activity, "Error de Comunicación", errorMsgFromApi) {
                        // Pasamos el localOrderId si lo tenemos, para TransactionData
                        finishWithFailureEarly(errorMsgFromApi, localOrderId)
                    }
                    return@launch
                }

                // Si llegamos aquí, yappyTransactionId es válido.
                handleQrResponse(localOrderId, yappyTransactionId, responseJson, dateValue, amountValue)

            } catch (e: Exception) {
                // Este catch es para excepciones en openSession o generateQr principalmente
                Timber.e(e, "TransactionHandler: Excepción durante el proceso de inicio de transacción.")
                handleTransactionError(e) // Esta función ya maneja los reintentos y llama a finishWithFailureEarly
            } finally {
                isLoading.value = false
            }
        }
    }

    private suspend fun openSession(context: Context): String {
        val config = LocalStorage.getConfig(context)
        // Asegurarse de que las credenciales básicas están presentes
        if (config["api_key"].isNullOrBlank() || config["secret_key"].isNullOrBlank() ||
            config["device.id"].isNullOrBlank() || config["group_id"].isNullOrBlank()) {
            Timber.e("TransactionHandler.openSession: Faltan credenciales de configuración para abrir sesión.")
            // No podemos mostrar un diálogo desde una suspend fun directamente de forma sencilla,
            // lanzamos una excepción que será capturada por el bloque try-catch en handle()
            throw IllegalStateException("Configuración incompleta para abrir sesión.")
        }
        return ApiService.openDeviceSession(
            apiKey = config["api_key"]!!, // Usamos !! porque acabamos de verificar isNullOrBlank
            secretKey = config["secret_key"]!!,
            deviceId = config["device.id"]!!,
            deviceName = config["device.name"] ?: "DefaultDeviceName", // Puede tener un default
            deviceUser = config["device.user"] ?: "DefaultDeviceUser", // Puede tener un default
            groupId = config["group_id"]!!,
            context = context
        ).also {
            if (it.isNotBlank()) {
                Timber.d("⚙️ TransactionHandler.openSession: Sesión abierta, token de sesión obtenido: ${it.take(10)}...")
            } else {
                Timber.w("TransactionHandler.openSession: Token de sesión vacío recibido de ApiService.")
            }
        }
    }

    private suspend fun generateQr(sessionToken: String, amountValue: Double): Triple<String, String, String> {
        val config = LocalStorage.getConfig(activity)
        val qrEndpoint = "${ApiConfig.BASE_URL}/qr/generate/DYN" // Asumiendo que ApiConfig.BASE_URL es correcto
        Timber.d(
            "⚙️ TransactionHandler.generateQr: Generando QR con endpoint=%s, APIKey=%s, SecretKey=%s, Amount=%.2f",
            qrEndpoint, (config["api_key"] ?: "N/A").take(5), (config["secret_key"] ?: "N/A").take(5), amountValue
        )
        return ApiService.generateQrWithToken(
            endpoint = qrEndpoint,
            token = sessionToken, // Este es el Bearer token de sesión
            apiKey = config["api_key"] ?: "",
            secretKey = config["secret_key"] ?: "",
            inputValue = amountValue
        )
    }

    private fun handleQrResponse(
        localOrderId: String,
        yappyTransactionId: String,
        responseJson: String,
        dateFromIntent: String, // La fecha original del intent que inició la transacción
        amountValue: Double
    ) {
        val json = JSONObject(responseJson) // Asumimos que responseJson es un JSON válido
        if (json.has("body")) {
            val body = json.getJSONObject("body")
            val resultHash = body.optString("hash")
            // Es preferible usar la fecha devuelta por Yappy si está disponible
            val yappyDate = body.optString("date", dateFromIntent)
            Timber.d("✅ QR generado para Yappy TxnID: %s, hash: %s, YappyDate: %s", yappyTransactionId, resultHash, yappyDate)

            val dm = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)

            val qrIntent = Intent(activity, QrResultActivity::class.java).apply {
                putExtra("qrHash", resultHash)
                putExtra("qrTransactionId", yappyTransactionId)
                putExtra("qrDate", yappyDate) // Usar la fecha de Yappy
                putExtra("qrAmount", amountValue.toString())
                putExtra("localOrderId", localOrderId) // Pasar también el localOrderId
            }

            if (displays.isNotEmpty()) {
                Timber.d("🖥️ Mostrando QR en pantalla secundaria y finalizando MainActivity para evitar superposición.")
                QrPresentation(activity, resultHash).show()
                // Después de mostrar en la pantalla secundaria, aún queremos que QrResultActivity maneje el polling
                // y el resultado final. Si QrPresentation es solo visual y no interactivo,
                // entonces la MainActivity NO debería finalizar aquí. Debería iniciar QrResultActivity
                // y QrResultActivity podría decidir si mostrar QrPresentation o su propia UI.
                // Por ahora, mantendremos la lógica original de que MainActivity finaliza.
                // Pero si QrPresentation NO detiene el flujo, esto debe reconsiderarse.
                // ASUMIENDO QUE QrPresentation es bloqueante o que el flujo principal sigue en QrResultActivity:
                activity.startActivity(qrIntent)
                activity.finish() // MainActivity finaliza. QrResultActivity toma el control.
            } else {
                Timber.d("📱 Mostrando QR en QrResultActivity.")
                activity.startActivity(qrIntent)
                activity.finish() // MainActivity finaliza. QrResultActivity toma el control.
            }
        } else {
            val msg = json.optString("message", "Error desconocido al generar QR (respuesta sin 'body').")
            Timber.e("⚠️ Respuesta de generación de QR sin 'body': %s. JSON: %s", msg, responseJson)
            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
            finishWithFailureEarly(msg, localOrderId, yappyTransactionId)
        }
    }

    private fun handleTransactionError(e: Exception) {
        Timber.e(e, "💥 Excepción durante el proceso de inicio de transacción (posiblemente en openSession o generateQr).")
        val errorMessage = ErrorUtils.getErrorMessageFromException(e)
        if (retryCount < maxRetries) {
            retryCount++
            Timber.d("Reintentando... intento $retryCount de $maxRetries")
            ErrorHandler.showNetworkError(
                activity = activity,
                message = "$errorMessage\n\nIntento $retryCount de $maxRetries.",
                onDismiss = { finishWithFailureEarly(errorMessage, currentLocalOrderId, currentYappyTransactionId) },
                onRetry = { handle() } // Vuelve a llamar a handle() para reintentar todo el proceso
            )
        } else {
            Timber.e("Máximo de reintentos alcanzado.")
            ErrorHandler.showNetworkError(
                activity = activity,
                message = "Se ha alcanzado el número máximo de reintentos. Por favor, intenta más tarde.",
                onDismiss = { finishWithFailureEarly("Máximo de reintentos alcanzado: $errorMessage", currentLocalOrderId, currentYappyTransactionId) }
            )
        }
    }

    // CAMBIO: Modificado para devolver un resultado estructurado
    private fun finishWithFailureEarly(
        errorMessage: String,
        localOrderId: String? = currentLocalOrderId, // Usar el guardado si está disponible
        yappyTransactionId: String? = currentYappyTransactionId // Usar el guardado si está disponible
    ) {
        Timber.w("TransactionHandler: Finalizando temprano con fallo: $errorMessage")
        val resultIntent = Intent()
        // La acción de respuesta no se suele establecer, la actividad que llama sabe a qué acción está respondiendo.
        // resultIntent.action = TefTransactionResults.ACTION_TRANSACTION_RESULT

        resultIntent.putExtra("TransactionResult", TefTransactionResults.RESULT_FAILED)
        resultIntent.putExtra("TransactionType", TefTransactionResults.TYPE_SALE) // Asumimos SALE para cualquier intento de transacción
        resultIntent.putExtra("ErrorMessage", errorMessage)

        // TransactionData: Intentar construirlo con la información disponible
        val transactionDataJson = JSONObject().apply {
            yappyTransactionId?.takeIf { it.isNotBlank() }?.let { put("yappyTransactionId", it) }
            localOrderId?.takeIf { it.isNotBlank() }?.let { put("localOrderId", it) }
            // Otros datos como fecha y monto podrían no estar confirmados o ser relevantes aquí.
        }.toString()

        resultIntent.putExtra("TransactionData", transactionDataJson.take(250))
        // BatchNumber: Omitido, ya que es improbable tenerlo en un fallo temprano.

        activity.setResult(Activity.RESULT_OK, resultIntent) // Siempre RESULT_OK con detalles en extras
        activity.finish()
    }

    private fun getAmountFromIntent(): Double {
        val extras = activity.intent.extras
        val amountStr = extras?.getString("Amount")
            ?: activity.intent.getStringExtra("Amount")
            ?: "0"
        var amount = amountStr.toDoubleOrNull() ?: 0.0

        // La documentación de HioPos indica que Amount viene en céntimos.
        // Si amountStr es "100", significa 1.00.
        // Si la conversión ya es correcta a la unidad (ej. 1.0 para 1 dólar), esta división no es necesaria.
        // Pero si viene como "100" para $1.00, entonces SÍ es necesaria.
        // Basado en tu log "amount=1.0", parece que ya está en la unidad principal.
        // Si el log fuera "amount=100" y quisieras $1.00, la división sería correcta.
        // Vamos a asumir que la conversión de String a Double ya maneja esto o que `amountStr`
        // ya está en la unidad principal (ej. "1.0").
        // La lógica de dividir por 100 si es >= 100 es un poco ambigua sin saber el formato exacto de entrada.
        // La mantendré como estaba, pero revísala.
        if (amount >= 100 && amountStr.none { it == '.'}) { // Solo dividir si es un número entero grande (probablemente centavos)
            Timber.d("↔️ Ajustando amount (posiblemente en centavos) de $amount a ${amount / 100.0}")
            amount /= 100.0
        } else {
            Timber.d("Amount recibido: $amount (ya en unidad principal o con decimales)")
        }


        if (amount <= 0) {
            // Intentar extraer de DocumentData como fallback
            val documentData = extras?.getString("DocumentData") ?: ""
            if (documentData.isNotBlank()) {
                Timber.d("Attempting to parse NetAmount from DocumentData.")
                // El regex necesita escapar las barras invertidas correctamente para una string literal de Kotlin
                val netAmountRegex = Regex("""<HeaderField Key=\\"NetAmount\\">([\d.]+)</HeaderField>""")
                val match = netAmountRegex.find(documentData)
                val netAmountFromXml = match?.groups?.get(1)?.value?.toDoubleOrNull()
                if (netAmountFromXml != null && netAmountFromXml > 0) {
                    Timber.d("NetAmount encontrado en DocumentData: $netAmountFromXml")
                    amount = netAmountFromXml
                } else {
                    Timber.w("NetAmount no encontrado o inválido en DocumentData.")
                }
            }
        }
        Timber.d("Final amount a usar: %.2f", amount)
        return amount
    }

    private fun showInvalidAmountError() {
        val errorMsg = "Monto inválido o no recibido en el Intent."
        Toast.makeText(activity, errorMsg, Toast.LENGTH_LONG).show()
        // No podemos obtener localOrderId ni yappyTransactionId aquí porque el monto es el primer chequeo.
        finishWithFailureEarly(errorMsg)
    }
}