package com.example.yp_qr

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument


@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "mainScreen") {
        composable("mainScreen") {
            MainScreen(navController = navController)
        }
        composable(
            route = "qrResult/{date}/{transactionId}/{hash}",
            arguments = listOf(
                navArgument("date") { type = NavType.StringType },
                navArgument("transactionId") { type = NavType.StringType },
                navArgument("hash") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val date = backStackEntry.arguments?.getString("date") ?: ""
            val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
            val hash = backStackEntry.arguments?.getString("hash") ?: ""
            QrResultScreen(date = date, transactionId = transactionId, hash = hash)
        }
    }
}
