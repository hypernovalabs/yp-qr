package com.example.tefbanesco.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.tefbanesco.screens.CancelSuccessScreen
import com.example.tefbanesco.screens.QrResultScreen
import timber.log.Timber

class QrResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Obtener datos del Intent
        val date = intent.getStringExtra("qrDate") ?: ""
        val transactionId = intent.getStringExtra("qrTransactionId") ?: ""
        val hash = intent.getStringExtra("qrHash") ?: ""
        val amount = intent.getStringExtra("qrAmount") ?: ""

        // Log de los parámetros recibidos
        Timber.d(
            "🔍 QrResultActivity onCreate: date=%s, transactionId=%s, hash=%s, amount=%s",
            date, transactionId, hash, amount
        )

        setContent {
            // Estado para mostrar la pantalla de cancelación exitosa
            var showCancelSuccess by remember { mutableStateOf(false) }

            if (showCancelSuccess) {
                // Mostrar pantalla de éxito al cancelar
                CancelSuccessScreen(
                    title = "Pago Cancelado",
                    message = "El pago fue cancelado exitosamente.",
                    onConfirm = {
                        Timber.d(
                            "✅ CancelSuccessScreen onConfirm: cerrando QrResultActivity for transactionId=%s",
                            transactionId
                        )
                        finish()
                    }
                )
            } else {
                // Pantalla principal de QR
                QrResultScreen(
                    date = date,
                    transactionId = transactionId,
                    hash = hash,
                    amount = amount,
                    onCancelSuccess = {
                        Timber.d(
                            "🔔 QrResultActivity - onCancelSuccess: Pago cancelado para transactionId=%s",
                            transactionId
                        )
                        showCancelSuccess = true
                    }
                )
            }
        }
    }
}
