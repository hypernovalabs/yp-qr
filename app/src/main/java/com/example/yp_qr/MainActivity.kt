package com.example.yp_qr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import com.example.yp_qr.intenthandlers.*
import com.example.yp_qr.ui.theme.YpqrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        println("Intent action: ${intent?.action}")

        when (intent?.action) {
            "icg.actions.electronicpayment.tefbanesco.INITIALIZE" -> {
                InitializeHandler(this).handle()
                return
            }
            "icg.actions.electronicpayment.tefbanesco.SHOW_SETUP_SCREEN" -> {
                ShowSetupHandler(this).handle()
                return
            }
            "icg.actions.electronicpayment.tefbanesco.FINALIZE" -> {
                FinalizeHandler(this).handle()
                return
            }
            "icg.actions.electronicpayment.tefbanesco.GET_BEHAVIOR" -> {
                GetBehaviorHandler(this).handle()
                return
            }
            "icg.actions.electronicpayment.tefbanesco.GET_VERSION" -> {
                GetVersionHandler(this).handle()
                return
            }
            "icg.actions.electronicpayment.tefbanesco.GET_CUSTOM_PARAMS" -> {
                GetCustomParamsHandler(this).handle()
                return
            }
            "icg.actions.electronicpayment.tefbanesco.TRANSACTION" -> {
                TransactionHandler(this).handle()
                return
            }
        }

        // Parámetros para navegación condicional
        val navigateTo = intent?.getStringExtra("navigateTo")
        val qrDate = intent?.getStringExtra("date")
        val qrTransactionId = intent?.getStringExtra("transactionId")
        val qrHash = intent?.getStringExtra("hash")

        enableEdgeToEdge()
        setContent {
            YpqrTheme {
                Surface(modifier = Modifier, color = Color(0xFF2196F3)) {
                    val navController = rememberNavController()
                    AppNavigationWithExtras(
                        navController = navController,
                        navigateTo = navigateTo,
                        date = qrDate,
                        transactionId = qrTransactionId,
                        hash = qrHash
                    )
                }
            }
        }
    }
}
