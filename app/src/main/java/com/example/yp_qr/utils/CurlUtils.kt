// File: utils/CurlUtils.kt
package com.example.yappy.utils

fun buildTransactionStatusCurl(
    baseUrl: String,
    transactionId: String,
    token: String,
    apiKey: String,
    secretKey: String
): String {
    return """
        curl -X GET "$baseUrl/transaction/$transactionId" \
          -H "Content-Type: application/json" \
          -H "Authorization: Bearer $token" \
          -H "api-key: $apiKey" \
          -H "secret-key: $secretKey"
    """.trimIndent()
}
