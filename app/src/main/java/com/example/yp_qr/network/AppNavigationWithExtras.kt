package com.example.tefbanesco.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import com.example.tefbanesco.screens.MainScreen
import com.example.tefbanesco.screens.QrResultScreen

@Composable
fun AppNavigationWithExtras(
    navController: NavHostController,
    navigateTo: String?,
    date: String?,
    transactionId: String?,
    hash: String?,
    amount: String?,
    onCancelSuccess: () -> Unit,
    onPaymentSuccess: () -> Unit    // ← Nuevo parámetro
) {
    NavHost(
        navController = navController,
        startDestination = "mainScreen"
    ) {
        composable("mainScreen") {
            MainScreen(navController)
        }
        composable(
            route = "qrResult/{date}/{transactionId}/{hash}/{amount}",
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("transactionId") { type = NavType.StringType },
                navArgument("hash") { type = NavType.StringType },
                navArgument("amount") { type = NavType.StringType }
            )
        ) { backStack ->
            val argDate = backStack.arguments?.getString("date") ?: ""
            val argTxn  = backStack.arguments?.getString("transactionId") ?: ""
            val argHash = backStack.arguments?.getString("hash") ?: ""
            val argAmt  = backStack.arguments?.getString("amount") ?: ""
            QrResultScreen(
                date            = argDate,
                transactionId   = argTxn,
                hash            = argHash,
                amount          = argAmt,
                onCancelSuccess = onCancelSuccess,
                onPaymentSuccess= onPaymentSuccess     // ← Pasamos el nuevo callback
            )
        }
    }

    LaunchedEffect(navigateTo, date, transactionId, hash, amount) {
        if (
            navigateTo == "qrResult" &&
            !date.isNullOrBlank() &&
            !transactionId.isNullOrBlank() &&
            !hash.isNullOrBlank() &&
            !amount.isNullOrBlank()
        ) {
            navController.navigate("qrResult/$date/$transactionId/$hash/$amount")
        }
    }
}
