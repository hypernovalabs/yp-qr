package com.example.yp_qr.dialogs

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.yp_qr.network.ConfigManager
import com.example.yp_qr.network.ConfigNetworkHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ConfigDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    val snackbarHostState = remember { SnackbarHostState() } // 🔵 Snackbar state

    // Estados
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(screenWidth * 0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) } // 🔵 Host de Snackbar
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Iniciar Sesión", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Usuario") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { onDismiss() }, enabled = !isLoading) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    onLogin(
                                        context = context,
                                        username = username,
                                        password = password,
                                        snackbarHostState = snackbarHostState,
                                        onDismiss = onDismiss,
                                        setLoading = { isLoading = it }
                                    )
                                }
                            },
                            enabled = !isLoading
                        ) {
                            Text(if (isLoading) "Cargando..." else "Obtener Configuración")
                        }
                    }
                }
            }
        }
    }
}

private suspend fun onLogin(
    context: Context,
    username: String,
    password: String,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    setLoading: (Boolean) -> Unit
) {
    if (username.isBlank() || password.isBlank()) {
        snackbarHostState.showSnackbar("Por favor, completa todos los campos.")
        return
    }

    setLoading(true)

    val success = ConfigNetworkHelper.loginAndSaveConfig(context, username, password)
    if (success) {
        ConfigManager.loadConfig(context)
        onDismiss()
    } else {
        snackbarHostState.showSnackbar("Credenciales inválidas o error de conexión.")
    }

    setLoading(false)
}
