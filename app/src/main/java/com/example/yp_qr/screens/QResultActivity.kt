package com.example.tefbanesco.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class QrResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val date = intent.getStringExtra("qrDate") ?: ""
        val transactionId = intent.getStringExtra("qrTransactionId") ?: ""
        val hash = intent.getStringExtra("qrHash") ?: ""

        setContent {
            QrResultScreen(
                date = date,
                transactionId = transactionId,
                hash = hash,
                onCancelSuccess = {
                    // 🔵 Aquí defines qué pasa cuando se cancela el pago
                    finish() // Por ahora, simplemente cerrar la pantalla
                }
            )
        }
    }
}
