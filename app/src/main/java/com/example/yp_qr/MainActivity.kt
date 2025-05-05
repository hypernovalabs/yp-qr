package com.example.tefbanesco

import android.app.Activity
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

class MainActivity : ComponentActivity() {

    private lateinit var transactionHandler: TransactionHandler

    private val intentActionState = mutableStateOf<String?>(null)
    private val extrasState = mutableStateOf<Bundle?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(DebugTree())
        Timber.d("🔍 onCreate Intent: action=%s, extras=%s", intent?.action, intent?.extras)

        intentActionState.value = intent?.action
        extrasState.value = intent?.extras

        enableEdgeToEdge()
        ConfigManager.loadConfig(this)

        setContent {
            val intentAction by intentActionState
            val extras by extrasState

            var showSuccessScreen by remember { mutableStateOf(false) }
            var showCancelSuccessScreen by remember { mutableStateOf(false) }
            var showConfigDialog by remember { mutableStateOf(false) }
            var showErrorDialog by remember { mutableStateOf(false) }

            transactionHandler = TransactionHandler(
                activity = this,
                onSuccess = null // ✅ Ya no es necesario usarlo en MainActivity
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
                    Timber.d("🆗 Confirmado éxito, cerrando Activity")
                    showSuccessScreen = false
                    finish()
                },
                onCancelConfirm = {
                    Timber.d("❎ Confirmado cancelación, cerrando Activity")
                    showCancelSuccessScreen = false
                    finish()
                },
                onRequestCancelSuccess = {
                    Timber.d("🔄 Mostrar CancelSuccessScreen")
                    showCancelSuccessScreen = true
                },
                onRequestShowConfig = {
                    Timber.d("⚙️ Mostrar ConfigDialog")
                    showConfigDialog = true
                    showErrorDialog = false
                },
                onDismissConfig = {
                    Timber.d("✖️ Cerrar ConfigDialog y Activity")
                    showConfigDialog = false
                    finish()
                },
                onRequestShowError = {
                    Timber.d("🚨 Mostrar ErrorDialog")
                    showErrorDialog = true
                },
                onPaymentSuccess = {
                    Timber.d("🎉 onPaymentSuccess callback: showSuccessScreen = true")
                    showSuccessScreen = true
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Timber.d("🔄 onNewIntent: action=%s, extras=%s", intent.action, intent.extras)
        intentActionState.value = intent.action
        extrasState.value = intent.extras
    }
}
