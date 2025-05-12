
// AJUSTA TU PAQUETE REAL (ej. com.example.yappy.screens)
package com.example.tefbanesco.screens

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.tefbanesco.storage.LocalStorage // AJUSTA TU PAQUETE REAL
import com.example.tefbanesco.network.ApiService // AJUSTA TU PAQUETE REAL
import com.example.tefbanesco.utils.toReadableString // Importa tu función helper
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TefTransactionResults {
    const val RESULT_ACCEPTED = "ACCEPTED"
    const val RESULT_FAILED = "FAILED"
    const val TYPE_SALE = "SALE"
}

class QrResultActivity : ComponentActivity() {

    private var yappyTransactionId: String = ""
    private var localOrderId: String = ""
    private var transactionDateFromYappy: String = ""
    private var transactionAmountProcessed: String = ""
    private var originalHioPosTxnId: Int = 0
    private var qrHashFromIntent: String = ""

    private var shopDataXmlFromHioPos: String = ""
    private var printerColsFromHioPos: Int = 42
    private var tipAmountFromHioPos: String = "0"
    private var taxAmountFromHioPos: String = "0"
    private var currencyISOFromHioPos: String = ""
    private var transactionTypeFromHioPos: String = TefTransactionResults.TYPE_SALE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("🏁 QrResultActivity: onCreate")

        intent.extras?.let { extras ->
            Timber.d("  QrResultActivity: Extras recibidos -> ${extras.toReadableString()}")
            transactionDateFromYappy    = extras.getString("qrDate") ?: ""
            yappyTransactionId          = extras.getString("qrTransactionId") ?: ""
            localOrderId                = extras.getString("localOrderId") ?: ""
            transactionAmountProcessed  = extras.getString("qrAmount") ?: "0.0"
            originalHioPosTxnId         = extras.getInt("originalHioPosTransactionId", 0)
            qrHashFromIntent            = extras.getString("qrHash") ?: ""
            shopDataXmlFromHioPos       = extras.getString("ShopData") ?: ""
            printerColsFromHioPos       = extras.getInt("ReceiptPrinterColumns", 42)
            tipAmountFromHioPos         = extras.getString("TipAmount") ?: "0"
            taxAmountFromHioPos         = extras.getString("TaxAmount") ?: "0"
            currencyISOFromHioPos       = extras.getString("CurrencyISO") ?: ""
            transactionTypeFromHioPos   = extras.getString("TransactionType") ?: TefTransactionResults.TYPE_SALE
        } ?: run {
            Timber.e("❌ QrResultActivity: Intent sin extras. Finalizando con error.")
            finishWithError("Error interno: Datos de transacción perdidos por QrResultActivity.", 0)
            return
        }

        Timber.i("📄 QrResultActivity: Datos para procesar -> HioPosTxnId=$originalHioPosTxnId, YappyTxnId=$yappyTransactionId, AmountYappy=$transactionAmountProcessed, CurrencyHioPos=$currencyISOFromHioPos, TipHioPos=$tipAmountFromHioPos, TaxHioPos=$taxAmountFromHioPos")

        if (qrHashFromIntent.isBlank()) {
            Timber.e("❌ QrResultActivity: qrHash vacío. Finalizando.")
            finishWithError("Error: QR no disponible para mostrar.", originalHioPosTxnId)
            return
        }
        if (yappyTransactionId.isBlank() && originalHioPosTxnId == 0) {
            Timber.e("❌ QrResultActivity: IDs críticos no disponibles. Finalizando.")
            finishWithError("Error: IDs de transacción no disponibles.", 0)
            return
        }

