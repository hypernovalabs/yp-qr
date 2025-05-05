package com.example.tefbanesco.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.tefbanesco.storage.LocalStorage
import com.example.tefbanesco.network.ApiService
//import com.example.tefbanesco.screens.CancelSuccessScreen
//import com.example.tefbanesco.screens.SuccessScreen
//import com.example.tefbanesco.screens.QrResultScreen
import kotlinx.coroutines.launch
import timber.log.Timber

class QrResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val date           = intent.getStringExtra("qrDate") ?: ""
        val transactionId  = intent.getStringExtra("qrTransactionId") ?: ""
        val hash           = intent.getStringExtra("qrHash") ?: ""
        val amount         = intent.getStringExtra("qrAmount") ?: ""

        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()

            var showCancelSuccess by remember { mutableStateOf(false) }
            var showPaymentSuccess by remember { mutableStateOf(false) }

            when {
                // 1) Pantalla de cancelación
                showCancelSuccess -> CancelResultScreen(
                    title = "Pago Cancelado",
                    message = "El pago fue cancelado exitosamente.",
                    onConfirm = { finish() }
                )

                // 2) Pantalla de éxito
                showPaymentSuccess -> SuccessResultScreen(
                    title = "Pago Confirmado",
                    message = "El pago se completó exitosamente.",
                    onConfirm = {
                        Timber.d("✅ SuccessScreen onConfirm: cerrando Activity")
                        finish()
                    }
                )

                // 3) Pantalla QR con polling y cancelación
                else -> QrResultScreen(
                    date            = date,
                    transactionId   = transactionId,
                    hash            = hash,
                    amount          = amount,
                    onCancelSuccess = {
                        Timber.d("🔔 QrResultActivity - onCancelSuccess: txn=$transactionId")
                        showCancelSuccess = true
                    },
                    onPaymentSuccess = {
                        Timber.d("🔔 QrResultActivity - onPaymentSuccess: txn=$transactionId")
                        // 2 acciones tras el pago:
                        coroutineScope.launch {
                            // a) Cerrar sesión de dispositivo
                            try {
                                // Recuperar credenciales
                                val cfg = LocalStorage.getConfig(context)
                                val token     = cfg["device_token"].orEmpty()
                                val apiKey    = cfg["api_key"].orEmpty()
                                val secretKey = cfg["secret_key"].orEmpty()
                                ApiService.closeDeviceSession(token, apiKey, secretKey)
                                Timber.d("🔒 Sesión cerrada correctamente")
                            } catch (e: Exception) {
                                Timber.e(e, "❌ Error cerrando sesión")
                            }
                            // b) Limpiar token y demás prefs
                            LocalStorage.clear(context)
                            Timber.d("🧹 LocalStorage limpiado")

                            // Mostrar pantalla de éxito
                            showPaymentSuccess = true
                        }
                    }
                )
            }
        }
    }
}
