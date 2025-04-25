package com.example.yp_qr

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
            QrResultScreen(date = date, transactionId = transactionId, hash = hash)
        }
    }
}
