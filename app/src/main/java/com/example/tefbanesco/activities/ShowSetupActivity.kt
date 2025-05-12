package com.example.tefbanesco.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tefbanesco.storage.LocalStorage
import com.example.tefbanesco.utils.ErrorUtils
import timber.log.Timber

/**
 * Activity para manejar el intent SHOW_SETUP_SCREEN de HioPos.
 *
 * Esta Activity cumple con el requisito de la API v3.5 de HioPos de usar
 * setContent(), setResult() y finish() para devolver resultados a HioPos.
 */
class ShowSetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("[YAPPY] ShowSetupActivity: onCreate")

        setContent {
            SetupScreen(
                onSaveConfig = { success ->
                    if (success) {
                        // Configuración guardada con éxito
                        setResult(Activity.RESULT_OK)
                    } else {
                        // Error al guardar configuración
                        val errorIntent = Intent()
                        errorIntent.putExtra("ErrorMessage", "Error al guardar configuración")
                        errorIntent.putExtra("ErrorMessageTitle", "Error de Configuración")
                        setResult(Activity.RESULT_CANCELED, errorIntent)
                    }
                    // Finalizar activity y volver a HioPos
                    finish()
                },
                onCancel = {
                    // Usuario canceló la configuración
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            )
        }
    }
}

@Composable
fun SetupScreen(
    onSaveConfig: (Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val config = remember { LocalStorage.getConfig(context) }

    // Estado para los campos del formulario
    var apiKey by remember { mutableStateOf(config["api_key"] ?: "") }
    var secretKey by remember { mutableStateOf(config["secret_key"] ?: "") }
    var deviceId by remember { mutableStateOf(config["device.id"] ?: "") }
    var deviceName by remember { mutableStateOf(config["device.name"] ?: "") }
    var groupId by remember { mutableStateOf(config["group_id"] ?: "") }

    // Estado para mensajes de error
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Configuración Yappy",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // ApiKey
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // SecretKey
        OutlinedTextField(
            value = secretKey,
            onValueChange = { secretKey = it },
            label = { Text("Secret Key") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // DeviceId
        OutlinedTextField(
            value = deviceId,
            onValueChange = { deviceId = it },
            label = { Text("Device ID") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // DeviceName
        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("Device Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // GroupId
        OutlinedTextField(
            value = groupId,
            onValueChange = { groupId = it },
            label = { Text("Group ID") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        // Mensaje de error si existe
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botones
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    try {
                        // Validar campos obligatorios
                        if (apiKey.isBlank() || secretKey.isBlank() || deviceId.isBlank() || groupId.isBlank()) {
                            errorMessage = "Todos los campos son obligatorios excepto Device Name"
                            return@Button
                        }

                        // Guardar configuración
                        val newConfig = mapOf(
                            "api_key" to apiKey,
                            "secret_key" to secretKey,
                            "device.id" to deviceId,
                            "device.name" to deviceName,
                            "group_id" to groupId
                        )
                        LocalStorage.saveConfig(context, newConfig)

                        // Notificar éxito
                        Timber.i("[YAPPY] Configuración guardada correctamente")
                        onSaveConfig(true)
                    } catch (e: Exception) {
                        // Manejar error
                        Timber.e(e, "[YAPPY] Error al guardar configuración")
                        errorMessage = "Error: ${ErrorUtils.getErrorMessageFromException(e)}"
                        onSaveConfig(false)
                    }
                },
                modifier = Modifier.width(130.dp)
            ) {
                Text("Guardar")
            }

            Button(
                onClick = {
                    Timber.d("[YAPPY] Configuración cancelada por el usuario")
                    onCancel()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.width(130.dp)
            ) {
                Text("Cancelar")
            }
        }
    }
}