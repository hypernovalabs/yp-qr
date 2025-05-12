package com.example.tefbanesco.utils

import android.content.Intent
import android.os.Bundle
import timber.log.Timber
import kotlin.concurrent.timer

/**
 * Claves de los extras que entrega HioPos en el Intent de transacción.
 * Actualizado según la API v3.5 (21 de Enero de 2025)
 */
object HioPosIntentKeys {
    const val CURRENCY_ISO = "CurrencyISO"
    const val TENDER_TYPE = "TenderType"
    const val TRANSACTION_TYPE = "TransactionType"
    const val LANGUAGE_ISO = "LanguageISO"
    const val AMOUNT = "Amount"
    const val TIP_AMOUNT = "TipAmount"
    const val TAX_AMOUNT = "TaxAmount"
    const val TAX_DETAIL = "TaxDetail"
    const val TRANSACTION_ID = "TransactionId"
    const val TRANSACTION_DATA = "TransactionData"
    const val RECEIPT_PRINTER_COLUMNS = "ReceiptPrinterColumns"
    const val SHOP_DATA = "ShopData"
    const val SELLER_DATA = "SellerData"
    const val DOCUMENT_DATA = "DocumentData"
    const val DOCUMENT_PATH = "DocumentPath"
    const val OVER_PAYMENT_TYPE = "OverPaymentType"
    const val IS_ADVANCED_PAYMENT = "IsAdvancedPayment"
}

/**
 * Extensión para convertir el Bundle de extras a String legible.
 */
fun Bundle.extrasToReadableString(): String {
    if (this.isEmpty) return "Bundle[empty]"
    val sb = StringBuilder("Bundle[")
    for (key in keySet()) {
        sb.append(key).append("=").append(get(key)).append(", ")
    }
    if (sb.endsWith(", ")) sb.setLength(sb.length - 2)
    sb.append("]")
    return sb.toString()
}

/**
 * Data class para mantener los datos extraídos del Intent.
 * Actualizado según la API v3.5 (21 de Enero de 2025)
 */
data class PaymentIntentData(
    val currencyIso: String,
    val tenderType: String,
    val transactionType: String,
    val languageIso: String,
    val amount: Double,
    val tipAmount: Double,
    val taxAmount: Double,
    val taxDetail: String?,
    val transactionId: String?,
    val transactionData: String?,
    val receiptPrinterColumns: Int,
    val shopData: String?,
    val sellerData: String?,
    val documentData: String?,
    val documentPath: String?,
    val overPaymentType: Int,
    val isAdvancedPayment: Boolean
)

/**
 * Parsea un Intent de transacción y devuelve un objeto PaymentIntentData,
 * o null si no hay extras o ocurre un error.
 */
