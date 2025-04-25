package com.example.yp_qr

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.json.JSONObject


@Composable
fun TestInputDialog(
    onDismiss: () -> Unit,
    onSubmit: (Double) -> Unit,
    onQrResult: (date: String, transactionId: String, hash: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Endpoints
    val deviceEndpoint = "${ApiConfig.BASE_URL}/session/device"
    val qrEndpoint = "${ApiConfig.BASE_URL}/qr/generate/DYN"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Ingresar valor numérico", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        error = false
                    },
                    label = { Text("Valor") },
                    isError = error,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val value = input.toDoubleOrNull()
                            if (value != null) {
                                showConfirmDialog = true
                            } else {
                                error = true
                            }
                        },
                        enabled = !loading
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Confirmar")
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        val config = remember { mutableStateMapOf<String, String>() }
        LaunchedEffect(Unit) {
            val stored = LocalStorage.getConfig(context)
            config.putAll(stored)
        }
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirmar Envío al Endpoint") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Se enviará una consulta a:")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(deviceEndpoint, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("🔐 Headers:", style = MaterialTheme.typography.labelMedium)
                    Text("api-key: ${config["api_key"] ?: "-"}")
                    Text("secret-key: ${config["secret_key"] ?: "-"}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("📦 Body:", style = MaterialTheme.typography.labelMedium)
                    Text("device.id: ${config["device.id"]}")
                    Text("device.name: ${config["device.name"]}")
                    Text("device.user: ${config["device.user"]}")
                    Text("group_id: ${config["group_id"]}")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    loading = true
                    scope.launch {
                        try {
                            // Llama a openDeviceSession pasando el contexto
                            val token = ApiService.openDeviceSession(
                                apiKey = config["api_key"] ?: "",
                                secretKey = config["secret_key"] ?: "",
                                deviceId = config["device.id"] ?: "",
                                deviceName = config["device.name"] ?: "",
                                deviceUser = config["device.user"] ?: "",
                                groupId = config["group_id"] ?: "",
                                context = context
                            )
                            // Guarda el token obtenido en LocalStorage
                            LocalStorage.saveToken(context, token)

                            Toast.makeText(context, "🔐 Token generado correctamente", Toast.LENGTH_SHORT).show()

                            val (orderId, responseJson) = ApiService.generateQrWithToken(
                                endpoint = qrEndpoint,
                                token = token,
                                apiKey = config["api_key"] ?: "",
                                secretKey = config["secret_key"] ?: "",
                                inputValue = input.toDouble()
                            )

                            val json = JSONObject(responseJson)
                            if (json.has("body")) {
                                val body = json.getJSONObject("body")
                                val resultDate = body.optString("date")
                                val transactionId = body.optString("transactionId")
                                val resultHash = body.optString("hash")
                                onDismiss()
                                onQrResult(resultDate, transactionId, resultHash)
                            } else {
                                errorMessage = json.optString("message", "Error desconocido en la respuesta del QR")
                                showErrorDialog = true
                            }
                        } catch (e: Exception) {
                            errorMessage = e.localizedMessage ?: "Error desconocido"
                            showErrorDialog = true
                        } finally {
                            loading = false
                        }
                    }
                }) {
                    Text("Sí, enviar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("Cerrar")
                }
            },
            title = { Text("Error al generar QR") },
            text = { Text(errorMessage) }
        )
    }
}
