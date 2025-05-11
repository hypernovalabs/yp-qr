// File: QrResultActivity.kt
package com.example.tefbanesco.screens

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.example.tefbanesco.storage.LocalStorage
import com.example.tefbanesco.network.ApiService
import kotlinx.coroutines.launch
import com.example.tefbanesco.utils.buildTransactionDataString
import com.example.tefbanesco.utils.escapeXml
import com.example.tefbanesco.utils.parseShopData
import com.example.tefbanesco.utils.mapCurrencyISOToSymbol
import com.example.tefbanesco.utils.formatIsoDate
import com.example.tefbanesco.utils.bundleExtrasToString // Sigue siendo útil para el log de entrada
import timber.log.Timber
import java.util.Locale

object TefTransactionResults {
    const val RESULT_ACCEPTED = "ACCEPTED"
    const val RESULT_FAILED = "FAILED"
    const val RESULT_UNKNOWN = "UNKNOWN_RESULT"
    const val TYPE_SALE = "SALE"
}

class QrResultActivity : ComponentActivity() {

    private var qrHashFromIntent: String = ""
    private var yappyTransactionIdFromQr: String = ""
    private var localOrderIdFromQr: String = ""
    private var yappyDateFromQr: String = ""
    private var yappyAmountFromQr: String = "0.0"

    private var originalHioPosTransactionId: Int = 0
    private var originalHioPosAction: String? = null
    private var shopDataXmlFromHioPos: String = ""
    private var receiptPrinterColumnsFromHioPos: Int = 42
    private var tipAmountFromHioPos: String = "0"
    private var taxAmountFromHioPos: String = "0"
    private var currencyISOFromHioPos: String = ""
    private var transactionTypeFromHioPos: String = TefTransactionResults.TYPE_SALE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("🏁 QrResultActivity: onCreate")

        intent.extras?.let { extras ->
            Timber.d("  QrResultActivity: Extras recibidos -> ${bundleExtrasToString(extras)}")
            qrHashFromIntent = extras.getString("qrHash") ?: ""
            yappyTransactionIdFromQr = extras.getString("yappyTransactionIdFromQr") ?: ""
            localOrderIdFromQr = extras.getString("localOrderIdFromQr") ?: ""
            yappyDateFromQr = extras.getString("yappyDateFromQr") ?: ""
            yappyAmountFromQr = extras.getString("yappyAmountFromQr") ?: "0.0"

            originalHioPosTransactionId = extras.getInt("originalHioPosTransactionId", 0)
            originalHioPosAction = extras.getString("originalHioPosAction")
            shopDataXmlFromHioPos = extras.getString("shopDataXmlFromHioPos") ?: ""
            receiptPrinterColumnsFromHioPos = extras.getInt("receiptPrinterColumnsFromHioPos", 42)
            tipAmountFromHioPos = extras.getString("tipAmountFromHioPos") ?: "0"
            taxAmountFromHioPos = extras.getString("taxAmountFromHioPos") ?: "0"
            currencyISOFromHioPos = extras.getString("currencyISOFromHioPos") ?: ""
            transactionTypeFromHioPos = extras.getString("transactionTypeFromHioPos") ?: TefTransactionResults.TYPE_SALE

            Timber.i("📄 QrResultActivity: Datos inicializados -> HioPosTxnId=$originalHioPosTransactionId, ActionOriginal=${originalHioPosAction}, YappyTxnIdQR=$yappyTransactionIdFromQr, QRHash=${qrHashFromIntent.take(10)}")
        } ?: run {
            Timber.e("❌ QrResultActivity: Intent sin extras. Finalizando con error.")
            finishWithTransactionResult(TefTransactionResults.RESULT_FAILED, "Error interno: Datos de transacción perdidos.")
            return
        }

        if (qrHashFromIntent.isBlank()) {
            Timber.e("❌ QrResultActivity: qrHash vacío. Finalizando.")
            finishWithTransactionResult(TefTransactionResults.RESULT_FAILED, "Error: QR no disponible para mostrar.")
            return
        }

