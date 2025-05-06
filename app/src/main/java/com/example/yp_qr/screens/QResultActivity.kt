package com.example.tefbanesco.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.tefbanesco.storage.LocalStorage
import com.example.tefbanesco.network.ApiService
import com.example.tefbanesco.screens.CancelResultScreen
import com.example.tefbanesco.screens.SuccessResultScreen
import com.example.tefbanesco.screens.QrResultScreen
import kotlinx.coroutines.launch
import timber.log.Timber

class QrResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extraemos la fecha y el transactionId que ahora viene de Yappy
        val qrDate             = intent.getStringExtra("qrDate") ?: ""
        val yappyTransactionId = intent.getStringExtra("qrTransactionId") ?: ""
        val qrHash             = intent.getStringExtra("qrHash") ?: ""
        val qrAmount           = intent.getStringExtra("qrAmount") ?: ""

        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()

            var showCancelSuccess by remember { mutableStateOf(false) }
            var showPaymentSuccess by remember { mutableStateOf(false) }

            when {
                // 1) Pantalla de cancelación
                showCancelSuccess -> CancelResultScreen(
                    title = "Pago Cancelado",
                    message = "La transacción $yappyTransactionId fue cancelada exitosamente.",
                    onConfirm = { finish() }
                )

                // 2) Pantalla de éxito
                showPaymentSuccess -> SuccessResultScreen(
                    title = "Pago Confirmado",
                    message = "La transacción $yappyTransactionId se completó exitosamente.",
                    onConfirm = {
                        Timber.d("✅ SuccessScreen onConfirm: cerrando Activity")
                        finish()
                    }
                )

                // 3) Pantalla QR con polling y cancelación
                else -> QrResultScreen(
                    date            = qrDate,
                    transactionId   = yappyTransactionId,
                    hash            = qrHash,
                    amount          = qrAmount,
                    onCancelSuccess = {
                        Timber.d("🔔 QrResultActivity - onCancelSuccess: txn=$yappyTransactionId")
                        showCancelSuccess = true
                    },
                    onPaymentSuccess = {
                        Timber.d("🔔 QrResultActivity - onPaymentSuccess: txn=$yappyTransactionId")
                        coroutineScope.launch {
                            // a) Cerrar sesión de dispositivo
                            try {
                                val cfg       = LocalStorage.getConfig(context)
                                val token     = cfg["device_token"].orEmpty()
                                val apiKey    = cfg["api_key"].orEmpty()
                                val secretKey = cfg["secret_key"].orEmpty()
                                ApiService.closeDeviceSession(token, apiKey, secretKey)
                                Timber.d("🔒 Sesión cerrada correctamente")
                            } catch (e: Exception) {
                                Timber.e(e, "❌ Error cerrando sesión")
                            }
                            // b) Limpiar prefs
                            LocalStorage.clear(context)
                            Timber.d("🧹 LocalStorage limpiado")

                            showPaymentSuccess = true
                        }
                    }
                )
            }
        }
    }
}
