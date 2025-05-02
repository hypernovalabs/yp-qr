// ✅ MainActivity.kt
package com.example.tefbanesco

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.example.tefbanesco.dialogs.ConfigDialog
import com.example.tefbanesco.dialogs.LoadingDialog
import com.example.tefbanesco.errors.ErrorHandler
import com.example.tefbanesco.intenthandlers.TransactionHandler
import com.example.tefbanesco.network.ApiConfig
import com.example.tefbanesco.network.AppNavigationWithExtras
import com.example.tefbanesco.network.ConfigManager
import com.example.tefbanesco.screens.SuccessScreen
import com.example.tefbanesco.screens.CancelSuccessScreen

class MainActivity : ComponentActivity() {

    private lateinit var transactionHandler: TransactionHandler
    private var intentAction: String? = null
    private var extras: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intentAction = intent?.action
        extras = intent?.extras

        Log.d("MainActivity", "\uD83D\uDD0D Intent recibido: $intentAction")

        enableEdgeToEdge()
        ConfigManager.loadConfig(this)

        setContent {
            var showSuccessScreen by remember { mutableStateOf(false) }
            var showCancelSuccessScreen by remember { mutableStateOf(false) }
            var showConfigDialog by remember { mutableStateOf(false) }
            var showErrorDialog by remember { mutableStateOf(false) }

            transactionHandler = TransactionHandler(
                activity = this,
                onSuccess = { showSuccessScreen = true }
            )

            MainContent(
                intentAction = intentAction,
                extras = extras,
                transactionHandler = transactionHandler,
                showSuccessScreen = showSuccessScreen,
                showCancelSuccessScreen = showCancelSuccessScreen,
                showConfigDialog = showConfigDialog,
                showErrorDialog = showErrorDialog,
                onSuccessConfirm = {
                    showSuccessScreen = false
                    finish()
                },
                onCancelConfirm = {
                    showCancelSuccessScreen = false
                    finish()
                },
                onRequestCancelSuccess = {
                    showCancelSuccessScreen = true
                },
                onRequestShowConfig = {
                    showConfigDialog = true
                    showErrorDialog = false
                },
                onDismissConfig = {
                    showConfigDialog = false
                    finish()
                },
                onRequestShowError = {
                    showErrorDialog = true
                }
            )
        }
    }
}

@Composable
fun MainContent(
    intentAction: String?,
    extras: Bundle?,
    transactionHandler: TransactionHandler,
    showSuccessScreen: Boolean,
    showCancelSuccessScreen: Boolean,
    showConfigDialog: Boolean,
    showErrorDialog: Boolean,
    onSuccessConfirm: () -> Unit,
    onCancelConfirm: () -> Unit,
    onRequestCancelSuccess: () -> Unit,
    onRequestShowConfig: () -> Unit,
    onDismissConfig: () -> Unit,
    onRequestShowError: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()

    LaunchedEffect(intentAction) {
        if (intentAction == "icg.actions.electronicpayment.tefbanesco.TRANSACTION") {
            Log.d("MainContent", "\uD83D\uDE80 Ejecutando handler de TRANSACTION")
            transactionHandler.handle()
        } else {
            Log.d("MainContent", "ℹ️ Ignorando intent: $intentAction")
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF2196F3)) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                showConfigDialog -> ConfigDialog(onDismiss = onDismissConfig)
                showErrorDialog -> ErrorHandler.showConfigurationError(context as Activity) { onRequestShowConfig() }
                transactionHandler.isLoading.value -> LoadingDialog(message = "Procesando transacción...")
                showSuccessScreen -> SuccessScreen(
                    title = "¡QR Generado!",
                    message = "Por favor escanee el QR para completar el pago.",
                    onConfirm = onSuccessConfirm
                )
                showCancelSuccessScreen -> CancelSuccessScreen(
                    title = "Pago Cancelado",
                    message = "El pago fue cancelado exitosamente.",
                    onConfirm = onCancelConfirm
                )
                else -> AppNavigationWithExtras(
                    navController = navController,
                    navigateTo = extras?.getString("navigateTo"),
                    date = extras?.getString("date"),
                    transactionId = extras?.getString("transactionId"),
                    hash = extras?.getString("hash"),
                    onCancelSuccess = onRequestCancelSuccess
                )
            }
        }
    }
}
