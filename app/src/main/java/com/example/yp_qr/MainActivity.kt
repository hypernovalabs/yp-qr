package com.example.tefbanesco

import android.app.Activity // Importación general, puede ser útil
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import timber.log.Timber
import timber.log.Timber.DebugTree
import com.example.tefbanesco.intenthandlers.TransactionHandler
import com.example.tefbanesco.network.ConfigManager
import com.example.tefbanesco.utils.bundleExtrasToString // Asumiendo que está en utils.kt

class MainActivity : ComponentActivity() {

    private lateinit var transactionHandler: TransactionHandler

    private val intentActionState = mutableStateOf<String?>(null)
    private val extrasState = mutableStateOf<Bundle?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(DebugTree())
        Timber.d("🔍 onCreate Intent: action=%s, extras=${intent?.extras?.let { bundleExtrasToString(it) }}", intent?.action)

        intentActionState.value = intent?.action
        extrasState.value = intent?.extras

        enableEdgeToEdge()
        ConfigManager.loadConfig(this)

        setContent {
            val currentIntentAction by intentActionState
            val currentExtras by extrasState

            var showSuccessScreen by remember { mutableStateOf(false) }
            var showCancelSuccessScreen by remember { mutableStateOf(false) }
            var showConfigDialog by remember { mutableStateOf(false) }
            var showErrorDialog by remember { mutableStateOf(false) } // Manteniendo este estado

            // Inicializar TransactionHandler, se usa remember para que la instancia persista entre recomposiciones
            transactionHandler = remember { TransactionHandler(activity = this) }

            MainContent(
                intentAction = currentIntentAction,
                extras = currentExtras,
                transactionHandler = transactionHandler,
                showSuccessScreen = showSuccessScreen,
                showCancelSuccessScreen = showCancelSuccessScreen,
                showConfigDialog = showConfigDialog,
                showErrorDialog = showErrorDialog, // Pasando el estado
                onSuccessConfirm = {
                    Timber.d("🆗 Confirmado éxito, cerrando MainActivity")
                    showSuccessScreen = false
                    finish()
                },
                onCancelConfirm = {
                    Timber.d("❎ Confirmado cancelación, cerrando MainActivity")
                    showCancelSuccessScreen = false
                    finish()
                },
                onRequestCancelSuccess = {
                    Timber.d("🔄 MainContent solicita mostrar CancelSuccessScreen")
                    showCancelSuccessScreen = true
                },
                onRequestShowConfig = {
                    Timber.d("⚙️ MainContent solicita mostrar ConfigDialog")
                    showConfigDialog = true
                    showErrorDialog = false // Ocultar error si se muestra config
                },
                onDismissConfig = {
                    Timber.d("✖️ MainContent: ConfigDialog cerrado. Finalizando MainActivity.")
                    showConfigDialog = false
                    finish()
                },
                onRequestShowError = { // Manteniendo este callback
                    Timber.d("🚨 MainContent solicita mostrar ErrorDialog")
                    showErrorDialog = true
                },
                onPaymentSuccess = {
                    Timber.d("🎉 MainContent: onPaymentSuccess callback. Mostrando SuccessScreen.")
                    showSuccessScreen = true
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Timber.d("🔄 onNewIntent: action=%s, extras=${intent.extras?.let { bundleExtrasToString(it) }}", intent.action)
        setIntent(intent) // Actualizar el intent de la actividad
        intentActionState.value = intent.action
        extrasState.value = intent.extras
        // Considerar resetear estados de UI si es necesario
        // showSuccessScreen = false
        // showCancelSuccessScreen = false
        // showConfigDialog = false
        // showErrorDialog = false
    }
}