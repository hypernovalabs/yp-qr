package com.example.tefbanesco.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tefbanesco.R
import com.example.tefbanesco.network.ApiService
import com.example.tefbanesco.storage.LocalStorage
import com.example.tefbanesco.utils.buildTransactionStatusCurl
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

// Color palette
private val PrimaryColor    = ComposeColor(0xFF1E88E5)
private val PrimaryDark     = ComposeColor(0xFF1565C0)
private val AccentColor     = ComposeColor(0xFF42A5F5)
private val ErrorColor      = ComposeColor(0xFFFF7043)
private val LightBackground = ComposeColor(0xFFF1F8FF)

@Composable
fun QrResultScreen(
    date: String,
    transactionId: String,
    hash: String,
    amount: String,
    onCancelSuccess: () -> Unit,
    onPaymentSuccess: () -> Unit
) {
    val context = LocalContext.current
    // Format amount
    val formattedAmount = remember(amount) {
        NumberFormat.getCurrencyInstance(Locale.US)
            .format(amount.toDoubleOrNull() ?: 0.0)
    }

    // Load credentials once
    val config          = LocalStorage.getConfig(context)
    val token           = config["device_token"] ?: ""
    val apiKey          = config["api_key"] ?: ""
    val secretKey       = config["secret_key"] ?: ""

    val scrollState    = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var isCancelling   by remember { mutableStateOf(false) }
    var cancelError    by remember { mutableStateOf<String?>(null) }
    var currentStatus  by remember { mutableStateOf("PENDING") }
    var rawApiResponse by remember { mutableStateOf<String?>(null) } // Keep for debugging if needed, though not displayed

    val isCheckingStatus = currentStatus == "PENDING" && !isCancelling

    // Polling transaction status with improved error handling and retry limits
    LaunchedEffect(transactionId, token, apiKey, secretKey) {
        var retryCount = 0
        val maxRetries = 12 // 12 retries × 5 seconds = 1 minute maximum polling time
        var pollInterval = 5_000L // Start with 5 seconds
        var consecutiveErrors = 0

        while (true) {
            // Break if cancelling or max retries reached
            if (isCancelling) break
            if (retryCount >= maxRetries) {
                currentStatus = "MAX_RETRIES_REACHED"
                Timber.w("[YAPPY] Max polling retries reached for transaction $transactionId")
                break
            }

            // Check credentials
            if (token.isBlank() || apiKey.isBlank() || secretKey.isBlank()) {
                rawApiResponse = "Error: missing credentials for polling"
                currentStatus = "ERROR_CONFIG"
                Timber.e("[YAPPY] Missing credentials for polling transaction status")
                delay(10_000) // Wait longer on config error
                retryCount++
                continue
            }

            try {
                Timber.d("[YAPPY] Polling status for transaction $transactionId (attempt ${retryCount+1}/$maxRetries)")
                val response = ApiService.getTransactionStatus(
                    transactionId, token, apiKey, secretKey
                )
                rawApiResponse = response // Keep for debugging
                val json = JSONObject(response)
                val statusBody = json.optJSONObject("body")
                    ?.optString("status")

                // Get status from response
                currentStatus = statusBody.takeUnless { it.isNullOrBlank() }
                    ?: json.optString("code", currentStatus) // Fallback to code if body status is null/blank

                Timber.d("[YAPPY] Transaction status: $currentStatus")

                // Reset error counter on successful API call
                consecutiveErrors = 0

                // Check for terminal states
                when (currentStatus.uppercase(Locale.US)) {
                    "COMPLETED" -> {
                        Timber.i("[YAPPY] Transaction COMPLETED. Calling onPaymentSuccess()")
                        onPaymentSuccess()
                        break
                    }
                    "CANCELLED", "FAILED", "EXPIRED" -> {
                        Timber.i("[YAPPY] Transaction $currentStatus. Calling onCancelSuccess()")
                        onCancelSuccess()
                        break
                    }
                }
            } catch (e: Exception) {
                // Increment error counter
                consecutiveErrors++

                // Categorize errors for better feedback
                val errorMsg = when {
                    e is java.net.UnknownHostException ||
                    e is java.net.ConnectException -> {
                        "NETWORK_ERROR"
                    }
                    e is java.net.SocketTimeoutException -> {
                        "TIMEOUT_ERROR"
                    }
                    e.message?.contains("401", true) == true -> {
                        "AUTH_ERROR"
                    }
                    e.message?.contains("500", true) == true -> {
                        "SERVER_ERROR"
                    }
                    else -> "POLLING_ERROR"
                }

                rawApiResponse = "Polling error: ${e.localizedMessage}"
                currentStatus = errorMsg

                Timber.e(e, "[YAPPY] Error polling transaction status: $errorMsg")

                // Implement exponential backoff for network errors
                if (consecutiveErrors > 1) {
                    pollInterval = (pollInterval * 1.5).toLong().coerceAtMost(15_000L) // Max 15 seconds
                    Timber.d("[YAPPY] Increasing poll interval to $pollInterval ms after $consecutiveErrors consecutive errors")
                }
            }

            retryCount++
            delay(pollInterval)
        }
    }

    // Screen content
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AccentColor, PrimaryDark)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeaderSection()
            Spacer(Modifier.height(12.dp))

            Text(
                text      = "Monto a pagar: $formattedAmount",
                fontSize  = 20.sp,
                fontWeight= FontWeight.Bold,
                color     = ComposeColor.White,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
            QrCodeWithBorder(hash, 260, PrimaryColor)
            Spacer(Modifier.height(24.dp))

            Text(
                text      = "Escanea este código para realizar tu pago",
                fontSize  = 18.sp,
                fontWeight= FontWeight.SemiBold,
                color     = ComposeColor.White,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // Status display with human-readable messages and color indicators
            val (statusMessage, statusColor) = when (currentStatus.uppercase(Locale.US)) {
                "PENDING" -> "Esperando pago..." to ComposeColor.White
                "COMPLETED" -> "¡Pago Completado!" to ComposeColor(0xFF4CAF50) // Green
                "CANCELLED" -> "Pago Cancelado" to ErrorColor
                "FAILED" -> "Pago Fallido" to ErrorColor
                "EXPIRED" -> "Pago Expirado" to ErrorColor
                "NETWORK_ERROR" -> "Error de Conexión" to ErrorColor
                "TIMEOUT_ERROR" -> "Tiempo de Espera Agotado" to ErrorColor
                "AUTH_ERROR" -> "Error de Autenticación" to ErrorColor
                "SERVER_ERROR" -> "Error del Servidor" to ErrorColor
                "ERROR_CONFIG" -> "Error de Configuración" to ErrorColor
                "MAX_RETRIES_REACHED" -> "Tiempo de Espera Agotado" to ErrorColor
                else -> "Estado: $currentStatus" to ComposeColor.White
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = statusColor.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp)
                ) {
                    Text(
                        text = statusMessage,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        textAlign = TextAlign.Center
                    )

                    // Show additional help text based on status
                    if (currentStatus.uppercase(Locale.US) == "PENDING") {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Abre la app de Yappy y escanea el código QR",
                            fontSize = 14.sp,
                            color = ComposeColor.White,
                            textAlign = TextAlign.Center
                        )
                    } else if (currentStatus.contains("ERROR", true)) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Intenta de nuevo o contacta a soporte",
                            fontSize = 14.sp,
                            color = ComposeColor.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (isCheckingStatus) {
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                        color = ComposeColor.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Verificando estado...", color = ComposeColor.White, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(32.dp))
            // Cancel button with improved error handling
            Button(
                onClick = {
                    coroutineScope.launch {
                        if (currentStatus.uppercase(Locale.US) != "PENDING") return@launch // Only allow cancelling PENDING
                        isCancelling = true
                        cancelError = null // Reset any previous error

                        try {
                            Timber.d("[YAPPY] Attempting to cancel transaction $transactionId")
                            val result = ApiService.cancelTransaction(
                                transactionId, token, apiKey, secretKey
                            )
                            rawApiResponse = result // Keep for debugging

                            // Parse the response
                            val json = JSONObject(result)
                            val bodyObj = json.optJSONObject("body")
                            val status = bodyObj?.optString("status")
                            val code = json.optString("code", "")

                            when {
                                status.equals("CANCELLED", true) -> {
                                    Timber.i("[YAPPY] Transaction successfully cancelled")
                                    onCancelSuccess()
                                }
                                code.equals("YP-0000", true) && status.isNullOrBlank() -> {
                                    // Status returned null but code is success
                                    Timber.i("[YAPPY] Transaction likely cancelled (success code but no status)")
                                    onCancelSuccess()
                                }
                                code.startsWith("YP-", true) -> {
                                    // Error code from Yappy
                                    val message = json.optString("message", "Error al cancelar")
                                    cancelError = "Error Yappy: $message"
                                    Timber.w("[YAPPY] Cancellation error: $code - $message")
                                }
                                status.equals("COMPLETED", true) -> {
                                    // Can't cancel a completed transaction
                                    cancelError = "No se puede cancelar: transacción ya completada"
                                    Timber.w("[YAPPY] Can't cancel: transaction already COMPLETED")

                                    // Since it's completed, notify success
                                    onPaymentSuccess()
                                }
                                else -> {
                                    // Unknown error
                                    cancelError = "Error desconocido al cancelar"
                                    Timber.w("[YAPPY] Unknown cancel error. Code: $code, Status: $status")
                                }
                            }
                        } catch (e: Exception) {
                            // Categorize errors for better messages
                            cancelError = when {
                                e is java.net.UnknownHostException ||
                                e is java.net.ConnectException ->
                                    "Error de conexión al intentar cancelar"
                                e is java.net.SocketTimeoutException ->
                                    "Tiempo de espera agotado al cancelar"
                                e.message?.contains("401", true) == true ->
                                    "Error de autenticación al cancelar"
                                else -> "Error al cancelar: ${e.message}"
                            }

                            rawApiResponse = "Cancel error: ${e.localizedMessage}"
                            Timber.e(e, "[YAPPY] Exception while cancelling transaction: $cancelError")
                        } finally {
                            isCancelling = false
                        }
                    }
                },
                enabled = !isCancelling && currentStatus.uppercase(Locale.US) == "PENDING", // Only enabled if not cancelling and status is PENDING
                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.White),
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth() // Make button fill width
                    .height(48.dp)
            ) {
                Text(if (isCancelling) "Cancelando..." else "Cancelar Pago", color = ErrorColor)
            }

            cancelError?.let { errorMsg ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text      = errorMsg,
                    color     = ErrorColor,
                    fontSize  = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeaderSection() {
    val painter = painterResource(id = R.drawable.yappy_logo)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Adjust height to accommodate larger logo if needed, or let Box wrap content
            .padding(vertical = 16.dp), // Add some vertical padding
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter         = painter,
            contentDescription= "Logo Yappy",
            modifier        = Modifier.size(120.dp), // Increased logo size here
            contentScale    = ContentScale.Fit
        )
    }
}

