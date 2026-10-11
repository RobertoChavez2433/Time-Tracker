package com.robertochavez.timetracker.feature.home

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerCompactRow
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags

@Composable
fun HomeRoute(
    onEdit: (String) -> Unit,
    onEditHome: () -> Unit,
    onAdd: () -> Unit,
    result: String = "",
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TimeTrackerScreen(testTag = TimeTrackerTestTags.HOME_SCREEN) {
        item { TimeTrackerScreenTitle("Places", "Locations used for automatic tracking") }
        item { TimeTrackerPrimaryButton("Add Place", onAdd, Modifier.testTag(TimeTrackerTestTags.ADD_PLACE_BUTTON)) }
        if (result.isNotBlank()) item { TimeTrackerMutedText(result) }
        item {
            TimeTrackerCompactRow(
                "Home",
                state.home?.let { "${it.radiusLabel()} radius" } ?: "Not set",
                "Edit",
                onEditHome,
                Modifier.testTag(TimeTrackerTestTags.HOME_EDIT_PIN_BUTTON),
            )
            HorizontalDivider()
        }
        if (state.work.isEmpty()) item { TimeTrackerMutedText("No saved worksites yet.") }
        items(state.work, key = { it.id }) { place ->
            TimeTrackerCompactRow(
                place.label,
                "${place.projectNumber?.let {
                    "Job $it"
                } ?: "Unassigned"} · ${place.radiusLabel()} radius\n${place.address ?: "Pinned location"}",
                "Edit",
                { onEdit(place.id) },
                Modifier.testTag(TimeTrackerTestTags.savedPlaceItem(place.id)),
            )
            HorizontalDivider()
        }
    }
}
