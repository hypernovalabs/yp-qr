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
    amount: String?,               // ← Nuevo parámetro
    onCancelSuccess: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = "mainScreen"
    ) {
        // Pantalla principal
        composable("mainScreen") {
            MainScreen(navController)
        }

        // Destino QR con los cuatro argumentos: date, transactionId, hash y amount
        composable(
            route = "qrResult/{date}/{transactionId}/{hash}/{amount}",
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("transactionId") { type = NavType.StringType },
                navArgument("hash") { type = NavType.StringType },
                navArgument("amount") { type = NavType.StringType }    // ← Argumento para el monto
            )
        ) { backStackEntry ->
            val argDate = backStackEntry.arguments?.getString("date") ?: ""
            val argTransactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
            val argHash = backStackEntry.arguments?.getString("hash") ?: ""
            val argAmount = backStackEntry.arguments?.getString("amount") ?: "" // ← Recupera el monto

            QrResultScreen(
                date = argDate,
                transactionId = argTransactionId,
                hash = argHash,
                amount = argAmount,                                       // ← Lo pasa al Composable
                onCancelSuccess = onCancelSuccess
            )
        }
    }

    // Lanzar la navegación cuando los extras lleguen
    LaunchedEffect(navigateTo, date, transactionId, hash, amount) {
        if (
            navigateTo == "qrResult" &&
            !date.isNullOrBlank() &&
            !transactionId.isNullOrBlank() &&
            !hash.isNullOrBlank() &&
            !amount.isNullOrBlank()                                  // ← Verifica también el monto
        ) {
            navController.navigate("qrResult/$date/$transactionId/$hash/$amount")
        }
    }
}
