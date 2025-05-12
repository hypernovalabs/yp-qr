package com.example.tefbanesco.screens

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.tefbanesco.storage.LocalStorage
import com.example.tefbanesco.network.ApiService
import com.example.tefbanesco.utils.toReadableString
import kotlinx.coroutines.launch
import timber.log.Timber
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

object TefTransactionResults {
    const val RESULT_ACCEPTED = "ACCEPTED"
    const val RESULT_FAILED = "FAILED"
    const val TYPE_SALE = "SALE"
}

class QrResultActivity : ComponentActivity() {
    private val df = DecimalFormat("0.00")

    // HioPOS Input
    private var originalHioPosTxnIdString: String = ""
    private var shopDataXml: String = ""
    private var printerCols: Int = 42
    private var tipFromHioPos: String = "0"
    private var taxFromHioPos: String = "0"
    private var currencyISO: String = ""
    private var transactionType: String = TefTransactionResults.TYPE_SALE

    // Yappy QR
    private var qrHash: String = ""
    private var yappyTransactionId: String = ""
    private var localOrderId: String = ""
    private var yappyDate: String = ""
    private var processedAmount: String = "0"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("[YAPPY] QResultActivity: onCreate")

        intent.extras?.let { extras ->
            Timber.d("[YAPPY] Datos recibidos: ${extras.toReadableString()}")

            originalHioPosTxnIdString = extras.getString("originalHioPosTransactionIdString").orEmpty()
            shopDataXml = extras.getString("ShopData").orEmpty()
            printerCols = extras.getInt("ReceiptPrinterColumns", 42)
            tipFromHioPos = extras.getString("TipAmount").orEmpty()
            taxFromHioPos = extras.getString("TaxAmount").orEmpty()
            currencyISO = extras.getString("CurrencyISO").orEmpty()
            transactionType = extras.getString("TransactionType").orEmpty()

            qrHash = extras.getString("qrHash").orEmpty()
            yappyTransactionId = extras.getString("qrTransactionId").orEmpty()
            localOrderId = extras.getString("localOrderId").orEmpty()

            processedAmount = extras.getString("qrAmount").orEmpty()
            yappyDate = extras.getString("qrDate").orEmpty()
        } ?: run {
            finishWithError("Error: datos faltantes")
            return
        }

