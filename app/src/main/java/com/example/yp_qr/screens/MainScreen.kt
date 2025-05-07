package com.example.yappy.screens

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
import com.example.yappy.components.SquareButton
import com.example.yappy.dialogs.ConfigDialog
import com.example.yappy.network.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    navController: NavController,
    isLoading: Boolean // ← nuevo parámetro para ocultar botón si está cargando
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showConfigDialog by remember { mutableStateOf(false) }
    val isOnline = remember { mutableStateOf(NetworkUtils.isConnected(context)) }

    // Efecto para actualizar estado de red periódicamente
    LaunchedEffect(Unit) {
        while (true) {
            isOnline.value = NetworkUtils.isConnected(context)
            delay(3000)
        }
    }

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

            // Botón central de configuración (solo si no está cargando)
            if (!isLoading) {
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
            }

            // Mostrar diálogo de configuración
            if (showConfigDialog) {
                ConfigDialog(onDismiss = { showConfigDialog = false })
            }
        }
    }
}