        setContent {
            var showCancelScreen by remember { mutableStateOf(false) }
            var showSuccessScreen by remember { mutableStateOf(false) }
            var showUnknownScreen by remember { mutableStateOf(false) }

            when {
                showCancelScreen -> CancelResultScreen(
                    title = "Pago Cancelado o Fallido",
                    message = "La transacción Yappy ($yappyTransactionIdFromQr) fue cancelada, falló o expiró.",
                    onConfirm = { }
                )
                showSuccessScreen -> SuccessResultScreen(
                    title = "Pago Confirmado",
                    message = "La transacción Yappy ($yappyTransactionIdFromQr) se completó exitosamente.",
                    onConfirm = { }
                )
                showUnknownScreen -> CancelResultScreen(
                    title = "Estado Desconocido",
                    message = "No se pudo confirmar el estado final de la transacción Yappy ($yappyTransactionIdFromQr).",
                    onConfirm = { }
                )
                else -> QrResultScreen(
                    date = yappyDateFromQr,
                    transactionId = yappyTransactionIdFromQr,
                    hash = qrHashFromIntent,
                    amount = yappyAmountFromQr,
                    onCancelSuccess = {
                        Timber.i("🔔 QrResultActivity: onCancelSuccess (desde QrResultScreen) para YappyTxnId=$yappyTransactionIdFromQr")
                        finishWithTransactionResult(TefTransactionResults.RESULT_FAILED, "Transacción Yappy cancelada/fallida/expirada.")
                        showCancelScreen = true
                    },
                    onPaymentSuccess = {
                        Timber.i("🔔 QrResultActivity: onPaymentSuccess (desde QrResultScreen) para YappyTxnId=$yappyTransactionIdFromQr")
                        lifecycleScope.launch {
                            try {
                                val cfg = LocalStorage.getConfig(this@QrResultActivity)
                                val token = cfg["device_token"].orEmpty()
                                val apiKey = cfg["api_key"].orEmpty()
                                val secretKey = cfg["secret_key"].orEmpty()
                                if (token.isNotBlank() && apiKey.isNotBlank() && secretKey.isNotBlank()) {
                                    Timber.d("⏳ QrResultActivity: Cerrando sesión Yappy...")
                                    ApiService.closeDeviceSession(token, apiKey, secretKey)
                                    Timber.i("🔒 QrResultActivity: Sesión Yappy cerrada.")
                                } else {
                                    Timber.w("⚠️ QrResultActivity: No hay token/configuración Yappy completa para cerrar sesión.")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "⚠️ QrResultActivity: Error cerrando sesión Yappy.")
                            } finally {
                                LocalStorage.saveToken(this@QrResultActivity, "")
                                Timber.d("🧹 QrResultActivity: Token Yappy borrado localmente.")
                                finishWithTransactionResult(TefTransactionResults.RESULT_ACCEPTED)
                                showSuccessScreen = true
                            }
                        }
                    }
                )
            }
        }
    }

    private fun finishWithTransactionResult(
        result: String,
        errorMessage: String? = null
    ) {
        if (isFinishing) {
            Timber.w("QrResultActivity.finishWithTransactionResult: Ya está finalizando, ignorando llamada duplicada. Result: $result")
            return
        }
        Timber.d("🚀 QrResultActivity.finishWithTransactionResult: Preparando respuesta. Result: $result, HioPosTxnId: $originalHioPosTransactionId, YappyTxnId: $yappyTransactionIdFromQr")

        val txDataString = buildTransactionDataString(yappyTransactionIdFromQr, localOrderIdFromQr)
        val merchantXml = buildMerchantReceipt(result, errorMessage)
        var customerXml = merchantXml.replace("*** COPIA COMERCIO ***", "*** COPIA CLIENTE ***")

        val batchXml = "<Receipt numCols=\"$receiptPrinterColumnsFromHioPos\"></Receipt>"

        val resultIntent = Intent().apply {
            originalHioPosAction?.let { this.action = it }

            putExtra("TransactionResult", result)
            putExtra("TransactionType", transactionTypeFromHioPos)
            if (originalHioPosTransactionId != 0) {
                putExtra("TransactionId", originalHioPosTransactionId)
            }
            val amountToReturn = (yappyAmountFromQr.toDoubleOrNull() ?: 0.0)
            putExtra("Amount", (amountToReturn * 100).toInt().toString())
            putExtra("TipAmount", tipAmountFromHioPos)
            putExtra("TaxAmount", taxAmountFromHioPos)
            putExtra("CurrencyISO", currencyISOFromHioPos)
            putExtra("TransactionData", txDataString)
            putExtra("MerchantReceipt", merchantXml)
            putExtra("CustomerReceipt", customerXml)
            putExtra("BatchReceipt", batchXml)
            if (result != TefTransactionResults.RESULT_ACCEPTED && !errorMessage.isNullOrBlank()) {
                putExtra("ErrorMessage", errorMessage)
            }
        }

        // Logging detallado de los extras del Intent de respuesta
        Timber.i("📬 QrResultActivity: Respuesta final para Hiopos:")
        Timber.i("  Action: ${resultIntent.action}")
        resultIntent.extras?.let { extras ->
            for (key in extras.keySet()) {
                val value = extras.get(key)
                // Para los recibos XML largos, loguear solo una parte o un indicador
                if (key == "MerchantReceipt" || key == "CustomerReceipt" || key == "BatchReceipt") {
                    Timber.i("  Extra: $key = ${value?.toString()?.take(100)}... (XML)")
                } else {
                    Timber.i("  Extra: $key = $value (Tipo: ${value?.javaClass?.simpleName})")
                }
            }
        } ?: Timber.i("  Extras: null")

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun buildMerchantReceipt(resultForReceipt: String, errorForReceipt: String?): String {
        val shopInfo = parseShopData(shopDataXmlFromHioPos)
        val displayDate = formatIsoDate(yappyDateFromQr, "dd-MM-yyyy HH:mm:ss")

        return buildString {
            append("<Receipt numCols=\"$receiptPrinterColumnsFromHioPos\">\n")
            append(buildReceiptLine(" ", "NORMAL"))
            append(buildReceiptLine(shopInfo["name"] ?: "COMERCIO YAPPY", "BOLD", centered = true))
            shopInfo["fiscalId"]?.takeIf { it.isNotBlank() }?.let {
                append(buildReceiptLine("RIF/ID: $it", "BOLD", centered = true))
            }
            append(buildReceiptLine("-".repeat(receiptPrinterColumnsFromHioPos), "BOLD"))
            append(buildReceiptLine("TRANSACCION: ${transactionTypeFromHioPos.uppercase(Locale.getDefault())} YAPPY", "BOLD"))
            append(buildReceiptLine("FECHA: $displayDate", "BOLD"))
            val amountVal = (yappyAmountFromQr.toDoubleOrNull() ?: 0.0)
            append(buildReceiptLine("MONTO: %.2f ${mapCurrencyISOToSymbol(currencyISOFromHioPos)}".format(amountVal), "BOLD"))
            append(buildReceiptLine("ID YAPPY: $yappyTransactionIdFromQr", "BOLD"))
            if (originalHioPosTransactionId != 0) append(buildReceiptLine("ID POS: $originalHioPosTransactionId", "BOLD"))

            val estadoTxt = when (resultForReceipt) {
                TefTransactionResults.RESULT_ACCEPTED -> "APROBADA"
                TefTransactionResults.RESULT_FAILED -> "FALLIDA"
                TefTransactionResults.RESULT_UNKNOWN -> "DESCONOCIDA"
                else -> resultForReceipt.uppercase(Locale.getDefault())
            }
            append(buildReceiptLine("ESTADO: $estadoTxt", "BOLD"))

            if (resultForReceipt != TefTransactionResults.RESULT_ACCEPTED && !errorForReceipt.isNullOrBlank()) {
                append(buildReceiptLine("ERROR: ${errorForReceipt.take(receiptPrinterColumnsFromHioPos)}", "BOLD"))
            }
            append(buildReceiptLine(" ", "NORMAL"))
            append(buildReceiptLine("*** COPIA COMERCIO ***", "NORMAL", centered = true))
            append(buildReceiptLine("Gracias por su compra!", "BOLD", centered = true))
            append(buildReceiptLine(" ", "NORMAL"))
            append(buildCutPaperLine())
            append("</Receipt>")
        }
    }

    private fun buildReceiptLine(
        text: String,
        format: String = "NORMAL",
        centered: Boolean = false
    ): String {
        var content = escapeXml(text)
        if (centered) {
            val textLength = content.length
            if (textLength < receiptPrinterColumnsFromHioPos) {
                val padding = (receiptPrinterColumnsFromHioPos - textLength) / 2
                if (padding > 0) content = " ".repeat(padding) + content
            }
        }
        content = content.take(receiptPrinterColumnsFromHioPos)
        return """
          <ReceiptLine type="TEXT">
            <Formats><Format from="0" to="$receiptPrinterColumnsFromHioPos">$format</Format></Formats>
            <Text>$content</Text>
          </ReceiptLine>
        """.trimIndent() + "\n"
    }

    private fun buildCutPaperLine(): String = """
          <ReceiptLine type="CUT_PAPER">
            <Formats><Format from="0" to="$receiptPrinterColumnsFromHioPos">NORMAL</Format></Formats>
            <Text> </Text>
          </ReceiptLine>
        """.trimIndent() + "\n"
}