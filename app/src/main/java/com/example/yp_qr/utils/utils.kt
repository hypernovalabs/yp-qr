// File: utils.kt
package com.example.tefbanesco.utils

import android.os.Bundle
// No necesitamos JSONObject aquí si TransactionData es un string simple
// import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * Construye una cadena para el campo TransactionData.
 * Formato: "YAPPY_TRANSACTION_ID/LOCAL_ORDER_ID" o solo "YAPPY_TRANSACTION_ID".
 * Truncada a 250 caracteres.
 *
 * @param yappyTransactionId El ID de la transacción de Yappy (opcional).
 * @param localOrderId El ID de orden local (opcional).
 * @return String formateada.
 */
fun buildTransactionDataString(
    yappyTransactionId: String?,
    localOrderId: String?
): String {
    val parts = mutableListOf<String>()
    yappyTransactionId?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    localOrderId?.takeIf { it.isNotBlank() }?.let { parts.add(it) }

    return parts.joinToString("/").take(250)
}

fun escapeXml(text: String): String {
    return text.replace("&", "&")
        .replace("<", "<")
        .replace(">", ">")
        .replace('\"', '"')
        .replace("'", "'")
}

fun parseShopData(xmlString: String?): Map<String, String> {
    val data = mutableMapOf<String, String>()
    if (xmlString.isNullOrBlank()) {
        Timber.w("parseShopData: XML de ShopData es nulo o vacío.")
        return data
    }
    try {
        Timber.d("parseShopData: Parseando XML de ShopData: ${xmlString.take(150)}...")
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser().apply { setInput(xmlString.reader()) }

        var eventType = parser.eventType
        var currentTagName: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTagName = parser.name
                }
                XmlPullParser.TEXT -> {
                    val textValue = parser.text?.trim()
                    if (currentTagName != null && !textValue.isNullOrBlank()) {
                        val key = if (currentTagName == "cityWithPostalCode") "city" else currentTagName
                        data[key] = textValue
                    }
                }
                XmlPullParser.END_TAG -> {
                    currentTagName = null
                }
            }
            eventType = parser.next()
        }
        Timber.d("parseShopData: Parseo de ShopData completado: $data")
    } catch (e: Exception) {
        Timber.e(e, "parseShopData: Error parseando XML de ShopData.")
    }
    return data
}

fun mapCurrencyISOToSymbol(isoCode: String?): String {
    if (isoCode.isNullOrBlank()) return ""
    val alphaISO = when (isoCode) {
        "840" -> "USD"
        "590" -> "PAB"
        else -> isoCode.uppercase(Locale.getDefault())
    }
    return when (alphaISO) {
        "USD" -> "$"
        "PAB" -> "B/."
        else -> alphaISO
    }
}

fun formatIsoDate(isoDateTimeString: String?, outputPattern: String): String {
    if (isoDateTimeString.isNullOrBlank()) return "N/A"
    return try {
        val parsers = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        )
        var date: Date? = null
        for (parser in parsers) {
            parser.timeZone = TimeZone.getTimeZone("UTC")
            try {
                date = parser.parse(isoDateTimeString)
                if (date != null) break
            } catch (_: Exception) {
            }
        }

        if (date != null) {
            val outputFormat = SimpleDateFormat(outputPattern, Locale.getDefault())
            outputFormat.format(date)
        } else {
            Timber.w("formatIsoDate: No se pudo parsear la fecha ISO: $isoDateTimeString con los formatos conocidos.")
            isoDateTimeString
        }
    } catch (e: Exception) {
        Timber.e(e, "formatIsoDate: Error formateando fecha ISO: $isoDateTimeString")
        isoDateTimeString
    }
}

fun bundleExtrasToString(bundle: Bundle?): String {
    if (bundle == null) return "Bundle[null]"
    if (bundle.isEmpty) return "Bundle[empty]"
    return bundle.keySet().joinToString(prefix = "Bundle[", postfix = "]") { key ->
        "$key=${bundle.get(key)}"
    }
}

// Necesitarás añadir PaymentIntentData aquí o importarla si está en otro archivo de utils
// Para mantenerlo autocontenido por ahora, lo pongo aquí. Si ya lo tienes en PaymentIntentParser.kt en el mismo paquete, esta duplicación no es necesaria.
data class PaymentIntentData(
    val currencyISO: String?,
    val tenderType: String?,
    val authorizationId: String?,
    val tipAmount: Double,
    val shopDataXml: String?,
    val receiptPrinterColumns: Int,
    val externalTransactionData: String?, // Este es el TransactionData de ENTRADA de HioPOS
    val transactionType: String?,
    val languageISO: String?,
    val taxAmount: Double,
    val taxDetailXml: String?,
    val posTransactionId: Int,
    val totalAmount: Double,
    val originalIntentAction: String? // << NUEVO: Para pasar la action original
)

fun parsePaymentIntent(intent: Intent): PaymentIntentData? {
    val bundle: Bundle? = intent.extras
    if (bundle == null) {
        Timber.w("El Intent no contiene extras. No se pueden parsear los datos de la transacción.")
        return null
    }

    return try {
        val amountRaw    = bundle.getString("Amount", "0")?.toDoubleOrNull() ?: 0.0
        val tipRaw       = bundle.getString("TipAmount", "0")?.toDoubleOrNull() ?: 0.0
        val taxRaw       = bundle.getString("TaxAmount", "0")?.toDoubleOrNull() ?: 0.0

        PaymentIntentData(
            currencyISO             = bundle.getString("CurrencyISO"),
            tenderType              = bundle.getString("TenderType"),
            authorizationId         = bundle.getString("AuthorizationId")?.takeIf { it.isNotBlank() },
            tipAmount               = tipRaw / 100.0,
            shopDataXml             = bundle.getString("ShopData"),
            receiptPrinterColumns   = bundle.getInt("ReceiptPrinterColumns", 42), // Default a 42 si no viene
            externalTransactionData = bundle.getString("TransactionData")?.takeIf { it.isNotBlank() },
            transactionType         = bundle.getString("TransactionType"),
            languageISO             = bundle.getString("LanguageISO"),
            taxAmount               = taxRaw / 100.0,
            taxDetailXml            = bundle.getString("TaxDetail"),
            posTransactionId        = bundle.getInt("TransactionId", 0),
            totalAmount             = amountRaw / 100.0,
            originalIntentAction    = intent.action // << NUEVO: Guardar la action original
        ).also {
            Timber.i("Datos del Intent de pago parseados (incluyendo action original): $it")
        }
    } catch (e: Exception) {
        Timber.e(e, "Error al parsear los datos del Intent de pago.")
        null
    }
}