
// TransactionResultHelper.kt
package com.example.tefbanesco.utils

import android.app.Activity
import android.content.Intent
import com.example.tefbanesco.screens.TefTransactionResults
import java.text.DecimalFormat
import timber.log.Timber

/**
 * Helper para construir y enviar Intents de resultado a HioPOS.
 */
object TransactionResultHelper {
    private val decimalFmt = DecimalFormat("0.00")

    fun finishWithFailureEarly(
        activity: Activity,
        pd: PaymentIntentData?,
        errorMessage: String,
        localOrderId: String? = null,
        yappyTransactionId: String? = null
    ) {
        val intent = Intent("icg.actions.electronicpayment.tefbanesco.TRANSACTION").apply {
            putExtra("TransactionResult", TefTransactionResults.RESULT_FAILED)
            putExtra("ErrorMessage", errorMessage)
            pd?.transactionType?.let { putExtra("TransactionType", it) }
            pd?.transactionId?.let { putExtra("TransactionId", it) }
            putExtra("TransactionData", "${yappyTransactionId.orEmpty()}/${localOrderId.orEmpty()}")
            pd?.let { data ->
                putExtra("Amount", decimalFmt.format(data.amount).replace(".", ""))
                putExtra("TipAmount", decimalFmt.format(data.tipAmount).replace(".", ""))
                putExtra("TaxAmount", decimalFmt.format(data.taxAmount).replace(".", ""))
                putExtra("CurrencyISO", data.currencyIso)
            }
        }
        Timber.i("Enviando fallo a HioPOS: $errorMessage, Order=$localOrderId, Yappy=$yappyTransactionId")
        activity.setResult(Activity.RESULT_OK, intent)
        activity.finish()
    }

    fun prepareSuccessIntent(
        intent: Intent,
        pd: PaymentIntentData,
        localOrderId: String,
        yappyTransactionId: String,
        qrHash: String
    ) {
        intent.apply {
            putExtra("qrHash", qrHash)
            putExtra("qrTransactionId", yappyTransactionId)
            putExtra("localOrderId", localOrderId)
            putExtra("originalHioPosTransactionId", pd.transactionId)
            putExtra("ShopData", pd.shopData)
            putExtra("ReceiptPrinterColumns", pd.receiptPrinterColumns)
            putExtra("CurrencyISO", pd.currencyIso)
            putExtra("TransactionType", pd.transactionType)
            putExtra("Amount", decimalFmt.format(pd.amount).replace(".", ""))
            putExtra("TipAmount", decimalFmt.format(pd.tipAmount).replace(".", ""))
            putExtra("TaxAmount", decimalFmt.format(pd.taxAmount).replace(".", ""))

            // API v3.5: Pasar el DocumentPath si existe (prioridad sobre DocumentData)
            pd.documentPath?.let {
                putExtra("DocumentPath", it)
                Timber.d("[YAPPY] Usando DocumentPath: $it")
            }

            // Si hay OverPaymentType, pasarlo como información útil
            if (pd.overPaymentType != -1) {
                putExtra("OverPaymentTypeOriginal", pd.overPaymentType)
                Timber.d("[YAPPY] OverPaymentType recibido: ${pd.overPaymentType}")
            }

            // Si es un pago avanzado, registrar
            if (pd.isAdvancedPayment) {
                putExtra("IsAdvancedPaymentOriginal", true)
                Timber.d("[YAPPY] Pago avanzado: true")
            }
        }
    }
}
