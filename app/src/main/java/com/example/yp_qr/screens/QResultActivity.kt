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
        Timber.d("🏁 QrResultActivity: onCreate")

        intent.extras?.let { extras ->
            Timber.d("Extras recibidos -> ${extras.toReadableString()}")

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
        } catch (e: Exception) {
            Timber.e(e, "Error cerrando sesión Yappy")
        } finally {
            LocalStorage.saveToken(this, "")
            finishWithTransactionResult(TefTransactionResults.RESULT_ACCEPTED)
        }
    }

    private fun finishWithTransactionResult(
        result: String,
        errorMessage: String? = null
    ) {
        Timber.d("🏁 Preparando resultado para HioPos: Result=$result, ErrorMsg=$errorMessage")

        val amountDouble = processedAmount.toDoubleOrNull() ?: 0.0
        val amountForHioPos = df.format(amountDouble)
            .replace(".", "")
            .replace(",", "")
        Timber.d("Calculado amountForHioPos: '$amountForHioPos' desde processedAmount: '$processedAmount'")

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

        Timber.d("Recibo generado (Merchant):")
        merchantReceipt.lines().forEach { Timber.d(it) }

        val resultIntent = Intent("icg.actions.electronicpayment.tefbanesco.TRANSACTION").apply {
            putExtra("TransactionResult", result)
            putExtra("TransactionType", transactionType)
            putExtra("Amount", amountForHioPos.toString())
            putExtra("TipAmount", tipFromHioPos.toString())
            putExtra("TaxAmount", taxFromHioPos.toString())
            putExtra("TransactionData", "${yappyTransactionId}/${localOrderId}")
            putExtra("BatchNumber", "000123")  // lote número 123

            putExtra("TransactionId", originalHioPosTxnIdString)
            putExtra("CurrencyISO", currencyISO)
            putExtra("MerchantReceipt", merchantReceipt)
            putExtra("CustomerReceipt", customerReceipt)
            if (result == TefTransactionResults.RESULT_FAILED && errorMessage != null) {
                putExtra("ErrorMessage", errorMessage)
                putExtra("ErrorMessageTitle", "Error Pago Yappy")
                Timber.w("Enviando error: $errorMessage")
            }
//
            putExtra("TenderType", "CREDIT") //flata
            putExtra("DocumentData", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Document><Header><HeaderField Key=\"PosId\">4</HeaderField><HeaderField Key=\"Alias\"/><HeaderField Key=\"SaleId\">bf2d97aa-73ae-491f-a507-41a815ed5032</HeaderField><HeaderField Key=\"Serie\"/><HeaderField Key=\"Number\">0</HeaderField><HeaderField Key=\"DocumentTypeId\">0</HeaderField><HeaderField Key=\"Z\">1</HeaderField><HeaderField Key=\"Date\">05-12-2025</HeaderField><HeaderField Key=\"Time\">10:33:14</HeaderField><HeaderField Key=\"IsTaxIncluded\">false</HeaderField><HeaderField Key=\"IsClosed\">false</HeaderField><HeaderField Key=\"IsSubTotalized\">false</HeaderField><HeaderField Key=\"TaxesAmount\">1.6800</HeaderField><HeaderField Key=\"NetAmount\">25.6800</HeaderField><HeaderField Key=\"ServiceTypeId\">0</HeaderField><HeaderField Key=\"ServiceNumber\">0</HeaderField><HeaderField Key=\"LoyaltyCardNumber\"/><HeaderField Key=\"LoyaltyCardPoints\">0.0000</HeaderField><HeaderField Key=\"LoyaltyCardBalance\">0.0000</HeaderField><HeaderField Key=\"LoyaltyCardTypeId\">0</HeaderField><HeaderField Key=\"PrintCount\">0</HeaderField><HeaderField Key=\"BlockToPrint\">null</HeaderField><HeaderField Key=\"TicketToPrint\"/><HeaderField Key=\"Rounding\">0.0000</HeaderField><HeaderField Key=\"TaxExemption\"/><HeaderField Key=\"IsoDocumentId\"/><HeaderField Key=\"IsDemo\">false</HeaderField><HeaderField Key=\"DocumentAPIPromoData\"/><HeaderField Key=\"ChannelId\">0</HeaderField><HeaderField Key=\"ChannelName\"/><HeaderField Key=\"ControlCode\"/><HeaderField Key=\"PriceListId\">1</HeaderField><HeaderField Key=\"CurrencyISOCode\">PAB</HeaderField><HeaderField Key=\"CurrencyExchangeRate\">1.0000</HeaderField><HeaderField Key=\"CurrencyId\">1</HeaderField><HeaderField Key=\"IsRoomCharge\">false</HeaderField><HeaderField Key=\"RoomName\"/><CustomDocHeaderFields/><Discount><DiscountField Key=\"DiscountReasonId\">0</DiscountField><DiscountField Key=\"Percentage\">0.0000</DiscountField><DiscountField Key=\"Amount\">0.0000</DiscountField><DiscountField Key=\"AmountWithTaxes\">0.0000</DiscountField></Discount><Company><CompanyField Key=\"CompanyId\">71428</CompanyField><CompanyField Key=\"FiscalId\">143224-532-2024  DV00</CompanyField><CompanyField Key=\"Name\">My T-Shirt Love</CompanyField><CompanyField Key=\"TradeName\">RETAIL S.A.</CompanyField><CompanyField Key=\"FiscalName\">RETAIL S.A.</CompanyField><CompanyField Key=\"CorrectTradeName\">My T-Shirt Love</CompanyField><CompanyField Key=\"Address\">Calle 50</CompanyField><CompanyField Key=\"PostalCode\">123</CompanyField><CompanyField Key=\"City\">Panamá</CompanyField><CompanyField Key=\"Phone\">62902519</CompanyField><CompanyField Key=\"Email\">retail@hypernovalabs.com</CompanyField><CustomCompanyFields/><CustomAccountingCompanyFields/></Company><Shop><ShopField Key=\"ShopId\">1</ShopField><ShopField Key=\"FiscalId\">143224-532-2024</ShopField><ShopField Key=\"Name\">My T-Shirt Love Suc. 1</ShopField><ShopField Key=\"TradeName\">RETAIL S.A.</ShopField><ShopField Key=\"FiscalName\">RETAIL S.A.</ShopField><ShopField Key=\"CorrectTradeName\">My T-Shirt Love Suc. 1</ShopField><ShopField Key=\"Address\">COMPROBANTE AUXILIAR DE FACTURA ELECTRÓNICA</ShopField><ShopField Key=\"PostalCode\">123</ShopField><ShopField Key=\"City\">Panamá</ShopField><ShopField Key=\"State\">Panamá</ShopField><ShopField Key=\"Phone\">62902519</ShopField><ShopField Key=\"Email\">retail@hypernovalabs.com</ShopField><ShopField Key=\"DefaultPriceListId\">1</ShopField><ShopField Key=\"DefaultPriceListName\">Tarifa por defecto</ShopField><ShopField Key=\"MainCurrencyId\">1</ShopField><ShopField Key=\"MainCurrencyName\">Balboa</ShopField><ShopField Key=\"LanguageIsoCode\">es</ShopField><ShopField Key=\"CountryIsoCode\">PAN</ShopField><ShopField Key=\"Web\"/><CustomShopFields/></Shop><Seller><SellerField Key=\"SellerId\">2</SellerField><SellerField Key=\"ContactType\">1</SellerField><SellerField Key=\"Gender\">0</SellerField><SellerField Key=\"FiscalIdDocType\">0</SellerField><SellerField Key=\"FiscalId\"/><SellerField Key=\"Name\">Administrador 1</SellerField><SellerField Key=\"GivenName1\"/><SellerField Key=\"Address\"/><SellerField Key=\"PostalCode\"/><SellerField Key=\"City\"/><SellerField Key=\"State\"/><SellerField Key=\"Phone\"/><SellerField Key=\"Email\"/></Seller><Customer><CustomerField Key=\"customerId\">0</CustomerField></Customer><Provider><ProviderField Key=\"providerId\">null</ProviderField></Provider><DocCurrency><DocCurrencyField Key=\"CurrencyId\">1</DocCurrencyField><DocCurrencyField Key=\"Name\">Balboa</DocCurrencyField><DocCurrencyField Key=\"DecimalCount\">2</DocCurrencyField><DocCurrencyField Key=\"Initials\">B/.</DocCurrencyField><DocCurrencyField Key=\"InitialsBefore\">1</DocCurrencyField><DocCurrencyField Key=\"IsoCode\">PAB</DocCurrencyField><DocCurrencyField Key=\"ExchangeRate\">1.0</DocCurrencyField></DocCurrency></Header><Lines><Line><LineField Key=\"LineId\">40e7189e-ec48-4db7-bc6e-534a0436a4f2</LineField><LineField Key=\"LineNumber\">1</LineField><LineField Key=\"ProductId\">34</LineField><LineField Key=\"ProductSizeId\">350</LineField><LineField Key=\"ExternalProductId\">0</LineField><LineField Key=\"Name\">T-SHIRT BOXY FIT</LineField><LineField Key=\"Size\">M / Único</LineField><LineField Key=\"Units\">1.0000</LineField><LineField Key=\"IsMenu\">false</LineField><LineField Key=\"PortionId\">0</LineField><LineField Key=\"IsGift\">false</LineField><LineField Key=\"PriceListId\">1</LineField><LineField Key=\"Price\">12.0000</LineField><LineField Key=\"SellerId\">2</LineField><LineField Key=\"WarehouseId\">2</LineField><LineField Key=\"DiscountReasonId\">0</LineField><LineField Key=\"ReturnReasonId\">0</LineField><LineField Key=\"ReturnReasonName\"/><LineField Key=\"DiscountPercentage\">0.0000</LineField><LineField Key=\"DiscountAmount\">0.0000</LineField><LineField Key=\"DiscountAmountWithTaxes\">0.0000</LineField><LineField Key=\"BaseAmount\">12.0000</LineField><LineField Key=\"TaxesAmount\">0.8400</LineField><LineField Key=\"TaxCategory\">0</LineField><LineField Key=\"NetAmount\">12.8400</LineField><LineField Key=\"Measure\">1.0000</LineField><LineField Key=\"MeasureInitials\"/><LineField Key=\"IsNew\">false</LineField><LineField Key=\"ProductReference\"/><LineField Key=\"ProductBarcode\"/><LineField Key=\"ServiceTypeId\">0</LineField><LineField Key=\"DestinationWarehouseId\">0</LineField><LineField Key=\"ReturnSaleSerie\"/><LineField Key=\"ReturnSaleNumber\"/><LineField Key=\"ReturnLineServiceTypeId\">0</LineField><LineField Key=\"ProductType\">Product</LineField><LineField Key=\"UsesStock\">true</LineField><LineField Key=\"FamilyId\">1</LineField><CustomProductFields/><CustomProductSizeFields/><CustomDocLineFields/><LineTaxes><LineTax><LineTaxField Key=\"TaxId\">1</LineTaxField><LineTaxField Key=\"Position\">1</LineTaxField><LineTaxField Key=\"Percentage\">7.0000</LineTaxField><CustomDocLineTaxFields/><CustomTaxFields/></LineTax></LineTaxes><CustomDocLineSummaryFields/></Line><Line><LineField Key=\"LineId\">5e6fb1aa-b7fe-4ef8-b204-b1d9e2d28118</LineField><LineField Key=\"LineNumber\">2</LineField><LineField Key=\"ProductId\">30</LineField><LineField Key=\"ProductSizeId\">311</LineField><LineField Key=\"ExternalProductId\">0</LineField><LineField Key=\"Name\">T-SHIRT SLIM FIT</LineField><LineField Key=\"Size\">S / AZUL</LineField><LineField Key=\"Units\">1.0000</LineField><LineField Key=\"IsMenu\">false</LineField><LineField Key=\"PortionId\">0</LineField><LineField Key=\"IsGift\">false</LineField><LineField Key=\"PriceListId\">1</LineField><LineField Key=\"Price\">12.0000</LineField><LineField Key=\"SellerId\">2</LineField><LineField Key=\"WarehouseId\">2</LineField><LineField Key=\"DiscountReasonId\">0</LineField><LineField Key=\"ReturnReasonId\">0</LineField><LineField Key=\"ReturnReasonName\"/><LineField Key=\"DiscountPercentage\">0.0000</LineField><LineField Key=\"DiscountAmount\">0.0000</LineField><LineField Key=\"DiscountAmountWithTaxes\">0.0000</LineField><LineField Key=\"BaseAmount\">12.0000</LineField><LineField Key=\"TaxesAmount\">0.8400</LineField><LineField Key=\"TaxCategory\">0</LineField><LineField Key=\"NetAmount\">12.8400</LineField><LineField Key=\"Measure\">1.0000</LineField><LineField Key=\"MeasureInitials\"/><LineField Key=\"IsNew\">false</LineField><LineField Key=\"ProductReference\"/><LineField Key=\"ProductBarcode\"/><LineField Key=\"ServiceTypeId\">0</LineField><LineField Key=\"DestinationWarehouseId\">0</LineField><LineField Key=\"ReturnSaleSerie\"/><LineField Key=\"ReturnSaleNumber\"/><LineField Key=\"ReturnLineServiceTypeId\">0</LineField><LineField Key=\"ProductType\">Product</LineField><LineField Key=\"UsesStock\">true</LineField><LineField Key=\"FamilyId\">1</LineField><CustomProductFields/><CustomProductSizeFields/><CustomDocLineFields/><LineTaxes><LineTax><LineTaxField Key=\"TaxId\">1</LineTaxField><LineTaxField Key=\"Position\">1</LineTaxField><LineTaxField Key=\"Percentage\">7.0000</LineTaxField><CustomDocLineTaxFields/><CustomTaxFields/></LineTax></LineTaxes><CustomDocLineSummaryFields/></Line></Lines><Taxes><Tax><TaxField Key=\"TaxId\">1</TaxField><TaxField Key=\"Description\">ITBMS 7%</TaxField><TaxField Key=\"LineNumber\">1</TaxField><TaxField Key=\"TaxBase\">24.0000</TaxField><TaxField Key=\"Percentage\">7.0000</TaxField><TaxField Key=\"TaxAmount\">1.6800</TaxField><TaxField Key=\"FiscalId\"/><TaxField Key=\"ExemptReason\"/><TaxField Key=\"IsoCode\"/><CustomDocTaxFields/><CustomTaxFields/></Tax></Taxes><PaymentMeans><PaymentMean><PaymentMeanField Key=\"PaymentMeanId\">2</PaymentMeanField><PaymentMeanField Key=\"Type\">0</PaymentMeanField><PaymentMeanField Key=\"LineNumber\">1</PaymentMeanField><PaymentMeanField Key=\"Description\">Visa</PaymentMeanField><PaymentMeanField Key=\"PaymenMeanName\">Visa</PaymentMeanField><PaymentMeanField Key=\"Amount\">25.6800</PaymentMeanField><PaymentMeanField Key=\"CurrencyISOCode\">PAB</PaymentMeanField><PaymentMeanField Key=\"CurrencyExchangeRate\">1.0000</PaymentMeanField><PaymentMeanField Key=\"TransactionId\"/><PaymentMeanField Key=\"AuthorizationId\"/><PaymentMeanField Key=\"ChargeDiscountType\">0</PaymentMeanField><PaymentMeanField Key=\"ChargeDiscountValue\">0.0000</PaymentMeanField><CustomPaymentMeanFields/><CustomDocPaymentMeanFields/><Currency><CurrencyField Key=\"Name\">Balboa</CurrencyField><CurrencyField Key=\"Initials\">B/.</CurrencyField><CurrencyField Key=\"InitialsBefore\">true</CurrencyField><CurrencyField Key=\"DecimalCount\">2</CurrencyField><CurrencyField Key=\"IsoCode\">PAB</CurrencyField><CustomCurrencyFields/></Currency><CurrencyField Key=\"CurrencyId\">1</CurrencyField></PaymentMean></PaymentMeans><AdditionalFields><AdditionalField Key=\"5000013\">COMPROBANTE AUXILIAR DE FACTURA ELECTRÓNICA</AdditionalField><AdditionalField Key=\"5000014\">Panamá</AdditionalField><AdditionalField Key=\"5000016\">retail@hypernovalabs.com</AdditionalField><AdditionalField Key=\"5000012\">143224-532-2024</AdditionalField><AdditionalField Key=\"5000010\">My T-Shirt Love Suc. 1</AdditionalField><AdditionalField Key=\"5000017\">62902519</AdditionalField><AdditionalField Key=\"5000015\">123</AdditionalField><AdditionalField Key=\"5000011\">RETAIL S.A.</AdditionalField><AdditionalField Key=\"5000009\">Administrador 1</AdditionalField></AdditionalFields></Document>")
//            putExtra("LanguageISO", "es")
            putExtra("ShopData", shopDataXml)
            putExtra("ReceiptPrinterColumns", "42")
            putExtra("SellerData","<?xml version='1.0' encoding='utf-8'?>\n" +
                    "<transactionRequestTaxDetail>\n" +
                    "   <taxList class=\"java.util.ArrayList\">\n" +
                    "      <taxDetail>\n" +
                    "         <exemptReason></exemptReason>\n" +
                    "         <fiscalId></fiscalId>\n" +
                    "         <percentage>700</percentage>\n" +
                    "         <taxAmount>168</taxAmount>\n" +
                    "         <taxBase>2400</taxBase>\n" +
                    "         <taxId>1</taxId>\n" +
                    "      </taxDetail>\n" +
                    "   </taxList>\n" +
                    "</transactionRequestTaxDetail>")
        }
        Timber.i("TransactionId: $originalHioPosTxnIdString")
        Timber.i("✅ Enviando RESULTADO a HioPos:")
        Timber.i("✅ Action: ${resultIntent.action}")
        resultIntent.extras?.let {
            val basicInfo = StringBuilder("Bundle[")
            it.keySet().forEach { key ->
                if (key != "MerchantReceipt" && key != "CustomerReceipt") {
                    basicInfo.append("$key=${it.get(key)}, ")
                }
            }
            if (basicInfo.length > 7) basicInfo.setLength(basicInfo.length - 2)
            basicInfo.append("]")
            Timber.i("✅ Extras Bundle (sin recibos): $basicInfo")

            Timber.i("✅ Extras Bundle - MerchantReceipt:")
            it.getString("MerchantReceipt")?.lines()?.forEach { Timber.i(it) }
            Timber.i("✅ Extras Bundle - CustomerReceipt:")
            it.getString("CustomerReceipt")?.lines()?.forEach { Timber.i(it) }

            val transactionIdValue = it.get("TransactionId")
            val transactionIdType = transactionIdValue?.javaClass?.simpleName ?: "null"
            Timber.i("✅ Verificación TIPO TransactionId en Bundle: $transactionIdType (Valor: '$transactionIdValue')")
        } ?: Timber.w("✅ Extras Bundle es NULL al enviar resultado.")
        Timber.i("✅ resultIntent->-> ::: ${resultIntent.extras.toReadableString()}")

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
        Timber.i("🏁 QrResultActivity finalizada.")
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
