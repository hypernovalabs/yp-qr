package com.example.yappy.screens

import android.app.Activity // Necesario para RESULT_OK, RESULT_CANCELED
import android.content.Intent // Necesario para el Intent de resultado
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
// Quita el import de androidx.compose.ui.unit.dp si no se usa directamente aquí
// import androidx.compose.ui.unit.dp
import com.example.yappy.storage.LocalStorage
import com.example.yappy.network.ApiService
// Los imports de las pantallas ya están bien
import kotlinx.coroutines.launch
import timber.log.Timber
import org.json.JSONObject // Necesario para crear el TransactionData

// CAMBIO: Definir constantes para los resultados y tipos de transacción
object TefTransactionResults {
    const val ACTION_TRANSACTION_RESULT = "icg.actions.electronicpayment.yappy.TRANSACTION" // Reconfirmar si esta es la acción de RESPUESTA o si es solo el nombre del apk_name lo que se debe retornar

    const val RESULT_ACCEPTED = "ACCEPTED"
    const val RESULT_FAILED = "FAILED"
    const val RESULT_UNKNOWN = "UNKNOWN_RESULT" // Si aplica para tu flujo

    const val TYPE_SALE = "SALE"
    // const val TYPE_VOID = "VOID_TRANSACTION" // Si implementas anulación directa que devuelve resultado
}


class QrResultActivity : ComponentActivity() {

    // CAMBIO: Variables para almacenar los datos necesarios para el resultado
    private var yappyTransactionId: String = ""
    private var localOrderId: String = "" // Lo recibiremos por si es útil para TransactionData
    private var transactionDate: String = ""
    private var transactionAmount: String = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extraemos los datos y los guardamos en las variables de la Activity
        transactionDate    = intent.getStringExtra("qrDate") ?: ""
        yappyTransactionId = intent.getStringExtra("qrTransactionId") ?: ""
        localOrderId       = intent.getStringExtra("localOrderId") ?: "" // Recibir localOrderId
        val qrHash         = intent.getStringExtra("qrHash") ?: ""
        transactionAmount  = intent.getStringExtra("qrAmount") ?: ""

        if (yappyTransactionId.isBlank()) {
            Timber.e("QrResultActivity: yappyTransactionId está vacío. Finalizando con error.")
            finishWithError("ID de transacción de Yappy no recibido.")
            return
        }

