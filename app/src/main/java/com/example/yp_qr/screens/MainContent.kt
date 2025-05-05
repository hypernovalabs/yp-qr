package com.example.tefbanesco

import android.app.Activity
import android.os.Bundle
import androidx.compose.foundation.layout.*
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
import com.example.tefbanesco.screens.CancelSuccessScreen
import com.example.tefbanesco.screens.SuccessScreen
import timber.log.Timber

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
    onRequestShowError: () -> Unit,
    onPaymentSuccess: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()

    LaunchedEffect(intentAction) {
        if (intentAction == "icg.actions.electronicpayment.tefbanesco.TRANSACTION") {
            Timber.d("🚀 Lanzando TransactionHandler.handle()")
            transactionHandler.handle()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF2196F3)) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                showConfigDialog -> ConfigDialog(onDismiss = onDismissConfig)
                showErrorDialog -> ErrorHandler.showConfigurationError(context as Activity) { onRequestShowConfig() }
                transactionHandler.isLoading.value -> LoadingDialog("Procesando transacción...")
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
                    amount = extras?.getString("amount"),
                    onCancelSuccess = onRequestCancelSuccess,
                    onPaymentSuccess = onPaymentSuccess
                )
            }
        }
    }
}