        setContent {
            val scope = rememberCoroutineScope()
            var showCancelScreen by remember { mutableStateOf(false) }
            var showSuccessScreen by remember { mutableStateOf(false) }

            when {
                showCancelScreen -> CancelResultScreen(
                    title = "Pago Cancelado",
                    message = "Transacción Yappy ($yappyTransactionId) cancelada/falló.",
                    onConfirm = {
                        Timber.d("UI: CancelScreen confirmada.")
                        // No llamamos a finish() aquí - ya lo hizo finishWithTransactionResult
                    }
                )
                showSuccessScreen -> SuccessResultScreen(
                    title = "Pago Confirmado",
                    message = "Transacción Yappy ($yappyTransactionId) completada.",
                    onConfirm = {
                        Timber.d("UI: SuccessScreen confirmada.")
                        // No llamamos a finish() aquí - ya lo hizo finishWithTransactionResult
                    }
                )
                else -> QrResultScreen(
                    date = transactionDateFromYappy, transactionId = yappyTransactionId,
                    hash = qrHashFromIntent, amount = transactionAmountProcessed,
                    onCancelSuccess = {
                        Timber.i("🔔 QrResultActivity: onCancelSuccess para YappyTxnId=$yappyTransactionId")
                        finishWithTransactionResult(TefTransactionResults.RESULT_FAILED, "Transacción Yappy cancelada/fallida/expirada.")
                        showCancelScreen = true
                    },
                    onPaymentSuccess = {
                        Timber.i("🔔 QrResultActivity: onPaymentSuccess para YappyTxnId=$yappyTransactionId")
                        scope.launch {
                            try {
                                val cfg = LocalStorage.getConfig(this@QrResultActivity)
                                val token = cfg["device_token"].orEmpty()
                                val apiKey = cfg["api_key"].orEmpty()
                                val secretKey = cfg["secret_key"].orEmpty()
                                if (token.isNotBlank()) {
                                    Timber.d("⏳ QrResultActivity: Cerrando sesión Yappy...")
                                    ApiService.closeDeviceSession(token, apiKey, secretKey)
                                    Timber.i("🔒 QrResultActivity: Sesión Yappy cerrada.")
                                } else {
                                    Timber.w("⚠️ QrResultActivity: No token Yappy para cerrar.")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "⚠️ QrResultActivity: Error cerrando sesión Yappy.")
                            }

                            LocalStorage.saveToken(this@QrResultActivity, "")
                            Timber.d("🧹 QrResultActivity: Token Yappy borrado.")

                            // IMPORTANTE: Finalizar con resultado exitoso
                            finishWithTransactionResult(TefTransactionResults.RESULT_ACCEPTED)
                            showSuccessScreen = true
                        }
                    }
                )
            }
        }
    }

    private fun buildReceiptLine(text: String, format: String = "BOLD", centered: Boolean = false): String {
        var lineText = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val maxLength = printerColsFromHioPos
        if (centered) {
            val textLength = lineText.length
            if (textLength < maxLength) {
                val padding = (maxLength - textLength) / 2
                if (padding > 0) lineText = " ".repeat(padding) + lineText
            }
        }
        lineText = lineText.take(maxLength)
        return """
        |  <ReceiptLine type="TEXT">
        |    <Formats><Format from="0" to="$maxLength">$format</Format></Formats>
        |    <Text>$lineText</Text>
        |  </ReceiptLine>
        """.trimMargin() + "\n"
    }

    private fun finishWithTransactionResult(result: String, errorMessage: String? = null) {
        Timber.d("🚀 QrResultActivity.finishWithTransactionResult: Preparando respuesta. Result: $result, HioPosTxnId: $originalHioPosTxnId")

        val shopInfo = parseShopData(shopDataXmlFromHioPos)
        val currencySymbolForReceipt = mapCurrencyISOToSymbol(currencyISOFromHioPos)
        val displayDateForReceipt = formatDisplayDateForReceipt(transactionDateFromYappy, "dd-MM-yyyy hh:mm:ss")

        val merchantReceiptXml = buildString {
            append("<Receipt numCols=\"$printerColsFromHioPos\">\n")
            append(buildReceiptLine(" ", "NORMAL")) // Línea vacía inicial
            append(buildReceiptLine("*** COPIA COMERCIO ***", "BOLD", centered = true))
            append(buildReceiptLine(shopInfo["name"] ?: "COMERCIO", "BOLD", centered = true))
            shopInfo["fiscalId"]?.takeIf { it.isNotBlank() }?.let {
                append(buildReceiptLine("RIF/ID: $it", "BOLD", centered = true))
            }

            append(buildReceiptLine("-".repeat(printerColsFromHioPos), "BOLD"))

            append(buildReceiptLine("TRANSACCION: ${transactionTypeFromHioPos.uppercase(Locale.ROOT)} YAPPY", "BOLD"))
            append(buildReceiptLine("FECHA: $displayDateForReceipt", "BOLD"))

            val formato = DecimalFormat("0.00")
            val amountDblReceipt = transactionAmountProcessed.toDoubleOrNull() ?: 0.0
            val amountFormatted = formato.format(amountDblReceipt)
            append(buildReceiptLine("MONTO: $amountFormatted $currencySymbolForReceipt", "BOLD"))

            append(buildReceiptLine("ID YAPPY: $yappyTransactionId", "BOLD"))
            if(originalHioPosTxnId != 0) append(buildReceiptLine("ID POS: $originalHioPosTxnId", "BOLD"))

            val estadoTxt = if(result == TefTransactionResults.RESULT_ACCEPTED) "APROBADA" else "FALLIDA"
            append(buildReceiptLine("ESTADO: $estadoTxt", "BOLD"))

            if(result == TefTransactionResults.RESULT_FAILED && !errorMessage.isNullOrBlank()){
                append(buildReceiptLine("ERROR: $errorMessage", "BOLD"))
            }

            append(buildReceiptLine(" ", "NORMAL"))
            append(buildReceiptLine("Gracias por su compra!", "BOLD", centered = true))
            append(buildReceiptLine(" ", "NORMAL"))

            // CUT_PAPER correctamente formateado según BAC
            append("""
            |  <ReceiptLine type="CUT_PAPER">
            |    <Formats><Format from="0" to="$printerColsFromHioPos">BOLD</Format></Formats>
            |    <Text>                                          </Text>
            |  </ReceiptLine>
            """.trimMargin())
            append("\n</Receipt>")
        }.also { Timber.v("📄 QrResultActivity: MerchantReceipt XML:\n$it") }

        val customerReceiptXml = merchantReceiptXml.replace("*** COPIA COMERCIO ***", "*** COPIA CLIENTE ***")
            .replace("RIF/ID:", " ")
            .replace(Regex("ID POS:.*(\r\n|\r|\n)"), " ")

        // CORRECCIÓN: Formatear los montos correctamente para HioPOS
        val formato = DecimalFormat("0.00")
        val amountDouble = transactionAmountProcessed.toDoubleOrNull() ?: 0.0

        // CORRECCIÓN: asegurar que el formato sea correcto - sin punto decimal
        val amountMainForHioPos = formato.format(amountDouble).replace(".", "")

        // CORRECCIÓN: asegurar que el intent tenga la acción correcta
        val resultIntent = Intent("icg.actions.electronicpayment.tefbanesco.TRANSACTION").apply {
            putExtra("TransactionResult", result)
            putExtra("TransactionType", transactionTypeFromHioPos)
            putExtra("TransactionId", originalHioPosTxnId)
            putExtra("Amount", amountMainForHioPos)
            putExtra("TipAmount", tipAmountFromHioPos)
            putExtra("TaxAmount", taxAmountFromHioPos)

            if (currencyISOFromHioPos.isNotBlank()) {
                putExtra("CurrencyISO", currencyISOFromHioPos)
            }

            // CORRECCIÓN: Formato correcto para TransactionData siguiendo la estructura de BAC
            val transactionDataToReturn = "$yappyTransactionId/$localOrderId"
            Timber.d("  Usando para TransactionData: '$transactionDataToReturn'")
            putExtra("TransactionData", transactionDataToReturn)

            putExtra("MerchantReceipt", merchantReceiptXml)
            putExtra("CustomerReceipt", customerReceiptXml)

            if (result == TefTransactionResults.RESULT_FAILED && !errorMessage.isNullOrBlank()) {
                putExtra("ErrorMessage", errorMessage)
            }
        }

        // CORRECCIÓN: Corregir el log para referirse al intent correcto
        Timber.d("resultIntent -> ${resultIntent.extras?.toReadableString()}")

        Timber.i("📬 QrResultActivity: Respuesta final para Hiopos -> ${resultIntent.extras?.toReadableString()}")

        // Imprimir extras en orden definido
        val extras = resultIntent.extras
        val orderedKeys = listOf(
            "TransactionResult", "TransactionType", "TransactionId",
            "Amount", "TipAmount", "TaxAmount", "CurrencyISO",
            "TransactionData",
            "ErrorMessage","MerchantReceipt", "CustomerReceipt"
        )
        if (extras != null) {
            Timber.i("📬 QrResultActivity: Respuesta final para Hiopos -> Extras en orden:")
            orderedKeys.forEach { key ->
                if (extras.containsKey(key)) {
                    val value = extras.get(key)
                    Timber.i("Hiopos -> Extras  ↳ $key: $value")
                }
            }
        } else {
            Timber.i("📬 QrResultActivity: El Bundle de extras es nulo.")
        }

        // IMPORTANTE: Configurar el resultado y finalizar la actividad
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun parseShopData(xmlString: String): Map<String, String> {
        val data = mutableMapOf<String, String>()
        if (xmlString.isBlank()) { Timber.w("parseShopData: XML ShopData vacío."); return data }
        try {
            Timber.d("parseShopData: Parseando: ${xmlString.take(100)}...")
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser().apply { setInput(xmlString.reader()) }
            var eventType = parser.eventType; var currentTagName: String? = null
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> currentTagName = parser.name
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim()
                        if (currentTagName != null && !text.isNullOrBlank()) {
                            val key = if (currentTagName == "cityWithPostalCode") "city" else currentTagName
                            data[key] = text
                        }
                    }
                    XmlPullParser.END_TAG -> currentTagName = null
                }
                eventType = parser.next()
            }
            Timber.d("parseShopData: Completado: $data")
        } catch (e: Exception) { Timber.e(e, "parseShopData: Error.") }
        return data
    }

    private fun formatDisplayDateForReceipt(isoDate: String?, desiredFormat: String = "dd/MM/yyyy HH:mm:ss"): String {
        if (isoDate.isNullOrBlank()) return "N/A"
        return try {
            val inputFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val dateObj = inputFmt.parse(isoDate)
            val outputFmt = SimpleDateFormat(desiredFormat, Locale.getDefault())
            dateObj?.let { outputFmt.format(it) } ?: isoDate
        } catch (e: Exception) { Timber.w(e, "formatDisplayDate Error: $isoDate"); isoDate }
    }

    private fun mapNumericISOToAlpha(numericISO: String?): String {
        if (numericISO.isNullOrBlank()) return ""
        return when (numericISO) {
            "590" -> "PAB"; "840" -> "USD"
            "PAB", "USD" -> numericISO
            else -> numericISO
        }
    }

    private fun mapCurrencyISOToSymbol(isoAlphaOrNumeric: String): String {
        val alphaISO = mapNumericISOToAlpha(isoAlphaOrNumeric)
        return when (alphaISO.uppercase(Locale.US)) {
            "PAB" -> "B/."; "USD" -> "$"
            else -> alphaISO
        }
    }

    private fun finishWithError(errorMessage: String, hioPosTxnIdToReturn: Int = originalHioPosTxnId) {
        Timber.e("‼️ QrResultActivity.finishWithError: HioPosTxnId=$hioPosTxnIdToReturn, Error='$errorMessage'")

        // CORRECCIÓN: Usar la acción correcta en el intent
        val resultIntent = Intent("icg.actions.electronicpayment.tefbanesco.TRANSACTION").apply {
            putExtra("TransactionResult", TefTransactionResults.RESULT_FAILED)
            putExtra("TransactionType", transactionTypeFromHioPos)
            putExtra("ErrorMessage", errorMessage)
            if (hioPosTxnIdToReturn != 0) putExtra("TransactionId", hioPosTxnIdToReturn)

            // CORRECCIÓN: Unificar formato para TransactionData
            val transactionDataToReturn = if (yappyTransactionId.isNotBlank()) {
                "$yappyTransactionId/$localOrderId"
            } else {
                "ERROR/$localOrderId"
            }
            putExtra("TransactionData", transactionDataToReturn)

            // CORRECCIÓN: Formatear monto correctamente como en BAC (sin punto decimal)
            val formato = DecimalFormat("0.00")
            val amountDbl = transactionAmountProcessed.toDoubleOrNull() ?: 0.0
            val amountFormatted = formato.format(amountDbl).replace(".", "")
            putExtra("Amount", amountFormatted)

            // CORRECCIÓN: Incluir todos los extras esperados por HIOPOS
            putExtra("TipAmount", tipAmountFromHioPos)
            putExtra("TaxAmount", taxAmountFromHioPos)

            if (currencyISOFromHioPos.isNotBlank()) {
                putExtra("CurrencyISO", currencyISOFromHioPos)
            }
        }

        Timber.i("  📤 QrResultActivity.finishWithError: Enviando error a Hiopos: ${resultIntent.extras?.toReadableString()}")
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}

// Extension function para mejorar la calidad del XML generado
fun String.xmlEscape(): String {
    return this.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