        setContent {
            // val context = LocalContext.current // Ya no se necesita aquí directamente
            val coroutineScope = rememberCoroutineScope()

            var showCancelSuccessVisual by remember { mutableStateOf(false) }
            var showPaymentSuccessVisual by remember { mutableStateOf(false) }

            when {
                showCancelSuccessVisual -> CancelResultScreen(
                    title = "Pago Cancelado",
                    message = "La transacción $yappyTransactionId fue cancelada.",
                    onConfirm = {
                        // CAMBIO: finishWithCancel ahora se llama desde el callback onCancelSuccess
                        // que ya ha sido invocado para llegar aquí. Solo cerramos.
                        finish()
                    }
                )

                showPaymentSuccessVisual -> SuccessResultScreen(
                    title = "Pago Confirmado",
                    message = "La transacción $yappyTransactionId se completó exitosamente.",
                    onConfirm = {
                        // CAMBIO: finishWithSuccess ahora se llama desde el callback onPaymentSuccess
                        // que ya ha sido invocado para llegar aquí. Solo cerramos.
                        finish()
                    }
                )

                else -> QrResultScreen(
                    date            = transactionDate,
                    transactionId   = yappyTransactionId,
                    hash            = qrHash,
                    amount          = transactionAmount,
                    onCancelSuccess = {
                        Timber.d("🔔 QrResultActivity - onCancelSuccess: txn=$yappyTransactionId")
                        // El polling en QrResultScreen ya determinó que fue CANCELLED, FAILED, o EXPIRED
                        // Podrías diferenciar aquí si quieres UNKNOWN_RESULT vs FAILED
                        // Por ahora, asumimos FAILED si no es un éxito explícito de cancelación de usuario.
                        // Si la cancelación fue iniciada por el usuario desde QrResultScreen, ese
                        // es un tipo de FAILED (o un VOID si tuvieras esa lógica separada).
                        finishWithTransactionResult(TefTransactionResults.RESULT_FAILED, "Transacción cancelada o fallida en Yappy.")
                        showCancelSuccessVisual = true // Para la UI
                    },
                    onPaymentSuccess = {
                        Timber.d("🔔 QrResultActivity - onPaymentSuccess: txn=$yappyTransactionId")
                        coroutineScope.launch {
                            try {
                                val cfg       = LocalStorage.getConfig(this@QrResultActivity)
                                val token     = cfg["device_token"].orEmpty()
                                val apiKey    = cfg["api_key"].orEmpty()
                                val secretKey = cfg["secret_key"].orEmpty()
                                if (token.isNotBlank()) { // Solo cerrar sesión si hay token
                                    ApiService.closeDeviceSession(token, apiKey, secretKey)
                                    Timber.d("🔒 Sesión de dispositivo cerrada correctamente")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "❌ Error cerrando sesión de dispositivo")
                            }
                            // No borramos toda la config aquí, solo el token de sesión si se desea.
                            // LocalStorage.clear(this@QrResultActivity) // DECIDIR SI ESTO ES NECESARIO
                            LocalStorage.saveToken(this@QrResultActivity, "") // Borrar solo el token de sesión
                            Timber.d("🧹 Token de sesión borrado de LocalStorage")

                            finishWithTransactionResult(TefTransactionResults.RESULT_ACCEPTED)
                            showPaymentSuccessVisual = true // Para la UI
                        }
                    }
                )
            }
        }
    }

    // CAMBIO: Nueva función para finalizar con un resultado de transacción específico
    private fun finishWithTransactionResult(result: String, errorMessage: String? = null) {
        val resultIntent = Intent() // No se especifica una acción para el intent de respuesta, solo los extras.
        // La acción "TRANSACTION" es la que INICIA la app.

        resultIntent.putExtra("TransactionResult", result)
        resultIntent.putExtra("TransactionType", TefTransactionResults.TYPE_SALE) // Asumiendo SALE

        // BatchNumber: ¿De dónde lo obtenemos? Si Yappy no lo provee, podría ser omitido o un valor por defecto.
        // Consultar documentación de Yappy si este dato está en alguna respuesta.
        // Por ahora, lo omitiremos o pondremos un placeholder si es estrictamente necesario.
        // resultIntent.putExtra("BatchNumber", "BATCH_YAPPY_123") // Placeholder

        // TransactionData: Serializar datos relevantes
        val transactionDataJson = JSONObject().apply {
            put("yappyTransactionId", yappyTransactionId)
            put("localOrderId", localOrderId)
            put("date", transactionDate)
            put("amount", transactionAmount)
            // Añadir cualquier otro dato que HioPosCloud pueda necesitar para referenciar la tx.
        }.toString()

        if (transactionDataJson.length > 250) {
            Timber.w("TransactionData excede los 250 caracteres: ${transactionDataJson.length}")
            // Considerar truncar o re-serializar de forma más compacta si es un problema.
            resultIntent.putExtra("TransactionData", transactionDataJson.take(250))
        } else {
            resultIntent.putExtra("TransactionData", transactionDataJson)
        }

        if (result == TefTransactionResults.RESULT_FAILED && errorMessage != null) {
            resultIntent.putExtra("ErrorMessage", errorMessage)
        }

        Timber.i("QrResultActivity: Finalizando con resultado: $result, Data: $transactionDataJson, Error: $errorMessage")
        setResult(Activity.RESULT_OK, resultIntent) // Siempre RESULT_OK y los detalles van en los extras
        finish()
    }

    // CAMBIO: Función para errores que ocurren ANTES de que el polling pueda determinar un estado
    private fun finishWithError(errorMessage: String) {
        val resultIntent = Intent()
        resultIntent.putExtra("TransactionResult", TefTransactionResults.RESULT_FAILED)
        resultIntent.putExtra("TransactionType", TefTransactionResults.TYPE_SALE)
        resultIntent.putExtra("ErrorMessage", errorMessage)
        // TransactionData podría estar incompleto o no disponible aquí.
        // Si yappyTransactionId no está, no podemos poner mucho.
        val transactionDataJson = JSONObject().apply {
            if (yappyTransactionId.isNotBlank()) put("yappyTransactionId", yappyTransactionId)
            if (localOrderId.isNotBlank()) put("localOrderId", localOrderId)
            // Otros campos podrían no estar disponibles.
        }.toString()
        resultIntent.putExtra("TransactionData", transactionDataJson.take(250))


        Timber.e("QrResultActivity: Finalizando con error directo: $errorMessage")
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}