@Composable
private fun QrCodeWithBorder(hash: String, size: Int, statusColor: ComposeColor) {
    Card(
        modifier = Modifier.size((size + 32).dp),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = LightBackground),
        elevation= CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(statusColor.copy(alpha = 0.05f), LightBackground),
                        radius = size.toFloat() * 0.75f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            QrCode(hash, size)
        }
    }
}

@Composable
private fun QrCode(hash: String, size: Int) {
    val qrBitmap = remember(hash) { generateQRCode(hash, size, size) }
    if (qrBitmap != null) {
        Image(
            bitmap            = qrBitmap.asImageBitmap(),
            contentDescription= "QR Code",
            modifier          = Modifier.size(size.dp)
        )
    } else {
        Card(
            modifier = Modifier.size(size.dp),
            shape    = RoundedCornerShape(8.dp),
            colors   = CardDefaults.cardColors(containerColor = ErrorColor.copy(alpha = 0.1f))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text      = "Error al generar QR",
                    style     = MaterialTheme.typography.bodyMedium.copy(fontSize = (size / 15).sp),
                    color     = ErrorColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun generateQRCode(text: String, width: Int, height: Int): Bitmap? {
    if (text.isBlank()) return null
    return try {
        val matrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until width) {
                for (y in 0 until height) {
                    setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Error generating QR bitmap for text: $text")
        null
    }
}