        setContent {
            var showCancel by remember { mutableStateOf(false) }
            var showSuccess by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            when {
                showCancel -> CancelResultScreen(
                    title = "Pago Cancelado",
                    message = "Transacción Yappy ($yappyTransactionId) cancelada/falló.",
                    onConfirm = {}
                )
                showSuccess -> SuccessResultScreen(
                    title = "Pago Confirmado",
                    message = "Transacción Yappy ($yappyTransactionId) completada.",
                    onConfirm = {}
                )
                else -> QrResultScreen(
                    date = yappyDate,
                    transactionId = yappyTransactionId,
                    hash = qrHash,
                    amount = processedAmount,
                    onCancelSuccess = {
                        finishWithTransactionResult(
                            TefTransactionResults.RESULT_FAILED,
                            "Transacción cancelada"
                        )
                        showCancel = true
                    },
                    onPaymentSuccess = {
                        scope.launch { closeSession() }
                        showSuccess = true
                    }
                )
            }
        }
    }

    private suspend fun closeSession() {
        try {
            val cfg = LocalStorage.getConfig(this)
            ApiService.closeDeviceSession(
                token = cfg["device_token"].orEmpty(),
                apiKey = cfg["api_key"].orEmpty(),
                secretKey = cfg["secret_key"].orEmpty()
            )
            Timber.d("[YAPPY] Sesión cerrada correctamente")
        } catch (e: Exception) {
            Timber.e("[YAPPY] Error cerrando sesión: ${e.message}")
        } finally {
            LocalStorage.saveToken(this, "")
            finishWithTransactionResult(TefTransactionResults.RESULT_ACCEPTED)
        }
    }

    private fun finishWithTransactionResult(
        result: String,
        errorMessage: String? = null
    ) {
        Timber.d("[YAPPY] Preparando resultado: Result=$result, ErrorMsg=$errorMessage")

        // Formatear el monto sin puntos ni comas para HioPos
        val amountDouble = processedAmount.toDoubleOrNull() ?: 0.0
        val amountForHioPos = df.format(amountDouble)
            .replace(".", "")
            .replace(",", "")

        // Asegurarnos que tipFromHioPos y taxFromHioPos estén bien formateados (sin puntos ni comas)
        val formattedTipAmount = tipFromHioPos.replace(".", "").replace(",", "")
        val formattedTaxAmount = taxFromHioPos.replace(".", "").replace(",", "")

        // Crear TransactionData con formato yappyTransactionId/YP-localOrderId
        val structureTransactionData = "${yappyTransactionId}/YP-${localOrderId}"

        // Generar recibos
        val merchantReceipt = buildReceipt(
            numCols = printerCols,
            shopXml = shopDataXml,
            txnType = transactionType,
            txnId = yappyTransactionId,
            amount = processedAmount,
            currencyIso = currencyISO,
            date = yappyDate,
            result = result,
            errorMessage = errorMessage,
            originalPosId = originalHioPosTxnIdString
        )
        val customerReceipt = merchantReceipt

        // Crear intent de resultado
        val resultIntent = Intent("icg.actions.electronicpayment.tefbanesco.TRANSACTION")

        Timber.d("[YAPPY] Datos a enviar: resultado=$result, tipo=$transactionType, monto=$amountForHioPos, " +
                "transactionId=$originalHioPosTxnIdString, transactionData=$structureTransactionData")

        // Asegurar que TransactionId sea un String como en el módulo funcional
        resultIntent.putExtra("TransactionResult", result)
        resultIntent.putExtra("TransactionType", transactionType)
        resultIntent.putExtra("Amount", amountForHioPos)  // Ya sin puntos ni comas
        resultIntent.putExtra("TipAmount", formattedTipAmount)
        resultIntent.putExtra("TaxAmount", formattedTaxAmount)
        resultIntent.putExtra("TransactionData", structureTransactionData)

        // Asegurarnos que TransactionId sea un String
        resultIntent.putExtra("TransactionId", originalHioPosTxnIdString)
        resultIntent.putExtra("CurrencyISO", currencyISO)
        resultIntent.putExtra("MerchantReceipt", merchantReceipt)
        resultIntent.putExtra("CustomerReceipt", customerReceipt)

        // Removemos BatchNumber y TenderType que no son campos esperados en la respuesta

        // Añadir AuthorizationId, CardHolder, CardType, CardNum según el módulo funcional
        resultIntent.putExtra("AuthorizationId", yappyTransactionId)
        resultIntent.putExtra("CardHolder", "Cliente Yappy")
        resultIntent.putExtra("CardType", "Yappy")
        resultIntent.putExtra("CardNum", "********${localOrderId.takeLast(4)}")

        if (result == TefTransactionResults.RESULT_FAILED && errorMessage != null) {
            resultIntent.putExtra("ErrorMessage", errorMessage)
            resultIntent.putExtra("ErrorMessageTitle", "Error Pago Yappy")

            // API v3.5: Añadir ReceiptFailed para transacciones fallidas
            val failureReceipt = buildReceipt(
                numCols = printerCols,
                shopXml = shopDataXml,
                txnType = transactionType,
                txnId = yappyTransactionId.takeIf { it.isNotBlank() } ?: "N/A",
                amount = processedAmount,
                currencyIso = currencyISO,
                date = yappyDate,
                result = result,
                errorMessage = errorMessage,
                originalPosId = originalHioPosTxnIdString
            )
            resultIntent.putExtra("ReceiptFailed", failureReceipt)

            Timber.w("[YAPPY] Error: $errorMessage")
        }

        // No devolvemos DocumentData, ShopData, SellerData ni ReceiptPrinterColumns
        // Ya que son campos de entrada que HioPos no espera recibir de vuelta

        // Log compacto del bundle para depuración
        resultIntent.extras?.let {
            val fields = it.keySet().filter { key ->
                key != "MerchantReceipt" && key != "CustomerReceipt"
            }.joinToString(", ") { key -> "$key=${it.get(key)}" }

            Timber.d("[YAPPY] Bundle: $fields")
        }

        // VERIFICACIÓN MODO DEBUG: Para activar intent simplificado, cambiar 'false' por 'true'
        val useSimpleIntent = true // Cambiado a true para probar intent simplificado
        if (useSimpleIntent) {
            // Intent simplificado con solo los campos mínimos requeridos según la API
            val simpleIntent = Intent("icg.actions.electronicpayment.tefbanesco.TRANSACTION") // Con acción específica como requiere la API
            simpleIntent.putExtra("TransactionResult", result)
            simpleIntent.putExtra("TransactionType", transactionType)
            simpleIntent.putExtra("Amount", amountForHioPos)
            simpleIntent.putExtra("TransactionId", originalHioPosTxnIdString)
            simpleIntent.putExtra("TransactionData", structureTransactionData)
            simpleIntent.putExtra("AuthorizationId", yappyTransactionId)
            simpleIntent.putExtra("MerchantReceipt", merchantReceipt)
            simpleIntent.putExtra("CustomerReceipt", customerReceipt)

            Timber.d("[YAPPY] Usando intent simplificado con acción correcta según API")
            setResult(Activity.RESULT_OK, simpleIntent)
        } else {
            setResult(Activity.RESULT_OK, resultIntent)
        }

        finish()
        Timber.d("[YAPPY] QrResultActivity finalizada")
    }

    private fun finishWithError(message: String) {
        finishWithTransactionResult(TefTransactionResults.RESULT_FAILED, message)
    }

    private fun buildReceipt(
        numCols: Int,
        shopXml: String,
        txnType: String,
        txnId: String,
        amount: String,
        currencyIso: String,
        date: String,
        result: String,
        errorMessage: String?,
        originalPosId: String?
    ): String {
        val shopData = parseShopData(shopXml)
        val symbol = mapCurrencyISOToSymbol(currencyIso)
        val formattedDate = formatDisplayDateForReceipt(date, "dd-MM-yyyy HH:mm:ss")

        return buildString {
            append("<Receipt numCols=\"$numCols\">\n")
            append(buildLine("*** COPIA COMERCIO ***", numCols, "BOLD", true))
            append(buildLine(shopData["name"].orEmpty(), numCols, "BOLD", true))
            shopData["fiscalId"]?.let { append(buildLine("RIF/ID: $it", numCols, "BOLD", true)) }
            append(buildLine("-".repeat(numCols), numCols))
            append(buildLine("TRANSACCION: ${txnType.uppercase(Locale.ROOT)}", numCols))
            append(buildLine("FECHA: $formattedDate", numCols))
            append(buildLine("MONTO: ${df.format(amount.toDoubleOrNull() ?: 0.0)} $symbol", numCols, "BOLD"))
            append(buildLine("ID YAPPY: $txnId", numCols))
            originalPosId?.takeIf { it.isNotBlank() }?.let { append(buildLine("ID POS: $it", numCols)) }
            append(buildLine("-".repeat(numCols), numCols))
            append(buildLine("ESTADO: ${if (result == TefTransactionResults.RESULT_ACCEPTED) "APROBADA" else "FALLIDA"}", numCols, "BOLD"))
            errorMessage?.takeIf { it.isNotBlank() }?.let { append(buildLine("MENSAJE: $it", numCols)) }
            append(buildLine(" ", numCols))
            append(buildLine("GRACIAS POR SU PAGO CON YAPPY!", numCols, "NORMAL", true))
            append(buildLine(" ", numCols))
            append("</Receipt>")
        }
    }

    private fun buildLine(
        text: String,
        numCols: Int,
        format: String = "NORMAL",
        centered: Boolean = false
    ): String {
        var t = text.xmlEscape()
        if (centered && t.length < numCols) {
            val pad = (numCols - t.length) / 2
            t = " ".repeat(pad) + t
        }
        t = t.take(numCols)
        return "<ReceiptLine type=\"TEXT\">" +
                "<Formats><Format from=\"0\" to=\"$numCols\">$format</Format></Formats>" +
                "<Text>$t</Text></ReceiptLine>\n"
    }

    private fun parseShopData(xml: String): Map<String, String> {
        val data = mutableMapOf<String,String>()
        if (xml.isBlank()) return data
        try {
            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
            val parser = factory.newPullParser().apply { setInput(xml.reader()) }
            var tag: String? = null
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> tag = parser.name
                    XmlPullParser.TEXT -> parser.text.trim().let { if (tag != null) data[tag] = it }
                    XmlPullParser.END_TAG -> tag = null
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            Timber.e(e, "parseShopData error")
        }
        return data
    }

    private fun formatDisplayDateForReceipt(iso: String, pattern: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
            val date = parser.parse(iso) ?: return iso
            SimpleDateFormat(pattern, Locale.getDefault()).format(date)
        } catch (e: Exception) {
            iso
        }
    }

    private fun mapNumericISOToAlpha(numeric: String): String = when (numeric) {
        "590" -> "PAB"
        "840" -> "USD"
        else -> numeric
    }

    private fun mapCurrencyISOToSymbol(iso: String): String = when (mapNumericISOToAlpha(iso).uppercase()) {
        "PAB" -> "B/."
        "USD" -> "$"
        else -> iso
    }
}

fun String.xmlEscape(): String = this.replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

fun Bundle.toReadableString(): String {
    if (this.isEmpty) return "Bundle[empty]"
    val sb = StringBuilder("Bundle[")
    for (key in keySet()) sb.append(key).append("=").append(get(key)).append(", ")
    if (sb.endsWith(", ")) sb.setLength(sb.length - 2)
    sb.append("]")
    return sb.toString()
}
