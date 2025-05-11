package com.example.tefbanesco.utils

import android.content.Intent
import android.os.Bundle
import timber.log.Timber

/**
 * Data class para mantener los datos extraídos del Intent de forma estructurada.
 */
data class PaymentIntentData(
    val currencyISO: String?,
    val tenderType: String?,
    val authorizationId: String?,      // null si venía vacío
    val tipAmount: Double,             // en unidad principal
    val shopDataXml: String?,
    val receiptPrinterColumns: Int,
    val externalTransactionData: String?, // null si venía vacío
    val transactionType: String?,
    val languageISO: String?,
    val taxAmount: Double,             // en unidad principal
    val taxDetailXml: String?,
    val posTransactionId: Int,
    val totalAmount: Double            // en unidad principal
)

/**
 * Parsea un Intent de transacción y devuelve un objeto PaymentIntentData,
 * o null si no hay extras o ocurre un error.
 */
fun parsePaymentIntent(intent: Intent): PaymentIntentData? {
    val bundle: Bundle? = intent.extras
    if (bundle == null) {
        Timber.w("El Intent no contiene extras. No se pueden parsear los datos de la transacción.")
        return null
    }

    return try {
        // Leer montos (vienen como cadenas de centavos)
        val amountRaw    = bundle.getString("Amount", "0")?.toDoubleOrNull() ?: 0.0
        val tipRaw       = bundle.getString("TipAmount", "0")?.toDoubleOrNull() ?: 0.0
        val taxRaw       = bundle.getString("TaxAmount", "0")?.toDoubleOrNull() ?: 0.0

        PaymentIntentData(
            currencyISO             = bundle.getString("CurrencyISO"),
            tenderType              = bundle.getString("TenderType"),
            authorizationId         = bundle.getString("AuthorizationId")?.takeIf { it.isNotBlank() },
            tipAmount               = tipRaw / 100.0,
            shopDataXml             = bundle.getString("ShopData"),
            receiptPrinterColumns   = bundle.getInt("ReceiptPrinterColumns", 0),
            externalTransactionData = bundle.getString("TransactionData")?.takeIf { it.isNotBlank() },
            transactionType         = bundle.getString("TransactionType"),
            languageISO             = bundle.getString("LanguageISO"),
            taxAmount               = taxRaw / 100.0,
            taxDetailXml            = bundle.getString("TaxDetail"),
            posTransactionId        = bundle.getInt("TransactionId", 0),
            totalAmount             = amountRaw / 100.0
        ).also {
            Timber.i("Datos del Intent de pago parseados: $it")
        }
    } catch (e: Exception) {
        Timber.e(e, "Error al parsear los datos del Intent de pago.")
        null
    }
}
