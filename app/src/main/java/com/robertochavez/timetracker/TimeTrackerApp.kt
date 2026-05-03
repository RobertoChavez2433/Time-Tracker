package com.robertochavez.timetracker

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.robertochavez.timetracker.core.designsystem.TimeTrackerColors
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags
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
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .testTag(TimeTrackerTestTags.APP_ROOT),
        containerColor = TimeTrackerColors.BackgroundBase,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag(TimeTrackerTestTags.BOTTOM_NAV),
                containerColor = TimeTrackerColors.SurfaceBase,
                tonalElevation = 0.dp,
            ) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        modifier = Modifier.testTag(TimeTrackerTestTags.navItem(destination.route)),
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
                            selectedTextColor = TimeTrackerColors.PrimaryOliveDark,
                            indicatorColor = TimeTrackerColors.PrimaryOlive,
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