fun parsePaymentIntent(intent: Intent): PaymentIntentData? {
    Timber.d("parsePaymentIntent: Intent recibido: $intent")
    Timber.d("parsePaymentIntent: Extras del Intent: ${intent.extras?.extrasToReadableString() ?: "null"}")
//    Timber.d("intent->->->->->: ${intent.extras.}")

    val bundle = intent.extras
    if (bundle == null) {
        Timber.w("El Intent no contiene extras. No se pueden parsear los datos de la transacción.")
        return null
    }

    return try {
        // Campos básicos
        val currencyIso     = bundle.getString(HioPosIntentKeys.CURRENCY_ISO, "")
        val tenderType      = bundle.getString(HioPosIntentKeys.TENDER_TYPE, "")
        val transactionType = bundle.getString(HioPosIntentKeys.TRANSACTION_TYPE, "")
        val languageIso     = bundle.getString(HioPosIntentKeys.LANGUAGE_ISO, "")

        // ────────────────────────────────────────────────────────────────
        // CAMBIO: Parsing robusto de montos (acepta String o Number)
        val amountObject = bundle.get(HioPosIntentKeys.AMOUNT)
        val amountCents: Long = when (amountObject) {
            is String -> amountObject.toLongOrNull() ?: 0L
            is Number -> amountObject.toLong()
            else       -> 0L
        }
        val tipObject = bundle.get(HioPosIntentKeys.TIP_AMOUNT)
        val tipCents:   Long = when (tipObject) {
            is String -> tipObject.toLongOrNull() ?: 0L
            is Number -> tipObject.toLong()
            else       -> 0L
        }
        val taxObject = bundle.get(HioPosIntentKeys.TAX_AMOUNT)
        val taxCents:   Long = when (taxObject) {
            is String -> taxObject.toLongOrNull() ?: 0L
            is Number -> taxObject.toLong()
            else       -> 0L
        }
        val amount    = amountCents / 100.0
        val tipAmount = tipCents   / 100.0
        val taxAmount = taxCents   / 100.0
        // ────────────────────────────────────────────────────────────────

        // Datos adicionales (XML/serializado)
        val taxDetail    = bundle.getString(HioPosIntentKeys.TAX_DETAIL)

        // ────────────────────────────────────────────────────────────────
        // CAMBIO: Lectura robusta de TransactionId (acepta String o Number)
        val transactionIdObject = bundle.get(HioPosIntentKeys.TRANSACTION_ID)
        Timber.d("TRANSACTION_ID: $transactionIdObject")

        if (transactionIdObject != null) {
            val className = transactionIdObject.javaClass.name
            Timber.d("Tipo de dato de TRANSACTION_ID: $className")
        } else {
            Timber.d("TRANSACTION_ID es nulo, no se puede determinar el tipo.")
        }
        val transactionId: String? = when (transactionIdObject) {
            is String -> transactionIdObject.takeIf { it.isNotEmpty() }
            is Number -> transactionIdObject.toString()
            else       -> null
        }
        // ────────────────────────────────────────────────────────────────

        val transactionData    = bundle.getString(HioPosIntentKeys.TRANSACTION_DATA)
        val printerColumns     = bundle.getInt(   HioPosIntentKeys.RECEIPT_PRINTER_COLUMNS, 42)
        val shopData           = bundle.getString(HioPosIntentKeys.SHOP_DATA)
        val sellerData         = bundle.getString(HioPosIntentKeys.SELLER_DATA)
        val documentData       = bundle.getString(HioPosIntentKeys.DOCUMENT_DATA)

        // Campos nuevos de la API v3.5
        val documentPath       = bundle.getString(HioPosIntentKeys.DOCUMENT_PATH)
        val overPaymentType    = bundle.getInt(HioPosIntentKeys.OVER_PAYMENT_TYPE, -1)
        val isAdvancedPayment  = bundle.getBoolean(HioPosIntentKeys.IS_ADVANCED_PAYMENT, false)

        PaymentIntentData(
            currencyIso           = currencyIso,
            tenderType            = tenderType,
            transactionType       = transactionType,
            languageIso           = languageIso,
            amount                = amount,
            tipAmount             = tipAmount,
            taxAmount             = taxAmount,
            taxDetail             = taxDetail,
            transactionId         = transactionId,
            transactionData       = transactionData,
            receiptPrinterColumns = printerColumns,
            shopData              = shopData,
            sellerData            = sellerData,
            documentData          = documentData,
            documentPath          = documentPath,
            overPaymentType       = overPaymentType,
            isAdvancedPayment     = isAdvancedPayment
        ).also {
            Timber.i("[YAPPY] Datos del Intent de pago parseados: $it")

            // Logging especial para documentPath (importante para API v3.5)
            if (documentPath != null) {
                Timber.d("[YAPPY] HioPos envió DocumentPath: $documentPath")
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Error al parsear los datos del Intent de pago.")
        null
    }
}

/*
# Especificación de Formatos XML para Extras de Intent de HioPos

Este documento detalla cómo llegan los datos complejos en formato XML en los campos de extras del Intent de HioPos, para que el desarrollador sepa exactamente qué estructura tiene cada uno.

---

## 1. TaxDetail (XML)

**Clave:** `TaxDetail`

**Formato:** XML con una raíz `<transactionRequestTaxDetail>` que contiene `<taxList>` y múltiples `<taxDetail>`.

**Ejemplo:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<transactionRequestTaxDetail>
<taxList class="java.util.ArrayList">
<taxDetail>
<taxId>1</taxId>
<taxBase>661</taxBase>       <!-- Base imponible en centavos (6.61) -->
<taxAmount>139</taxAmount>   <!-- Monto de impuesto en centavos (1.39) -->
<percentage>2100</percentage><!-- Tasa multiplicada por 100 (21%) -->
<fiscalId/>                  <!-- Opcional: RUC o ID fiscal -->
<exemptReason/>              <!-- Opcional: Motivo exención -->
</taxDetail>
<!-- ... más elementos taxDetail ... -->
</taxList>
</transactionRequestTaxDetail>
```

---

## 2. ShopData (XML)

**Clave:** `ShopData`

**Formato:** XML con raíz `<shopData>` y campos de comercio.

**Ejemplo:**

```xml
<shopData>
<name>Mi Comercio S.A.</name>
<fiscalId>A12345678</fiscalId>
<address>Calle 123, Ciudad</address>
<cityWithPostalCode>Ciudad, 0101</cityWithPostalCode>
</shopData>
```

---

## 3. SellerData (XML)

**Clave:** `SellerData`

**Formato:** XML con raíz `<sellerData>` y datos del vendedor.

**Ejemplo:**

```xml
<sellerData>
<name>Juan Pérez</name>
<givenName1>Juan</givenName1>
<fiscalId>12345678Z</fiscalId>
<email>juan.perez@dominio.com</email>
</sellerData>
```

---

## 4. TransactionData (XML)

**Clave:** `TransactionData`

**Formato:** XML con raíz `<Transaction>` y detalle de la transacción.

**Ejemplo:**

```xml
<Transaction>
<Id>TX123456</Id>
<Date>2025-05-12T14:30:00</Date>    <!-- ISO 8601 -->
<Items>
<Item>
<Description>Producto A</Description>
<Quantity>2</Quantity>
<UnitPrice>500</UnitPrice>       <!-- Precio en centavos -->
</Item>
<Item>
<Description>Producto B</Description>
<Quantity>1</Quantity>
<UnitPrice>1000</UnitPrice>
</Item>
</Items>
<TotalAmount>2000</TotalAmount>     <!-- Total en centavos -->
</Transaction>
```

---

## 5. DocumentData (XML)

**Clave:** `DocumentData`

**Formato:** XML con raíz `<documentData>` y campos del documento fiscal.

**Ejemplo:**

```xml
<documentData>
<documentType>INVOICE</documentType>     <!-- Tipo: INVOICE, RECEIPT, etc. -->
<documentNumber>F001-000123</documentNumber> <!-- Serie y número -->
<dateIssued>2025-05-12</dateIssued>      <!-- Fecha emisión YYYY-MM-DD -->
<dueDate>2025-05-19</dueDate>            <!-- Fecha vencimiento -->
<customer>
<name>Cliente X</name>
<id>C456789</id>
</customer>
</documentData>
```
*/