//// AJUSTA TU PAQUETE REAL (ej. com.example.yappy.screens)
//package com.example.tefbanesco.screens
//
//import android.app.Activity
//import android.content.Intent
//import android.os.Bundle
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.compose.runtime.*
//import com.example.tefbanesco.storage.LocalStorage // AJUSTA TU PAQUETE REAL
//import com.example.tefbanesco.network.ApiService // AJUSTA TU PAQUETE REAL
//import com.example.tefbanesco.utils.toReadableString // Importa tu función helper
//import kotlinx.coroutines.launch
//import org.json.JSONObject
//import timber.log.Timber
//import org.xmlpull.v1.XmlPullParser
//import org.xmlpull.v1.XmlPullParserFactory
//import java.text.SimpleDateFormat
//import java.util.Date
//import java.util.Locale
//import java.util.TimeZone
//
//// Mueve TefTransactionResults a un archivo utils si no lo has hecho
//object TefTransactionResults {
//    const val RESULT_ACCEPTED = "ACCEPTED"
//    const val RESULT_FAILED = "FAILED"
//    const val TYPE_SALE = "SALE"
//}
//
//class QrResultActivity : ComponentActivity() {
//
//    private var yappyTransactionId: String = ""
//    private var localOrderId: String = ""
//    private var transactionDateFromYappy: String = ""
//    private var transactionAmountProcessed: String = ""
//    private var originalHioPosTxnId: Int = 0
//    private var qrHashFromIntent: String = ""
//
//    private var shopDataXmlFromHioPos: String = ""
//    private var printerColsFromHioPos: Int = 42
//    private var tipAmountFromHioPos: String = "0"
//    private var taxAmountFromHioPos: String = "0"
//    private var currencyISOFromHioPos: String = ""
//    private var transactionTypeFromHioPos: String = TefTransactionResults.TYPE_SALE
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        Timber.d("🏁 QrResultActivity: onCreate")
//
//        intent.extras?.let { extras ->
//            Timber.d("  QrResultActivity: Extras recibidos -> ${extras.toReadableString()}")
//            transactionDateFromYappy    = extras.getString("qrDate") ?: ""
//            yappyTransactionId          = extras.getString("qrTransactionId") ?: ""
//            localOrderId                = extras.getString("localOrderId") ?: ""
//            transactionAmountProcessed  = extras.getString("qrAmount") ?: "0.0"
//            originalHioPosTxnId         = extras.getInt("originalHioPosTransactionId", 0)
//            qrHashFromIntent            = extras.getString("qrHash") ?: ""
//            shopDataXmlFromHioPos       = extras.getString("ShopData") ?: ""
//            printerColsFromHioPos       = extras.getInt("ReceiptPrinterColumns", 42)
//            tipAmountFromHioPos         = extras.getString("TipAmount") ?: "0"
//            taxAmountFromHioPos         = extras.getString("TaxAmount") ?: "0"
//            currencyISOFromHioPos       = extras.getString("CurrencyISO") ?: ""
//            transactionTypeFromHioPos   = extras.getString("TransactionType") ?: TefTransactionResults.TYPE_SALE
//        } ?: run {
//            Timber.e("❌ QrResultActivity: Intent sin extras. Finalizando con error.")
//            finishWithError("Error interno: Datos de transacción perdidos por QrResultActivity.", 0)
//            return
//        }
//
//        Timber.i("📄 QrResultActivity: Datos para procesar -> HioPosTxnId=$originalHioPosTxnId, YappyTxnId=$yappyTransactionId, AmountYappy=$transactionAmountProcessed, CurrencyHioPos=$currencyISOFromHioPos, TipHioPos=$tipAmountFromHioPos, TaxHioPos=$taxAmountFromHioPos")
//
//        if (qrHashFromIntent.isBlank()) {
//            Timber.e("❌ QrResultActivity: qrHash vacío. Finalizando.")
//            finishWithError("Error: QR no disponible para mostrar.", originalHioPosTxnId)
//            return
//        }
//        if (yappyTransactionId.isBlank() && originalHioPosTxnId == 0) {
//            Timber.e("❌ QrResultActivity: IDs críticos (Yappy y Hiopos) no disponibles. Finalizando.")
//            finishWithError("Error: IDs de transacción no disponibles.", 0)
//            return
//        }
//
//        setContent {
//            val scope = rememberCoroutineScope()
//            var showCancelScreen by remember { mutableStateOf(false) }
//            var showSuccessScreen by remember { mutableStateOf(false) }
//
//            when {
//                showCancelScreen -> CancelResultScreen(
//                    title = "Pago Cancelado",
//                    message = "Transacción Yappy ($yappyTransactionId) cancelada/falló.",
//                    onConfirm = { Timber.d("UI: CancelScreen confirmada."); finish() }
//                )
//                showSuccessScreen -> SuccessResultScreen(
//                    title = "Pago Confirmado",
//                    message = "Transacción Yappy ($yappyTransactionId) completada.",
//                    onConfirm = { Timber.d("UI: SuccessScreen confirmada."); finish() }
//                )
//                else -> QrResultScreen(
//                    date = transactionDateFromYappy, transactionId = yappyTransactionId,
//                    hash = qrHashFromIntent, amount = transactionAmountProcessed,
//                    onCancelSuccess = {
//                        Timber.i("🔔 QrResultActivity: onCancelSuccess para YappyTxnId=$yappyTransactionId")
//                        finishWithTransactionResult(TefTransactionResults.RESULT_FAILED, "Transacción Yappy cancelada/fallida/expirada.")
//                        showCancelScreen = true
//                    },
//                    onPaymentSuccess = {
//                        Timber.i("🔔 QrResultActivity: onPaymentSuccess para YappyTxnId=$yappyTransactionId")
//                        scope.launch {
//                            try {
//                                val cfg = LocalStorage.getConfig(this@QrResultActivity)
//                                val token = cfg["device_token"].orEmpty(); val apiKey = cfg["api_key"].orEmpty(); val secretKey = cfg["secret_key"].orEmpty()
//                                if (token.isNotBlank()) {
//                                    Timber.d("⏳ QrResultActivity: Cerrando sesión Yappy...")
//                                    ApiService.closeDeviceSession(token, apiKey, secretKey)
//                                    Timber.i("🔒 QrResultActivity: Sesión Yappy cerrada.")
//                                } else Timber.w("⚠️ QrResultActivity: No token Yappy para cerrar.")
//                            } catch (e: Exception) { Timber.e(e, "⚠️ QrResultActivity: Error cerrando sesión Yappy.") }
//                            LocalStorage.saveToken(this@QrResultActivity, "")
//                            Timber.d("🧹 QrResultActivity: Token Yappy borrado.")
//                            finishWithTransactionResult(TefTransactionResults.RESULT_ACCEPTED)
//                            showSuccessScreen = true
//                        }
//                    }
//                )
//            }
//        }
//    }
//
//    private fun buildReceiptLine(text: String, format: String = "NORMAL", centered: Boolean = false): String {
//        var lineText = text.replace("&", "&").replace("<", "<").replace(">", ">")
//        val maxLength = printerColsFromHioPos
//        if (centered) {
//            val textLength = lineText.length
//            if (textLength < maxLength) {
//                val padding = (maxLength - textLength) / 2
//                if (padding > 0) lineText = " ".repeat(padding) + lineText
//            }
//        }
//        lineText = lineText.take(maxLength)
//        return """
//        |  <ReceiptLine type="TEXT">
//        |    <Formats><Format from="0" to="$maxLength">$format</Format></Formats>
//        |    <Text>$lineText</Text>
//        |  </ReceiptLine>
//        """.trimMargin() + "\n"
//    }
//
//    private fun finishWithTransactionResult(result: String, errorMessage: String? = null) {
//        Timber.d("🚀 QrResultActivity.finishWithTransactionResult: Preparando respuesta. Result: $result, HioPosTxnId: $originalHioPosTxnId")
//
//        val shopInfo = parseShopData(shopDataXmlFromHioPos)
//        val currencySymbolForReceipt = mapCurrencyISOToSymbol(currencyISOFromHioPos) // Usa el ISO original
//        val displayDateForReceipt = formatDisplayDateForReceipt(transactionDateFromYappy)
//
//        val merchantReceiptXml = buildString {
//            append("<Receipt numCols=\"$printerColsFromHioPos\">\n")
//            append(buildReceiptLine("*** COPIA COMERCIO ***", "BOLD", centered = true))
//            shopInfo["name"]?.takeIf{it.isNotBlank()}?.let{append(buildReceiptLine("TIENDA: $it", centered = true))}
//            shopInfo["fiscalId"]?.takeIf{it.isNotBlank()}?.let{append(buildReceiptLine("RIF/ID: $it", centered = true))}
//            shopInfo["address"]?.takeIf{it.isNotBlank()}?.let{append(buildReceiptLine("DIRECCION: $it"))}
//            shopInfo["city"]?.takeIf{it.isNotBlank()}?.let{append(buildReceiptLine("CIUDAD: $it"))} // Asumiendo que parseShopData ahora lo pone en "city"
//            shopInfo["phone"]?.takeIf{it.isNotBlank()}?.let{append(buildReceiptLine("TELEFONO: $it"))}
//            append(buildReceiptLine("-".repeat(printerColsFromHioPos)))
//            append(buildReceiptLine("TRANSACCION: ${transactionTypeFromHioPos.uppercase(Locale.ROOT)} YAPPY"))
//            append(buildReceiptLine("FECHA: $displayDateForReceipt"))
//            val amountDblReceipt = transactionAmountProcessed.toDoubleOrNull() ?: 0.0
//            append(buildReceiptLine("MONTO: ${"%.2f".format(amountDblReceipt)} $currencySymbolForReceipt"))
//            append(buildReceiptLine("ID YAPPY: $yappyTransactionId"))
//            if(originalHioPosTxnId != 0) append(buildReceiptLine("ID POS: $originalHioPosTxnId"))
//            val estadoTxt = if(result == TefTransactionResults.RESULT_ACCEPTED) "APROBADA" else "FALLIDA"
//            append(buildReceiptLine("ESTADO: $estadoTxt", "BOLD"))
//            if(result == TefTransactionResults.RESULT_FAILED && !errorMessage.isNullOrBlank()){
//                append(buildReceiptLine("ERROR: $errorMessage"))
//            }
//            append(buildReceiptLine(" "))
//            append(buildReceiptLine("Gracias por su compra!", centered = true))
//            append(buildReceiptLine(" "))
//            append("</Receipt>")
//        }.also { Timber.v("📄 QrResultActivity: MerchantReceipt XML:\n$it") }
//
//        val customerReceiptXml = "<Receipt numCols=\"$printerColsFromHioPos\"></Receipt>"
//        val batchReceiptXml = "<Receipt numCols=\"$printerColsFromHioPos\"></Receipt>"
//
//        val resultIntent = Intent().apply {
//            putExtra("TransactionResult", result)
//            putExtra("TransactionType", transactionTypeFromHioPos)
//            if (originalHioPosTxnId != 0) putExtra("TransactionId", originalHioPosTxnId)
//
//            val amountDouble = transactionAmountProcessed.toDoubleOrNull() ?: 0.0
//            val amountMainForHioPos = (amountDouble * 100).toInt().toString()
//            putExtra("Amount", amountMainForHioPos)
//
//            putExtra("TipAmount", tipAmountFromHioPos) // Ya es string de centavos
//            putExtra("TaxAmount", taxAmountFromHioPos) // Ya es string de centavos
//
//            // Enviar CurrencyISO numérico original, Hiopos debe saber interpretarlo.
//            // Si Hiopos *requiere* alfabético, entonces el mapeo es necesario.
//            // Por ahora, enviamos el original que recibimos de Hiopos.
//            if (currencyISOFromHioPos.isNotBlank()) {
//                putExtra("CurrencyISO", currencyISOFromHioPos)
//                Timber.d("  Enviando CurrencyISO (original numérico o alfa): $currencyISOFromHioPos")
//            } else {
//                Timber.w("  CurrencyISO original de Hiopos está vacío, no se enviará.")
//            }
//
//            // ¡¡¡CONFIRMAR ESTE FORMATO CON HIOPOS PARA YAPPY!!!
//            val transactionDataToReturn = "$yappyTransactionId/$localOrderId"
//            putExtra("TransactionData", transactionDataToReturn.take(250))
//
//            putExtra("MerchantReceipt", merchantReceiptXml)
//            putExtra("CustomerReceipt", customerReceiptXml)
//            putExtra("BatchReceipt", batchReceiptXml)
//
//            if (result == TefTransactionResults.RESULT_FAILED && !errorMessage.isNullOrBlank()) {
//                putExtra("ErrorMessage", errorMessage)
//            }
//        }
//
//        Timber.i("📬 QrResultActivity: Respuesta final para Hiopos -> ${resultIntent.extras.toReadableString()}")
//        setResult(Activity.RESULT_OK, resultIntent)
//    }
//
//    private fun parseShopData(xmlString: String): Map<String, String> {
//        val data = mutableMapOf<String, String>()
//        if (xmlString.isBlank()) { Timber.w("parseShopData: XML ShopData vacío."); return data }
//        try {
//            Timber.d("parseShopData: Parseando: ${xmlString.take(100)}...")
//            val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
//            val parser = factory.newPullParser().apply { setInput(xmlString.reader()) }
//            var eventType = parser.eventType
//            var currentTagName: String? = null
//            while (eventType != XmlPullParser.END_DOCUMENT) {
//                when (eventType) {
//                    XmlPullParser.START_TAG -> {
//                        currentTagName = parser.name
//                        // Timber.v("    Parser START_TAG: $currentTagName") // Puede ser muy verboso
//                    }
//                    XmlPullParser.TEXT -> {
//                        val text = parser.text?.trim()
//                        if (currentTagName != null && !text.isNullOrBlank()) {
//                            // Mapear "cityWithPostalCode" a "city" si así lo usas en el recibo
//                            if (currentTagName == "cityWithPostalCode") {
//                                data["city"] = text
//                            } else {
//                                data[currentTagName] = text
//                            }
//                            // Timber.v("    Parsed tag '$currentTagName': $text") // Puede ser muy verboso
//                        }
//                    }
//                    XmlPullParser.END_TAG -> currentTagName = null
//                }
//                eventType = parser.next()
//            }
//            Timber.d("parseShopData: Completado: $data")
//        } catch (e: Exception) { Timber.e(e, "parseShopData: Error.") }
//        return data
//    }
//
//    private fun formatDisplayDateForReceipt(isoDate: String?): String {
//        if (isoDate.isNullOrBlank()) return "N/A"
//        return try {
//            val inputFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
//            val dateObj = inputFmt.parse(isoDate)
//            val outputFmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
//            dateObj?.let { outputFmt.format(it) } ?: isoDate
//        } catch (e: Exception) { Timber.w(e, "formatDisplayDate Error: $isoDate"); isoDate }
//    }
//
//    private fun mapNumericISOToAlpha(numericISO: String?): String { // Usado solo para el SÍMBOLO en el recibo
//        if (numericISO.isNullOrBlank()) return ""
//        // Timber.d("mapNumericISOToAlpha (para símbolo recibo): Mapeando ISO numérico '$numericISO'")
//        return when (numericISO) {
//            "590" -> "PAB"; "840" -> "USD"
//            "PAB", "USD" -> numericISO
//            else -> numericISO
//        }
//    }
//
//    private fun mapCurrencyISOToSymbol(isoAlphaOrNumeric: String): String { // Usado para el recibo
//        // Timber.d("mapCurrencyISOToSymbol: Mapeando ISO '$isoAlphaOrNumeric' a símbolo")
//        val alphaISO = mapNumericISOToAlpha(isoAlphaOrNumeric) // Asegura que sea alfa si hay mapeo
//        return when (alphaISO.uppercase(Locale.US)) {
//            "PAB" -> "B/."; "USD" -> "$"
//            else -> alphaISO.also { Timber.w("  Símbolo no encontrado para '$it', usando ISO alfa.") }
//        }
//    }
//
//    private fun finishWithError(errorMessage: String, hioPosTxnIdToReturn: Int = originalHioPosTxnId) {
//        Timber.e("‼️ QrResultActivity.finishWithError: HioPosTxnId=$hioPosTxnIdToReturn, Error='$errorMessage'")
//        val resultIntent = Intent().apply {
//            putExtra("TransactionResult", TefTransactionResults.RESULT_FAILED)
//            putExtra("TransactionType", transactionTypeFromHioPos)
//            putExtra("ErrorMessage", errorMessage)
//            if (hioPosTxnIdToReturn != 0) putExtra("TransactionId", hioPosTxnIdToReturn)
//
//            val txData = JSONObject()
//            if (yappyTransactionId.isNotBlank()) txData.put("yappyTransactionId", yappyTransactionId)
//            if (localOrderId.isNotBlank()) txData.put("localOrderId", localOrderId)
//            putExtra("TransactionData", txData.toString().take(250))
//
//            val amountDbl = transactionAmountProcessed.toDoubleOrNull() ?: 0.0
//            putExtra("Amount", (amountDbl * 100).toInt().toString())
//            putExtra("TipAmount", tipAmountFromHioPos)
//            putExtra("TaxAmount", taxAmountFromHioPos)
//
//            // Para errores, enviar el CurrencyISO original que vino de Hiopos
//            if (currencyISOFromHioPos.isNotBlank()) {
//                putExtra("CurrencyISO", currencyISOFromHioPos)
//            }
//        }
//        Timber.i("  📤 QrResultActivity.finishWithError: Enviando error a Hiopos: ${resultIntent.extras.toReadableString()}")
//        setResult(Activity.RESULT_OK, resultIntent)
//        finish()
//    }
//}
//
//// Ya debería estar en TransactionHandler.kt o en un utils.kt, no lo repitas aquí
//// fun extrasToString(bundle: Bundle): String { ... }


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
    private var localOrderId: String = "" // Aunque no se use en TransactionData por ahora, es bueno tenerlo
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
                    onConfirm = { Timber.d("UI: CancelScreen confirmada."); finish() }
                )
                showSuccessScreen -> SuccessResultScreen(
                    title = "Pago Confirmado",
                    message = "Transacción Yappy ($yappyTransactionId) completada.",
                    onConfirm = { Timber.d("UI: SuccessScreen confirmada."); finish() }
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
                                val token=cfg["device_token"].orEmpty(); val apiKey=cfg["api_key"].orEmpty(); val secretKey=cfg["secret_key"].orEmpty()
                                if(token.isNotBlank()){ Timber.d("⏳ Cerrando sesión Yappy..."); ApiService.closeDeviceSession(token,apiKey,secretKey); Timber.i("🔒 Sesión Yappy cerrada.") }
                                else { Timber.w("⚠️ No token Yappy para cerrar.") }
                            } catch (e: Exception) { Timber.e(e, "⚠️ Error cerrando sesión Yappy.") }
                            LocalStorage.saveToken(this@QrResultActivity, "")
                            Timber.d("🧹 Token Yappy borrado.")
                            finishWithTransactionResult(TefTransactionResults.RESULT_ACCEPTED)
                            showSuccessScreen = true
                        }
                    }
                )
            }
        }
    }

    private fun buildReceiptLine(text: String, format: String = "BOLD", centered: Boolean = false): String { // Default a BOLD
        var lineText = text.replace("&", "&").replace("<", "<").replace(">", ">")
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
        val displayDateForReceipt = formatDisplayDateForReceipt(transactionDateFromYappy, "dd-MM-yyyy HH:mm:ss") // Formato Hiopos

        val merchantReceiptXml = buildString {
            append("<Receipt numCols=\"$printerColsFromHioPos\">\n")
            append(buildReceiptLine(" ", "NORMAL")) // Línea vacía inicial como en ejemplo compañero
            append(buildReceiptLine(shopInfo["name"] ?: "COMERCIO", "BOLD", centered = true))
            shopInfo["fiscalId"]?.takeIf { it.isNotBlank() }?.let { append(buildReceiptLine("RIF/ID: $it", "BOLD", centered = true)) } // O RTN:
            // Podrías añadir más info de la tienda si es necesario y Hiopos la usa
            // shopInfo["address"]?.takeIf {it.isNotBlank()}?.let{append(buildReceiptLine("DIRECCION: $it"))}
            // shopInfo["city"]?.takeIf {it.isNotBlank()}?.let{append(buildReceiptLine("CIUDAD: $it"))}
            // shopInfo["phone"]?.takeIf {it.isNotBlank()}?.let{append(buildReceiptLine("TELEFONO: $it"))}
            append(buildReceiptLine("-".repeat(printerColsFromHioPos), "BOLD")) // Separador

            append(buildReceiptLine("TRANSACCION: ${transactionTypeFromHioPos.uppercase(Locale.ROOT)} YAPPY", "BOLD")) // Título en BOLD
            append(buildReceiptLine("FECHA: $displayDateForReceipt", "BOLD"))
            val amountDblReceipt = transactionAmountProcessed.toDoubleOrNull() ?: 0.0
            append(buildReceiptLine("MONTO: ${"%.2f".format(amountDblReceipt)} $currencySymbolForReceipt", "BOLD"))
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
            // CUT_PAPER
            append("""
            |  <ReceiptLine type="CUT_PAPER">
            |    <Formats><Format from="0" to="$printerColsFromHioPos">NORMAL</Format></Formats>
            |    <Text> </Text>
            |  </ReceiptLine>
            """.trimMargin()) // No añadir \n después del último
            append("\n</Receipt>")
        }.also { Timber.v("📄 QrResultActivity: MerchantReceipt XML:\n$it") }

        val customerReceiptXml = merchantReceiptXml.replace("*** COPIA COMERCIO ***", "*** COPIA CLIENTE ***")
            .replace("RIF/ID:", " ") // Opcional: quitar info sensible para cliente
            .replace(Regex("ID POS:.*(\r\n|\r|\n)"), " ")

        val batchReceiptXml = "<Receipt numCols=\"$printerColsFromHioPos\"></Receipt>"

        val resultIntent = Intent().apply {
            putExtra("TransactionResult", result)
            putExtra("TransactionType", transactionTypeFromHioPos)
            if (originalHioPosTxnId != 0) putExtra("TransactionId", originalHioPosTxnId)

            val amountDouble = transactionAmountProcessed.toDoubleOrNull() ?: 0.0
            val amountMainForHioPos = (amountDouble * 100).toInt().toString()
            putExtra("Amount", amountMainForHioPos)
            putExtra("TipAmount", tipAmountFromHioPos) // Ya es string de centavos
            putExtra("TaxAmount", taxAmountFromHioPos) // Ya es string de centavos

            if (currencyISOFromHioPos.isNotBlank()) {
                putExtra("CurrencyISO", currencyISOFromHioPos) // Enviar numérico original
            }

            // ¡¡¡CONFIRMAR ESTE FORMATO CON HIOPOS PARA YAPPY!!!
            val transactionDataToReturn = yappyTransactionId // Probando solo con ID de Yappy
            Timber.d("  Usando para TransactionData: '$transactionDataToReturn'")
            putExtra("TransactionData", transactionDataToReturn.take(250))

            putExtra("MerchantReceipt", merchantReceiptXml)
            putExtra("CustomerReceipt", customerReceiptXml)
            putExtra("BatchReceipt", batchReceiptXml)

            if (result == TefTransactionResults.RESULT_FAILED && !errorMessage.isNullOrBlank()) {
                putExtra("ErrorMessage", errorMessage)
            }
        }

        Timber.i("📬 QrResultActivity: Respuesta final para Hiopos -> ${resultIntent.extras.toReadableString()}")
        setResult(Activity.RESULT_OK, resultIntent)
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
            val outputFmt = SimpleDateFormat(desiredFormat, Locale.getDefault()) // Usar formato deseado
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
        val resultIntent = Intent().apply {
            putExtra("TransactionResult", TefTransactionResults.RESULT_FAILED)
            putExtra("TransactionType", transactionTypeFromHioPos)
            putExtra("ErrorMessage", errorMessage)
            if (hioPosTxnIdToReturn != 0) putExtra("TransactionId", hioPosTxnIdToReturn)

            val txData = JSONObject()
            if (yappyTransactionId.isNotBlank()) txData.put("yappyTransactionId", yappyTransactionId)
            if (localOrderId.isNotBlank()) txData.put("localOrderId", localOrderId)
            putExtra("TransactionData", txData.toString().take(250))

            val amountDbl = transactionAmountProcessed.toDoubleOrNull() ?: 0.0
            putExtra("Amount", (amountDbl * 100).toInt().toString())
            putExtra("TipAmount", tipAmountFromHioPos)
            putExtra("TaxAmount", taxAmountFromHioPos)
            if (currencyISOFromHioPos.isNotBlank()) { // Enviar numérico original en error
                putExtra("CurrencyISO", currencyISOFromHioPos)
            }
        }
        Timber.i("  📤 QrResultActivity.finishWithError: Enviando error a Hiopos: ${resultIntent.extras.toReadableString()}")
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}

// Mueve esto a utils/BundleUtils.kt o similar si no lo has hecho
// fun Bundle?.toReadableString(): String { ... }