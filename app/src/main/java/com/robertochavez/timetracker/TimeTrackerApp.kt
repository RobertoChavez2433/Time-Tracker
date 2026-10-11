package com.robertochavez.timetracker

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.robertochavez.timetracker.core.common.model.UiDebugState
import com.robertochavez.timetracker.core.designsystem.TimeTrackerColors
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags

@Composable
fun TimeTrackerApp() {
    val navController = rememberNavController()
    val destinations = listOf(
        AppDestination.Home,
        AppDestination.History,
        AppDestination.Places,
        AppDestination.Settings,
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppDestination.Home.route
    LaunchedEffect(currentRoute) {
        UiDebugState.setRoute(currentRoute)
    }

    Scaffold(
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .testTag(TimeTrackerTestTags.APP_ROOT),
        containerColor = TimeTrackerColors.BackgroundBase,
        bottomBar = {
            if (destinations.any { it.route == currentRoute }) {
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
                            label = {
                                Text(
                                    destination.label,
                                    modifier = Modifier.widthIn(max = 72.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                )
                            },
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
            }
        },
    ) { padding ->
        TimeTrackerNavHost(navController, Modifier.padding(padding))
    }

    StartupSetupGate()
}
