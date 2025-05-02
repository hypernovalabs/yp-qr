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
import kotlinx.coroutines.launch
import timber.log.Timber

// 🎨 Paleta de colores
val PrimaryColor     = ComposeColor(0xFF1E88E5)
val PrimaryDark      = ComposeColor(0xFF1565C0)
val AccentColor      = ComposeColor(0xFF42A5F5)
val ErrorColor       = ComposeColor(0xFFFF7043)
val LightBackground = ComposeColor(0xFFF1F8FF)

@Composable
fun QrResultScreen(
    date: String,
    transactionId: String,
    hash: String,
    amount: String,
    onCancelSuccess: () -> Unit
) {
    // Sólo un log inicial
    LaunchedEffect(Unit) {
        Timber.d("▶ Showing QrResultScreen date=%s, txnId=%s, hash=%s, amount=%s",
            date, transactionId, hash, amount)
    }

    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCancelling by remember { mutableStateOf(false) }
    var cancelError by remember { mutableStateOf<String?>(null) }

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

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Monto a pagar: $amount",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ComposeColor.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            QrCodeWithBorder(hash = hash, size = 260, statusColor = PrimaryColor)

            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(32.dp))

            // ————— Botón Cancelar —————
            Button(
                onClick = {
                    Timber.d("✋ Cancel button clicked for txnId=%s", transactionId)
                    coroutineScope.launch {
                        isCancelling = true
                        cancelError = null

                        val config = LocalStorage.getConfig(context)
                        val token = config["device_token"] ?: ""
                        val apiKey = config["api_key"] ?: ""
                        val secretKey = config["secret_key"] ?: ""

                        Timber.d("🔄 Calling cancelTransaction api with txnId=%s, token=%s, apiKey=%s",
                            transactionId, token, apiKey)

                        val result = try {
                            ApiService.cancelTransaction(
                                transactionId = transactionId,
                                token = token,
                                apiKey = apiKey,
                                secretKey = secretKey
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "❌ cancelTransaction threw")
                            isCancelling = false
                            cancelError = "Exception: ${e.localizedMessage}"
                            return@launch
                        }

                        Timber.d("📥 cancelTransaction result=%s", result)
                        isCancelling = false

                        if (result.contains("Error") || result.contains("Excepción")) {
                            Timber.w("⚠️ cancelTransaction returned error payload")
                            cancelError = "Error al cancelar el pago: $result"
                        } else {
                            Timber.i("🎉 cancelTransaction successful for txnId=%s", transactionId)
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

            // Mostrar error de cancelación
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
fun HeaderSection() {
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
fun QrCodeWithBorder(hash: String, size: Int, statusColor: ComposeColor) {
    Card(
        modifier = Modifier
            .size((size + 32).dp)
            .padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = LightBackground),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .background(
                    brush = Brush.radialGradient(
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
fun QrCode(hash: String, size: Int) {
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

fun generateQRCode(text: String, width: Int, height: Int): Bitmap? {
    return try {
        val matrix: BitMatrix = MultiFormatWriter().encode(
            text, BarcodeFormat.QR_CODE, width, height
        )
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        Timber.e(e, "Error generating QR bitmap")
        null
    }
}
