package com.example.tefbanesco.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.tefbanesco.screens.CancelSuccessScreen
import com.example.tefbanesco.screens.SuccessScreen
import timber.log.Timber

class QrResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Obtener datos del Intent
        val date          = intent.getStringExtra("qrDate") ?: ""
        val transactionId = intent.getStringExtra("qrTransactionId") ?: ""
        val hash          = intent.getStringExtra("qrHash") ?: ""
        val amount        = intent.getStringExtra("qrAmount") ?: ""

        // Log de los parámetros recibidos
        Timber.d(
            "🔍 QrResultActivity onCreate: date=%s, transactionId=%s, hash=%s, amount=%s",
            date, transactionId, hash, amount
        )

        setContent {
            // Estados para controlar qué pantalla mostrar
            var showCancelSuccess  by remember { mutableStateOf(false) }
            var showPaymentSuccess by remember { mutableStateOf(false) }

            when {
                // 1) Pago completado
                showPaymentSuccess -> {
                    SuccessScreen(
                        title   = "¡Pago Exitoso!",
                        message = "El pago se completó correctamente.",
                        onConfirm = {
                            Timber.d("✅ SuccessScreen onConfirm: cerrando QrResultActivity txnId=%s", transactionId)
                            finish()
                        }
                    )
                }
                // 2) Pago cancelado
                showCancelSuccess -> {
                    CancelSuccessScreen(
                        title   = "Pago Cancelado",
                        message = "El pago fue cancelado exitosamente.",
                        onConfirm = {
                            Timber.d("✅ CancelSuccessScreen onConfirm: cerrando QrResultActivity txnId=%s", transactionId)
                            finish()
                        }
                    )
                }
                // 3) Pantalla principal del QR
                else -> {
                    QrResultScreen(
                        date             = date,
                        transactionId    = transactionId,
                        hash             = hash,
                        amount           = amount,
                        onCancelSuccess  = {
                            Timber.d("🔔 QrResultActivity - onCancelSuccess for txnId=%s", transactionId)
                            showCancelSuccess = true
                        },
                        onPaymentSuccess = {
                            Timber.d("🤑 QrResultActivity - onPaymentSuccess for txnId=%s", transactionId)
                            showPaymentSuccess = true
                        }
                    )
                }
            }
        }
    }
}
