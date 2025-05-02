package com.example.tefbanesco.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.tefbanesco.components.SquareButton
import com.example.tefbanesco.dialogs.ConfigDialog
import com.example.tefbanesco.network.NetworkUtils
import kotlinx.coroutines.launch

@Composable
fun MainScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showConfigDialog by remember { mutableStateOf(false) }

    val isOnline = remember { mutableStateOf(NetworkUtils.isConnected(context)) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Estado de conectividad en la esquina superior derecha
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    imageVector = if (isOnline.value) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = if (isOnline.value) "Conectado" else "Sin conexión",
                    tint = if (isOnline.value) Color(0xFF4CAF50) else Color.Red
                )
            }

            // Botón central de configuración
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                SquareButton(
                    text = "Configuración",
                    icon = Icons.Default.Build,
                    onClick = { showConfigDialog = true }
                )
            }

            // Mostrar diálogo de configuración
            if (showConfigDialog) {
                ConfigDialog(onDismiss = { showConfigDialog = false })
            }
        }
    }
}
