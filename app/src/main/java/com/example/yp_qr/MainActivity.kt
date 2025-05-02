package com.example.tefbanesco

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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
import com.example.tefbanesco.network.AppNavigationWithExtras
import com.example.tefbanesco.network.ConfigManager
import com.example.tefbanesco.screens.CancelSuccessScreen
import com.example.tefbanesco.screens.SuccessScreen
import timber.log.Timber
import timber.log.Timber.DebugTree
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    private lateinit var transactionHandler: TransactionHandler

    // Estados para Compose que se actualizan en onNewIntent
    private val intentActionState = mutableStateOf<String?>(null)
    private val extrasState = mutableStateOf<Bundle?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar Timber en cualquier modo para capturar logs siempre
        Timber.plant(DebugTree())
        Timber.d("🔍 onCreate Intent recibido: action=%s, extras=%s", intent?.action, intent?.extras)

        // Cargar estado inicial de Intent
        intentActionState.value = intent?.action
        extrasState.value = intent?.extras

        enableEdgeToEdge()
        Timber.d("✨ Cargando configuración con ConfigManager.loadConfig")
        ConfigManager.loadConfig(this)

        setContent {
            // Observar cambios de Intent
            val intentAction by intentActionState
            val extras by extrasState

            var showSuccessScreen by remember { mutableStateOf(false) }
            var showCancelSuccessScreen by remember { mutableStateOf(false) }
            var showConfigDialog by remember { mutableStateOf(false) }
            var showErrorDialog by remember { mutableStateOf(false) }

            transactionHandler = TransactionHandler(
                activity = this,
                onSuccess = {
                    Timber.d("✅ onSuccess callback: showSuccessScreen = true")
                    showSuccessScreen = true
                }
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
                    Timber.d("🆗 Confirmando éxito y finalizando Activity")
                    showSuccessScreen = false
                    finish()
                },
                onCancelConfirm = {
                    Timber.d("❎ Confirmando cancelación y finalizando Activity")
                    showCancelSuccessScreen = false
                    finish()
                },
                onRequestCancelSuccess = {
                    Timber.d("🔄 Solicitud de pantalla de cancelación exitosa")
                    showCancelSuccessScreen = true
                },
                onRequestShowConfig = {
                    Timber.d("⚙️ Solicitando mostrar diálogo de configuración")
                    showConfigDialog = true
                    showErrorDialog = false
                },
                onDismissConfig = {
                    Timber.d("✖️ Diálogo de configuración cerrado, finalizando Activity")
                    showConfigDialog = false
                    finish()
                },
                onRequestShowError = {
                    Timber.d("🚨 Solicitando mostrar diálogo de error de configuración")
                    showErrorDialog = true
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Actualizar estado de Intent para que Compose reaccione
        Timber.d("🔄 onNewIntent: action=%s, extras=%s", intent.action, intent.extras)
        intentActionState.value = intent.action
        extrasState.value = intent.extras
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
        Timber.d("🔄 MainContent LaunchedEffect: intentAction=%s", intentAction)
        if (intentAction == "icg.actions.electronicpayment.tefbanesco.TRANSACTION") {
            Timber.d("🚀 Ejecutando TransactionHandler.handle()")
            transactionHandler.handle()
        } else {
            Timber.d("ℹ️ Ignorando intentAction=%s", intentAction)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF2196F3)) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                showConfigDialog -> ConfigDialog(onDismiss = onDismissConfig)
                showErrorDialog -> ErrorHandler.showConfigurationError(context as Activity) { onRequestShowConfig() }
                transactionHandler.isLoading.value -> LoadingDialog(message = "Procesando transacción..." )
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
                    amount = extras?.getString("amount"),       // ← Agregado monto
                    onCancelSuccess = onRequestCancelSuccess
                )
            }
        }
    }
}