package com.example.tefbanesco.utils

/**
 * Proporciona funciones para generar plantillas XML utilizadas en las respuestas de transacciones TEF.
 */
object TefXmlTemplates {

    /**
     * Genera XML para los datos del comercio.
     */
    fun createShopDataXml(
        shopName: String,
        shopRif: String,
        shopAddress: String,
        terminalId: String
    ): String = """
        <ShopData>
            <Name>$shopName</Name>
            <RIF>$shopRif</RIF>
            <Address>$shopAddress</Address>
            <TerminalID>$terminalId</TerminalID>
        </ShopData>
    """.trimIndent()

    /**
     * Genera XML para los datos del vendedor/cajero.
     */
    fun createSellerDataXml(
        sellerId: String,
        sellerName: String
    ): String = """
        <SellerData>
            <ID>$sellerId</ID>
            <Name>$sellerName</Name>
        </SellerData>
    """.trimIndent()

    /**
     * Genera XML para un recibo de transacción (comercio o cliente).
     */
    fun createReceiptXml(
        receiptType: String, // "Merchant" o "Customer"
        isCustomerCopy: Boolean,
        shopName: String,
        shopRif: String,
        shopAddress: String,
        terminalId: String,
        timestamp: String,
        transactionType: String,
        transactionResult: String,
        batchNumber: String,
        transactionId: String?, // Contenido de transactionData
        amount: String, // Ejemplo: "100.00"
        currency: String, // Ejemplo: "VES"
        cardMasked: String, // Ejemplo: "************1234"
        sellerId: String
    ): String {
        val title = if (isCustomerCopy) "COPIA CLIENTE" else "COPIA COMERCIO"
        val safeTransactionId = transactionId ?: "N/A"

        return """
            <Receipt type="$receiptType">
                <Header>
                    <ShopName>$shopName</ShopName>
                    <RIF>$shopRif</RIF>
                    <Address>$shopAddress</Address>
                    <TerminalID>$terminalId</TerminalID>
                    <DateTime>$timestamp</DateTime>
                </Header>
                <TransactionDetails>
                    <TransactionType>$transactionType</TransactionType>
                    <TransactionResult>$transactionResult</TransactionResult>
                    <BatchNumber>$batchNumber</BatchNumber>
                    <TransactionID>$safeTransactionId</TransactionID>
                    <Amount currency="$currency">$amount</Amount>
                    ${if (isCustomerCopy || receiptType == "Merchant") "<CardMasked>$cardMasked</CardMasked>" else ""}
                </TransactionDetails>
                <SellerInfo>
                    <SellerID>$sellerId</SellerID>
                </SellerInfo>
                <Footer>
                    <Message>$title</Message>
                    ${if (isCustomerCopy) "<Message>Gracias por su compra!</Message>" else ""}
                </Footer>
            </Receipt>
        """.trimIndent()
    }

    /**
     * Genera XML para un recibo de cierre de lote.
     */
    fun createBatchReceiptXml(
        shopName: String,
        shopRif: String,
        terminalId: String,
        batchNumber: String,
        closeDateTime: String,
        totalSalesCount: String, // Ejemplo: "10"
        totalSalesAmount: String, // Ejemplo: "1500.75"
        totalReturnsCount: String, // Ejemplo: "1"
        totalReturnsAmount: String, // Ejemplo: "50.25"
        netBatchAmount: String, // Ejemplo: "1450.50"
        currency: String // Ejemplo: "VES"
    ): String = """
        <BatchReceipt>
            <Header>
                <ShopName>$shopName</ShopName>
                <RIF>$shopRif</RIF>
                <TerminalID>$terminalId</TerminalID>
                <BatchNumber>$batchNumber</BatchNumber>
                <CloseDateTime>$closeDateTime</CloseDateTime>
            </Header>
            <Summary>
                <TotalSalesCount>$totalSalesCount</TotalSalesCount>
                <TotalSalesAmount currency="$currency">$totalSalesAmount</TotalSalesAmount>
                <TotalReturnsCount>$totalReturnsCount</TotalReturnsCount>
                <TotalReturnsAmount currency="$currency">$totalReturnsAmount</TotalReturnsAmount>
                <NetBatchAmount currency="$currency">$netBatchAmount</NetBatchAmount>
            </Summary>
            <Footer>
                <Message>DETALLE DE CIERRE DE LOTE</Message>
            </Footer>
        </BatchReceipt>
    """.trimIndent()
}