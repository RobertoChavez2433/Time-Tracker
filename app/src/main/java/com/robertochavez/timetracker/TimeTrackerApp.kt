package com.robertochavez.timetracker

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.robertochavez.timetracker.core.designsystem.TimeTrackerColors
import com.robertochavez.timetracker.feature.home.HomeRoute
import com.robertochavez.timetracker.feature.reports.ReportsRoute
import com.robertochavez.timetracker.feature.settings.SettingsRoute
import com.robertochavez.timetracker.feature.tracking.TrackingRoute

@Composable
fun TimeTrackerApp() {
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
            NavigationBar(
                containerColor = TimeTrackerColors.SurfaceDark,
                tonalElevation = 0.dp,
            ) {
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
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { androidx.compose.material3.Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TimeTrackerColors.TextInverse,
                            selectedTextColor = TimeTrackerColors.PrimaryCyan,
                            indicatorColor = TimeTrackerColors.PrimaryCyan,
                            unselectedIconColor = TimeTrackerColors.TextSecondary,
                            unselectedTextColor = TimeTrackerColors.TextSecondary,
                        ),
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

private sealed class AppDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : AppDestination("home", "Home", Icons.Outlined.Home)
    data object Tracking : AppDestination("tracking", "Tracking", Icons.Outlined.AccessTime)
    data object Reports : AppDestination("reports", "Reports", Icons.Outlined.BarChart)
    data object Settings : AppDestination("settings", "Settings", Icons.Outlined.Settings)
}
