package com.robertochavez.timetracker

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

internal sealed class AppDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : AppDestination("home", "Dashboard", Icons.Outlined.BarChart)
    data object Tracking : AppDestination("tracking", "Tracking", Icons.Outlined.AccessTime)
    data object Places : AppDestination("places", "Places", Icons.Outlined.Home)
    data object Settings : AppDestination("settings", "Settings", Icons.Outlined.Settings)
}
