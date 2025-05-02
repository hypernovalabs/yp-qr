// File: SuccessScreen.kt
package com.example.tefbanesco.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

// Paleta de colores
val WhiteBg   = Color(0xFFF5F9F7)
val BlueLight = Color(0xFF22B7F5)
val BlueMid   = Color(0xFF1274BD)
val BlueDark  = Color(0xFF1A4665)
val Orange    = Color(0xFFFC943D)
val GrayDark  = Color(0xFF424242)


@Composable
fun SuccessScreen(
    title: String = "¡Pago Confirmado!",
    message: String = "Gracias por tu pago. Transacción ID: 123456789",
    onConfirm: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BlueLight
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = WhiteBg)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Success",
                        tint = BlueMid,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = BlueDark
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                fontSize = 16.sp,
                color = GrayDark
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlueMid)
            ) {
                Text(text = "Finalizar", color = Color.White)
            }
        }
    }
}