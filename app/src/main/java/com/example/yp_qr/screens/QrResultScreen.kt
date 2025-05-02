package com.example.tefbanesco.screens

import android.graphics.Bitmap
import android.graphics.Color
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.text.NumberFormat
import java.util.Locale

// 🎨 Paleta de colores
private val PrimaryColor     = ComposeColor(0xFF1E88E5)
private val PrimaryDark      = ComposeColor(0xFF1565C0)
private val AccentColor      = ComposeColor(0xFF42A5F5)
private val ErrorColor       = ComposeColor(0xFFFF7043)
private val LightBackground  = ComposeColor(0xFFF1F8FF)

@Composable
fun QrResultScreen(
    date: String,
    transactionId: String,
    hash: String,
    amount: String,
    onCancelSuccess: () -> Unit,
    onPaymentSuccess: () -> Unit
) {
    // Log inicial
    LaunchedEffect(Unit) {
        Timber.d("▶ Showing QrResultScreen date=%s, txnId=%s, hash=%s, amount=%s",
            date, transactionId, hash, amount)
    }

    // Formatear monto como moneda USD
    val formattedAmount = remember(amount) {
        val value = amount.toDoubleOrNull() ?: 0.0
        NumberFormat.getCurrencyInstance(Locale.US).format(value)
    }

    // Credenciales
    val context = LocalContext.current
    val config  = LocalStorage.getConfig(context)
    val token     = config["device_token"] ?: ""
    val apiKey    = config["api_key"]      ?: ""
    val secretKey = config["secret_key"]   ?: ""

    // Estados de UI
    val scrollState      = rememberScrollState()
    val coroutineScope   = rememberCoroutineScope()
    var isCancelling     by remember { mutableStateOf(false) }
    var cancelError      by remember { mutableStateOf<String?>(null) }
    var currentStatus    by remember { mutableStateOf("") }

    // Indicador de polling activo
    val isCheckingStatus = remember(currentStatus, isCancelling) {
        currentStatus == "PENDING" && !isCancelling
    }

    // Polling de estado de transacción
    LaunchedEffect(transactionId) {
        while (true) {
            if (isCancelling) {
                Timber.d("Polling stopped: cancellation requested for txnId=%s", transactionId)
                break
            }
            Timber.d("Polling status for txnId=%s", transactionId)
            try {
                val resp = ApiService.getTransactionStatus(
                    transactionId = transactionId,
                    token = token,
                    apiKey = apiKey,
                    secretKey = secretKey
                )
                val body   = JSONObject(resp).optJSONObject("body")
                val status = body?.optString("status") ?: ""
                currentStatus = status
                Timber.d("Polled status=%s for txnId=%s", status, transactionId)
                if (status == "COMPLETED") {
                    Timber.i("Payment completed for txnId=%s", transactionId)
                    onPaymentSuccess()
                    break
                }
            } catch (e: Exception) {
                Timber.e(e, "Error polling transaction status for txnId=%s", transactionId)
            }
            delay(5_000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AccentColor, PrimaryDark),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
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
                text = "Monto a pagar: $formattedAmount",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ComposeColor.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(24.dp))

            QrCodeWithBorder(hash = hash, size = 260, statusColor = PrimaryColor)

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Escanea este código para realizar tu pago",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = ComposeColor.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            )

            // ————— Indicador de verificación de estado —————
            if (isCheckingStatus) {
                Spacer(Modifier.height(8.dp))
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
                    Text(
                        text = "Verificando estado...",
                        color = ComposeColor.White,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ————— Botón Cancelar —————
            Button(
                onClick = {
                    coroutineScope.launch {
                        if (currentStatus != "PENDING") {
                            cancelError = "No se puede cancelar. Estado: $currentStatus"
                            return@launch
                        }
                        isCancelling = true
                        cancelError  = null
                        Timber.d("✋ Cancel clicked for txnId=%s", transactionId)
                        val result = try {
                            ApiService.cancelTransaction(
                                transactionId = transactionId,
                                token = token,
                                apiKey = apiKey,
                                secretKey = secretKey
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "❌ cancelTransaction exception for txnId=%s", transactionId)
                            isCancelling = false
                            cancelError = "Exception: ${e.localizedMessage}"
                            return@launch
                        }
                        isCancelling = false
                        if (result.contains("Error") || result.contains("Excepción")) {
                            cancelError = "Error al cancelar el pago: $result"
                        } else {
                            onCancelSuccess()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.White),
                enabled = !isCancelling,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp)
                    .height(48.dp)
            ) {
                Text(
                    text = if (isCancelling) "Cancelando..." else "Cancelar Pago",
                    color = ErrorColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            cancelError?.let { error ->
                Text(
                    text = error,
                    color = ErrorColor,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun HeaderSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.yappy_logo),
            contentDescription = "Logo Yappy",
            modifier = Modifier.size(80.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun QrCodeWithBorder(hash: String, size: Int, statusColor: ComposeColor) {
    Card(
        modifier = Modifier
            .size((size + 32).dp)
            .padding(4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = LightBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(12.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(statusColor.copy(alpha = 0.1f), LightBackground)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            QrCode(hash = hash, size = size)
        }
    }
}

@Composable
private fun QrCode(hash: String, size: Int) {
    val qrBitmap = remember(hash) { generateQRCode(hash, size, size) }
    if (qrBitmap != null) {
        Image(
            bitmap = qrBitmap.asImageBitmap(),
            contentDescription = "QR Code",
            modifier = Modifier.size(size.dp)
        )
    } else {
        Card(
            modifier = Modifier
                .size(size.dp)
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Error al generar el código",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorColor,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

private fun generateQRCode(text: String, width: Int, height: Int): Bitmap? {
    return try {
        val matrix: BitMatrix = MultiFormatWriter().encode(
            text, BarcodeFormat.QR_CODE, width, height
        )
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until width) for (y in 0 until height) {
                setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Error generating QR bitmap")
        null
    }
}
