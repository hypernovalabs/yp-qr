package com.example.yp_qr

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
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
import kotlinx.coroutines.launch

@Composable
fun MainScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showConfigDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }

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

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SquareButton(
                        text = "Test",
                        icon = Icons.Default.PlayArrow,
                        onClick = { showTestDialog = true }
                    )
                    SquareButton(
                        text = "Configuración",
                        icon = Icons.Default.Build,
                        onClick = { showConfigDialog = true }
                    )
                }
            }

            if (showConfigDialog) {
                ConfigDialog(onDismiss = { showConfigDialog = false })
            }

            if (showTestDialog) {
                TestInputDialog(
                    onDismiss = { showTestDialog = false },
                    onSubmit = { value ->
                        scope.launch {
                            snackbarHostState.showSnackbar("Número ingresado: $value")
                        }
                        showTestDialog = false
                    },
                    onQrResult = { date, transactionId, hash ->
                        // Aquí se navega a la pantalla de resultado del QR pasando los parámetros
                        navController.navigate("qrResult/$date/$transactionId/$hash")
                    }
                )
            }
        }
    }
}
