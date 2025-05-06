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
    val endpointForCurl = config["endpoint"]?.takeIf { it.isNotBlank() }
        ?: "https://api-integrationcheckout-uat.yappycloud.com/v1"

    val scrollState    = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var isCancelling   by remember { mutableStateOf(false) }
    var cancelError    by remember { mutableStateOf<String?>(null) }
    var currentStatus  by remember { mutableStateOf("PENDING") }
    var rawApiResponse by remember { mutableStateOf<String?>(null) }

    val isCheckingStatus = currentStatus == "PENDING" && !isCancelling

    // Polling transaction status
    LaunchedEffect(transactionId, token, apiKey, secretKey) {
        while (true) {
            if (isCancelling) break
            if (token.isBlank() || apiKey.isBlank() || secretKey.isBlank()) {
                rawApiResponse = "Error: missing credentials for polling"
                currentStatus  = "ERROR_CONFIG"
                delay(10_000)
                continue
            }

            try {
                val response = ApiService.getTransactionStatus(
                    transactionId, token, apiKey, secretKey
                )
                rawApiResponse = response
                val json       = JSONObject(response)
                val statusBody = json.optJSONObject("body")
                    ?.optString("status")
                currentStatus  = statusBody.takeUnless { it.isNullOrBlank() }
                    ?: json.optString("code", currentStatus)

                when (currentStatus.uppercase(Locale.US)) {
                    "COMPLETED"  -> { onPaymentSuccess(); break }
                    "CANCELLED", "FAILED", "EXPIRED" -> { onCancelSuccess(); break }
                }
            } catch (e: Exception) {
                rawApiResponse = "Polling error: ${e.localizedMessage}"
                currentStatus  = "POLLING_EXCEPTION"
            }
            delay(5_000)
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

            // Mostrar datos de transacción
            Text(
                text      = "Transacción: $transactionId",
                fontSize  = 14.sp,
                fontWeight= FontWeight.Medium,
                color     = ComposeColor.White,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
            Text(
                text      = "Fecha: $date",
                fontSize  = 14.sp,
                fontWeight= FontWeight.Medium,
                color     = ComposeColor.White,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
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

            Spacer(Modifier.height(12.dp))
            Text(
                text      = "Estado actual: $currentStatus",
                fontSize  = 14.sp,
                color     = ComposeColor.White,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )

            rawApiResponse?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text      = "Respuesta API:",
                    fontSize  = 12.sp,
                    fontWeight= FontWeight.Bold,
                    color     = ComposeColor.LightGray,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth()
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = ComposeColor.Black.copy(alpha = 0.2f)
                ) {
                    Text(
                        text      = it,
                        fontSize  = 10.sp,
                        color     = ComposeColor.White,
                        textAlign = TextAlign.Start,
                        modifier  = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text      = "Device Token: ${token.take(20)}...",
                fontSize  = 10.sp,
                color     = ComposeColor.LightGray,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )

            if (isCheckingStatus) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment   = Alignment.CenterVertically,
                    horizontalArrangement= Arrangement.Center,
                    modifier            = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        strokeWidth=2.dp,
                        modifier   = Modifier.size(20.dp),
                        color      = ComposeColor.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Verificando estado...", color = ComposeColor.White, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(32.dp))
            // Cancel button
            Button(
                onClick = {
                    coroutineScope.launch {
                        if (currentStatus.uppercase(Locale.US) != "PENDING") return@launch
                        isCancelling = true
                        try {
                            val result = ApiService.cancelTransaction(
                                transactionId, token, apiKey, secretKey
                            )
                            rawApiResponse = result
                            val cancelled = JSONObject(result)
                                .optJSONObject("body")
                                ?.optString("status")
                                .equals("CANCELLED", true)
                            if (cancelled) onCancelSuccess() else cancelError = "Error al cancelar"
                        } catch (e: Exception) {
                            cancelError    = "Excepción: ${e.localizedMessage}"
                            rawApiResponse = cancelError
                        } finally {
                            isCancelling = false
                        }
                    }
                },
                enabled = !isCancelling,
                colors  = ButtonDefaults.buttonColors(containerColor = ComposeColor.White),
                modifier= Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(48.dp)
            ) {
                Text(if (isCancelling) "Cancelando..." else "Cancelar Pago", color = ErrorColor)
            }

            Spacer(Modifier.height(8.dp))
            // Copy cURL button
            Button(
                onClick = {
                    val curl = buildTransactionStatusCurl(
                        endpointForCurl, transactionId, token, apiKey, secretKey
                    )
                    val clipboard = context.getSystemService(
                        Context.CLIPBOARD_SERVICE
                    ) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("curl", curl))
                    Toast.makeText(context, "cURL copiado", Toast.LENGTH_SHORT).show()
                },
                colors  = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                modifier= Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(48.dp)
            ) {
                Text("📋 Copiar comando cURL", color = ComposeColor.White)
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
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter         = painter,
            contentDescription= "Logo Yappy",
            modifier        = Modifier.size(80.dp),
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