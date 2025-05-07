package com.example.yappy.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import com.example.yappy.screens.MainScreen
import com.example.yappy.screens.QrResultScreen
import com.example.yappy.intenthandlers.TransactionHandler

@Composable
fun AppNavigationWithExtras(
    navController: NavHostController,
    navigateTo: String?,
    date: String?,
    transactionId: String?,
    hash: String?,
    amount: String?,
    onCancelSuccess: () -> Unit,
    onPaymentSuccess: () -> Unit,
    transactionHandler: TransactionHandler // ← agregado para acceder a isLoading
) {
    NavHost(
        navController = navController,
        startDestination = "mainScreen"
    ) {
        composable("mainScreen") {
            MainScreen(
                navController = navController,
                isLoading = transactionHandler.isLoading.value // ← se pasa el estado de carga
            )
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
                onPaymentSuccess= onPaymentSuccess
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
