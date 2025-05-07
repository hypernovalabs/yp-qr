package com.example.yappy

import android.app.Activity
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.example.yappy.dialogs.ConfigDialog
import com.example.yappy.dialogs.LoadingDialog
import com.example.yappy.errors.ErrorHandler
import com.example.yappy.intenthandlers.TransactionHandler
import com.example.yappy.network.AppNavigationWithExtras
import com.example.yappy.screens.CancelResultScreen
import com.example.yappy.screens.SuccessResultScreen
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
        if (intentAction == "icg.actions.electronicpayment.yappy.TRANSACTION") {
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
                showSuccessScreen -> SuccessResultScreen(
                    title = "¡QR Generado!",
                    message = "Por favor escanee el QR para completar el pago.",
                    onConfirm = onSuccessConfirm
                )
                showCancelSuccessScreen -> CancelResultScreen(
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
                    onPaymentSuccess = onPaymentSuccess,
                    transactionHandler = transactionHandler // ✅ se pasa el handler para usar .isLoading
                )
            }
        }
    }
}
