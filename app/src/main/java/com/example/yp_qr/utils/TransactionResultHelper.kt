// File: app/src/main/java/com/example/yp_qr/utils/TransactionResultHelper.kt
package com.example.tefbanesco.utils

import android.app.Activity
import android.content.Intent
import com.example.tefbanesco.screens.TefTransactionResults // Asegúrate que esta clase/objeto tenga las constantes
import org.json.JSONObject
import timber.log.Timber

object TransactionResultHelper {

    /**
     * Prepara y establece el resultado de una transacción para ser devuelto a la actividad que llamó.
     *
     * @param activity La Activity que está finalizando y devolviendo el resultado.
     * @param result El estado del resultado de la transacción (e.g., "ACCEPTED", "FAILED").
     * @param yappyTransactionId El ID de la transacción de Yappy.
     * @param localOrderId El ID de orden local.
     * @param transactionDate La fecha de la transacción.
     * @param transactionAmount El monto de la transacción.
     * @param errorMessage Mensaje de error opcional, solo relevante si el resultado es un fallo.
     */
    fun prepareAndSetTransactionResult(
        activity: Activity,
        result: String,
        yappyTransactionId: String,
        localOrderId: String,
        transactionDate: String,
        transactionAmount: String,
        errorMessage: String? = null
    ) {
        val resultIntent = Intent()
        // La acción "TRANSACTION" es la que INICIA la app. No se suele poner acción en la respuesta.

        resultIntent.putExtra("TransactionResult", result)
        resultIntent.putExtra("TransactionType", TefTransactionResults.TYPE_SALE) // Asumiendo SALE

        // BatchNumber: Omitido o placeholder
        // resultIntent.putExtra("BatchNumber", "BATCH_YAPPY_123")

        val transactionDataJson = JSONObject().apply {
            put("yappyTransactionId", yappyTransactionId)
            put("localOrderId", localOrderId)
            put("date", transactionDate)
            put("amount", transactionAmount)
        }.toString()

        if (transactionDataJson.length > 250) {
            Timber.w("TransactionData excede los 250 caracteres: ${transactionDataJson.length}")
            resultIntent.putExtra("TransactionData", transactionDataJson.take(250))
        } else {
            resultIntent.putExtra("TransactionData", transactionDataJson)
        }

        if (result == TefTransactionResults.RESULT_FAILED && errorMessage != null) {
            resultIntent.putExtra("ErrorMessage", errorMessage)
        }

        Timber.i(
            "TransactionResultHelper: Finalizando Activity (${activity.localClassName}) con resultado: $result, Data: $transactionDataJson, Error: $errorMessage"
        )
        activity.setResult(Activity.RESULT_OK, resultIntent)
        // La llamada a activity.finish() se hará desde la propia Activity cuando sea apropiado.
    }

    /**
     * Prepara y establece un resultado de error genérico cuando la información completa de la transacción
     * podría no estar disponible.
     *
     * @param activity La Activity que está finalizando.
     * @param errorMessage El mensaje de error a devolver.
     * @param yappyTransactionId (Opcional) ID de transacción de Yappy si se conoce.
     * @param localOrderId (Opcional) ID de orden local si se conoce.
     */
    fun prepareAndSetGenericErrorResult(
        activity: Activity,
        errorMessage: String,
        yappyTransactionId: String? = null,
        localOrderId: String? = null
    ) {
        val resultIntent = Intent()
        resultIntent.putExtra("TransactionResult", TefTransactionResults.RESULT_FAILED)
        resultIntent.putExtra("TransactionType", TefTransactionResults.TYPE_SALE) // Asumiendo SALE
        resultIntent.putExtra("ErrorMessage", errorMessage)

        val transactionDataJson = JSONObject().apply {
            yappyTransactionId?.takeIf { it.isNotBlank() }?.let { put("yappyTransactionId", it) }
            localOrderId?.takeIf { it.isNotBlank() }?.let { put("localOrderId", it) }
        }.toString()
        resultIntent.putExtra("TransactionData", transactionDataJson.take(250))

        Timber.e("TransactionResultHelper: Finalizando Activity (${activity.localClassName}) con error directo: $errorMessage, Data: $transactionDataJson")
        activity.setResult(Activity.RESULT_OK, resultIntent)
    }
}