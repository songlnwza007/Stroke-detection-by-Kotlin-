package com.example.storkedetectionapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.storkedetectionapp.ui.facial.FacialDetectionScreen
import com.example.storkedetectionapp.ui.hand.HandExerciseScreen
import com.example.storkedetectionapp.ui.history.HistoryScreen
import com.example.storkedetectionapp.ui.home.HomeScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Facial : Screen("facial")
    object Hand : Screen("hand")
    object History : Screen("history")
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToFacial = { navController.navigate(Screen.Facial.route) },
                onNavigateToHand = { navController.navigate(Screen.Hand.route) },
                onNavigateToHistory = { navController.navigate(Screen.History.route) }
            )
        }

        composable(Screen.Facial.route) {
            FacialDetectionScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Hand.route) {
            HandExerciseScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}