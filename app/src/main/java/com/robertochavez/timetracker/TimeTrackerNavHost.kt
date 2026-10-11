package com.robertochavez.timetracker

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.robertochavez.timetracker.feature.home.HomeRoute
import com.robertochavez.timetracker.feature.home.PlaceEditorRoute
import com.robertochavez.timetracker.feature.reports.DayDetailRoute
import com.robertochavez.timetracker.feature.reports.HistoryRoute
import com.robertochavez.timetracker.feature.reports.ReportsRoute
import com.robertochavez.timetracker.feature.reports.WorksiteDetailRoute
import com.robertochavez.timetracker.feature.settings.SettingsRoute
import com.robertochavez.timetracker.feature.tracking.SessionCorrectionRoute
import com.robertochavez.timetracker.feature.tracking.TrackingStatus

@Composable
internal fun TimeTrackerNavHost(navController: androidx.navigation.NavHostController, modifier: Modifier) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.Home.route,
        modifier = modifier,
    ) {
        composable(AppDestination.Home.route) {
            ReportsRoute(
                onDay = { navController.navigate("day/$it") },
                onWorksite = { id, date -> navController.navigate("worksite/${Uri.encode(id)}/$date") },
                trackingStatus = { TrackingStatus() },
            )
        }
        composable(AppDestination.History.route) {
            HistoryRoute(onDay = { navController.navigate("day/$it") })
        }
        composable(AppDestination.Places.route) { entry ->
            HomeRoute(
                onEdit = { navController.navigate("place/work/${Uri.encode(it)}") },
                onEditHome = { navController.navigate("place/home") },
                onAdd = { navController.navigate("place/new") },
                result = entry.savedStateHandle.get<String>("place_result").orEmpty(),
            )
        }
        listOf("place/new", "place/home", "place/work/{worksiteId}").forEach { route ->
            composable(
                route,
                arguments = listOf(
                    navArgument("kind") { defaultValue = if (route == "place/home") "home" else "work" },
                    navArgument("worksiteId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                PlaceEditorRoute(
                    onBack = { navController.popBackStack() },
                    onSaved = { message ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("place_result", message)
                        navController.popBackStack()
                    },
                )
            }
        }
        composable("day/{date}", arguments = listOf(navArgument("date") { type = NavType.StringType })) {
            DayDetailRoute(onBack = {
                navController.popBackStack()
            }, onCorrection = { navController.navigate("session/${Uri.encode(it)}") })
        }
        composable("session/{sessionId}", arguments = listOf(navArgument("sessionId") { type = NavType.StringType })) {
            SessionCorrectionRoute(onBack = { navController.popBackStack() })
        }
        composable(
            "worksite/{worksiteId}/{date}",
            arguments = listOf(
                navArgument("worksiteId") { type = NavType.StringType },
                navArgument("date") { type = NavType.StringType },
            ),
        ) {
            WorksiteDetailRoute(onBack = { navController.popBackStack() }, onDay = { navController.navigate("day/$it") })
        }
        composable(AppDestination.Settings.route) {
            SettingsRoute(onSection = { navController.navigate("settings/$it") })
        }
        composable("settings/{section}", arguments = listOf(navArgument("section") { type = NavType.StringType })) { entry ->
            SettingsRoute(
                section = entry.arguments?.getString("section"),
                onSection = { navController.navigate("settings/$it") },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
