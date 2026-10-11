package com.robertochavez.timetracker.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.robertochavez.timetracker.core.common.model.PayPeriodCadence
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun WorkdaysSection(workdays: List<WorkdayUiModel>, onTrackableChange: (String, Boolean) -> Unit) {
    Text("Tracked days", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), maxItemsInEachRow = 7) {
        workdays.forEach { day ->
            DayButton(day) { onTrackableChange(day.name, !day.trackable) }
        }
    }
    SettingsNote(
        if (workdays.none { it.trackable }) {
            "Automatic tracking is paused until you select a day."
        } else {
            "Automatic sessions can start on the selected days. Changes save immediately."
        },
        warning = workdays.none { it.trackable },
    )
}

@Composable
internal fun PayPeriodCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsGroup("Pay period") {
        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PayPeriodCadence.entries.forEach { cadence ->
                CadenceChip(cadence, state.payPeriodCadence == cadence) { viewModel.selectPayPeriodCadence(cadence) }
            }
        }
        SettingsDateRow(
            "A known period start",
            state.anchorDate,
            TimeTrackerTestTags.SETTINGS_ANCHOR_DATE_FIELD,
            viewModel::updateAnchorDate,
        )
    }
    SettingsNote("Current period: ${state.payPeriodPreview}")
    TimeTrackerPrimaryButton(
        text = if (state.runningAction == "pay_period") "Saving…" else "Save pay period",
        onClick = viewModel::saveAnchorDate,
        enabled = state.runningAction == null && state.payPeriodEdited,
        modifier = Modifier.testTag(TimeTrackerTestTags.SETTINGS_SAVE_ANCHOR_BUTTON),
    )
    if (state.payPeriodEdited) {
        TextButton(onClick = viewModel::discardPayPeriodEdits) { Text("Discard changes") }
    }
}

@Composable
private fun DayButton(day: WorkdayUiModel, onClick: () -> Unit) {
    val date = DayOfWeek.valueOf(day.name)
    val name = date.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val color = if (day.trackable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val textColor = if (day.trackable) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier.clip(CircleShape).background(color).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .selectable(day.trackable, role = Role.Checkbox, onClick = onClick).semantics { contentDescription = name }
            .testTag(TimeTrackerTestTags.workdaySwitch(day.name)).padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(date.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.bodySmall, color = textColor)
    }
}

@Composable
private fun CadenceChip(cadence: PayPeriodCadence, selected: Boolean, onClick: () -> Unit) {
    val weekly = cadence == PayPeriodCadence.WEEKLY
    FilterChip(
        selected,
        onClick,
        label = { Text(if (weekly) "Weekly" else "Every 2 weeks") },
        modifier = Modifier.testTag(
            if (weekly) TimeTrackerTestTags.SETTINGS_WEEKLY_CADENCE_BUTTON else TimeTrackerTestTags.SETTINGS_BIWEEKLY_CADENCE_BUTTON,
        ),
    )
}
