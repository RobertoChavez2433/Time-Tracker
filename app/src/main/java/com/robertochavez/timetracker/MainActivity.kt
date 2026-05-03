package com.robertochavez.timetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.robertochavez.timetracker.feature.home.HomeRoute
import com.robertochavez.timetracker.feature.reports.ReportsRoute
import com.robertochavez.timetracker.feature.settings.SettingsRoute
import com.robertochavez.timetracker.feature.tracking.TrackingRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TimeTrackerTheme {
                TimeTrackerApp()
            }
        }
    }
}

@Composable
private fun TimeTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

@Composable
private fun TimeTrackerApp() {
    val navController = rememberNavController()
    val destinations = listOf(
        AppDestination.Home,
        AppDestination.Tracking,
        AppDestination.Reports,
        AppDestination.Settings,
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppDestination.Home.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(destination.shortLabel) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(AppDestination.Home.route) {
                HomeRoute()
            }
            composable(AppDestination.Tracking.route) {
                TrackingRoute()
            }
            composable(AppDestination.Reports.route) {
                ReportsRoute()
            }
            composable(AppDestination.Settings.route) {
                SettingsRoute()
            }
        }
    }
}

private sealed class AppDestination(
    val route: String,
    val label: String,
    val shortLabel: String,
) {
    data object Home : AppDestination("home", "Home", "H")
    data object Tracking : AppDestination("tracking", "Tracking", "T")
    data object Reports : AppDestination("reports", "Reports", "R")
    data object Settings : AppDestination("settings", "Settings", "S")
}
