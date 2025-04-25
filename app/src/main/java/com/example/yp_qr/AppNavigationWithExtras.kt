package com.example.yp_qr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavHostController

@Composable
fun AppNavigationWithExtras(
    navController: NavHostController,
    navigateTo: String?,
    date: String?,
    transactionId: String?,
    hash: String?
) {
    NavHost(
        navController = navController,
        startDestination = "mainScreen"
    ) {
        composable("mainScreen") {
            MainScreen(navController)
        }
        composable(
            route = "qrResult/{date}/{transactionId}/{hash}",
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("transactionId") { type = NavType.StringType },
                navArgument("hash") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val argDate = backStackEntry.arguments?.getString("date") ?: ""
            val argTransactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
            val argHash = backStackEntry.arguments?.getString("hash") ?: ""
            QrResultScreen(date = argDate, transactionId = argTransactionId, hash = argHash)
        }
    }

    // Navegación automática si se reciben parámetros
    LaunchedEffect(navigateTo, date, transactionId, hash) {
        if (
            navigateTo == "qrResult" &&
            !date.isNullOrBlank() &&
            !transactionId.isNullOrBlank() &&
            !hash.isNullOrBlank()
        ) {
            navController.navigate("qrResult/$date/$transactionId/$hash")
        }
    }
}
