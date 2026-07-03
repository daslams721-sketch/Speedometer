package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.TripDetailScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.TrackingViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun MainAppScreen() {
    val navController = rememberNavController()
    val trackingViewModel: TrackingViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Show navigation bar only on main screens (Dashboard, History), not Detail Screen
    val showBottomBar = currentDestination?.route in listOf("dashboard", "history")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    // Speedometer Dashboard Item
                    NavigationBarItem(
                        icon = {
                            val selected = currentDestination?.hierarchy?.any { it.route == "dashboard" } == true
                            Icon(
                                imageVector = if (selected) Icons.Filled.Speed else Icons.Outlined.Speed,
                                contentDescription = "Dashboard"
                            )
                        },
                        label = { Text("Speedometer") },
                        selected = currentDestination?.hierarchy?.any { it.route == "dashboard" } == true,
                        onClick = {
                            navController.navigate("dashboard") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.testTag("nav_item_dashboard")
                    )

                    // History Log Item
                    NavigationBarItem(
                        icon = {
                            val selected = currentDestination?.hierarchy?.any { it.route == "history" } == true
                            Icon(
                                imageVector = if (selected) Icons.Filled.History else Icons.Outlined.History,
                                contentDescription = "History"
                            )
                        },
                        label = { Text("History") },
                        selected = currentDestination?.hierarchy?.any { it.route == "history" } == true,
                        onClick = {
                            navController.navigate("history") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.testTag("nav_item_history")
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") {
                DashboardScreen(
                    viewModel = trackingViewModel
                )
            }
            composable("history") {
                HistoryScreen(
                    viewModel = trackingViewModel,
                    onTripSelected = { tripId ->
                        navController.navigate("detail/$tripId")
                    }
                )
            }
            composable(
                route = "detail/{tripId}",
                arguments = listOf(navArgument("tripId") { type = NavType.LongType })
            ) { backStackEntry ->
                val tripId = backStackEntry.arguments?.getLong("tripId") ?: 0L
                TripDetailScreen(
                    tripId = tripId,
                    viewModel = trackingViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

