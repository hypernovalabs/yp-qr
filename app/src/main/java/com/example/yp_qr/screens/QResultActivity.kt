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

        // Obtiene datos del intent
        val date = intent.getStringExtra("qrDate") ?: ""
        val transactionId = intent.getStringExtra("qrTransactionId") ?: ""
        val hash = intent.getStringExtra("qrHash") ?: ""
        val amount = intent.getStringExtra("qrAmount") ?: ""

        setContent {
            // Estado para mostrar la pantalla de cancelación exitosa
            var showCancelSuccess by remember { mutableStateOf(false) }

            if (showCancelSuccess) {
                // Cuando se cancela con éxito, mostrar CancelSuccessScreen
                CancelSuccessScreen(
                    title = "Pago Cancelado",
                    message = "El pago fue cancelado exitosamente.",
                    onConfirm = { finish() }
                )
            } else {
                // Pantalla principal de QR
                QrResultScreen(
                    date = date,
                    transactionId = transactionId,
                    hash = hash,
                    amount = amount,
                    onCancelSuccess = {
                        // Al cancelar, cambia el estado y loguea el evento
                        Timber.d("🔔 QrResultActivity - onCancelSuccess: Pago cancelado para transactionId=%s", transactionId)
                        showCancelSuccess = true
                    }
                )
            }
        }
    }
}
