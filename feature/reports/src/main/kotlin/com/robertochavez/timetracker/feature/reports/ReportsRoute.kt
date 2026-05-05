package com.robertochavez.timetracker.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerColors
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPanel
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSpacing
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags

@Composable
fun ReportsRoute(modifier: Modifier = Modifier, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    TimeTrackerScreen(modifier = modifier, testTag = TimeTrackerTestTags.DASHBOARD_SCREEN) {
        item {
            TimeTrackerScreenTitle(
                title = "Dashboard",
                subtitle = "Daily Home and Work tallies.",
            )
        }
        item {
            DashboardSummaryPanel(state.summaries)
        }
        item {
            WeeklyLedgerPanel(state.weeklyLedger)
        }
    }
}

@Composable
private fun DashboardSummaryPanel(summaries: List<DashboardSummaryUiModel>) {
    TimeTrackerPanel(modifier = Modifier.testTag(TimeTrackerTestTags.DASHBOARD_SUMMARY_PANEL)) {
        Row(horizontalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.Medium)) {
            summaries.forEach { summary ->
                SummaryColumn(summary = summary, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryColumn(summary: DashboardSummaryUiModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.XSmall)) {
        Text(summary.title, color = TimeTrackerColors.PrimaryOliveDark, style = MaterialTheme.typography.titleSmall)
        TimeTrackerMutedText("Home ${summary.home}")
        TimeTrackerMutedText("Work ${summary.work}")
        TimeTrackerMutedText("Miles ${summary.miles}")
    }
}

@Composable
private fun WeeklyLedgerPanel(ledger: WeeklyLedgerUiModel) {
    TimeTrackerPanel(modifier = Modifier.testTag(TimeTrackerTestTags.DASHBOARD_WEEKLY_LEDGER)) {
        Text(ledger.title, color = TimeTrackerColors.PrimaryOliveDark, style = MaterialTheme.typography.titleMedium)
        TimeTrackerMutedText(ledger.dateRange)
        Column(modifier = Modifier.fillMaxWidth()) {
            LedgerHeaderRow()
            ledger.rows.forEach { row ->
                LedgerDayRow(row)
            }
            HorizontalDivider(color = TimeTrackerColors.Divider)
            LedgerDayRow(ledger.total, isTotal = true)
        }
    }
}

@Composable
private fun LedgerHeaderRow() {
    LedgerRow(background = Color.Transparent) {
        LedgerCell("Day", FontWeight.SemiBold, TextAlign.Center)
        LedgerCell("Home", FontWeight.SemiBold, TextAlign.Center)
        LedgerCell("Work", FontWeight.SemiBold, TextAlign.Center)
        LedgerCell("Drive", FontWeight.SemiBold, TextAlign.Center)
        LedgerCell("Site", FontWeight.SemiBold, TextAlign.Center)
        LedgerCell("Miles", FontWeight.SemiBold, TextAlign.Center)
    }
}

@Composable
private fun LedgerDayRow(row: WeeklyLedgerRowUiModel, isTotal: Boolean = false) {
    val background = if (row.isToday) TimeTrackerColors.SurfaceTint else Color.Transparent
    val weight = if (row.isToday || isTotal) FontWeight.SemiBold else FontWeight.Normal
    LedgerRow(background = background) {
        LedgerCell(row.day, weight, TextAlign.Center)
        LedgerCell(row.home, weight, TextAlign.Center)
        LedgerCell(row.work, weight, TextAlign.Center)
        LedgerCell(row.drive, weight, TextAlign.Center)
        LedgerCell(row.onSite, weight, TextAlign.Center)
        LedgerCell(row.miles, weight, TextAlign.Center)
    }
}

@Composable
private fun LedgerRow(background: Color, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(vertical = TimeTrackerSpacing.XSmall),
        horizontalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.XSmall),
    ) {
        content()
    }
}

@Composable
private fun RowScope.LedgerCell(text: String, fontWeight: FontWeight, textAlign: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        modifier = Modifier.weight(1f),
        color = TimeTrackerColors.TextPrimary,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
    )
}
