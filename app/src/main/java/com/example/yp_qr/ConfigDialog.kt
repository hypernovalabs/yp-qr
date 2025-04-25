package com.example.yp_qr

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
import kotlinx.coroutines.launch

@Composable
fun ConfigDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    // Estado del resumen
    var showSummary by remember { mutableStateOf(false) }

    // Inputs
    var apiKey by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    var deviceUser by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf("") }

    // Cargar configuración existente al abrir
    LaunchedEffect(Unit) {
        val config = LocalStorage.getConfig(context)
        if (config.values.any { it.isNotEmpty() }) {
            apiKey = config["api_key"] ?: ""
            secretKey = config["secret_key"] ?: ""
            deviceId = config["device.id"] ?: ""
            deviceName = config["device.name"] ?: ""
            deviceUser = config["device.user"] ?: ""
            groupId = config["group_id"] ?: ""
            showSummary = true
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(screenWidth * 0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.background
        ) {
            if (showSummary) {
                // 🌟 RESUMEN
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()) // ✅ Scroll habilitado
                ) {
                    Text("Resumen de Configuración", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("API Key: $apiKey")
                    Text("Secret Key: $secretKey")
                    Text("Device ID: $deviceId")
                    Text("Device Name: $deviceName")
                    Text("Device User: $deviceUser")
                    Text("Group ID: $groupId")

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = {
                            showSummary = false
                        }) {
                            Text("Editar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            scope.launch {
                                LocalStorage.clear(context)
                                onDismiss()
                            }
                        }) {
                            Text("Eliminar")
                        }
                    }
                }
            }

            else {
                // 🧾 FORMULARIO
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Configuración API", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Fila 1
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Key") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = secretKey,
                            onValueChange = { secretKey = it },
                            label = { Text("Secret Key") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Fila 2
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = deviceId,
                            onValueChange = { deviceId = it },
                            label = { Text("Device ID") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = deviceName,
                            onValueChange = { deviceName = it },
                            label = { Text("Device Name") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Fila 3
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = deviceUser,
                            onValueChange = { deviceUser = it },
                            label = { Text("Device User") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = groupId,
                            onValueChange = { groupId = it },
                            label = { Text("Group ID") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (apiKey.isNotBlank() && secretKey.isNotBlank() &&
                                deviceId.isNotBlank() && deviceName.isNotBlank() &&
                                deviceUser.isNotBlank() && groupId.isNotBlank()
                            ) {
                                scope.launch {
                                    LocalStorage.saveConfig(
                                        context,
                                        apiKey,
                                        secretKey,
                                        deviceId,
                                        deviceName,
                                        deviceUser,
                                        groupId
                                    )
                                    showSummary = true
                                }
                            }
                        }) {
                            Text("Guardar")
                        }
                    }
                }
            }
        }
    }
}
