package com.robertochavez.timetracker.feature.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerColors
import com.robertochavez.timetracker.core.designsystem.TimeTrackerMutedText
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPanel
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreen
import com.robertochavez.timetracker.core.designsystem.TimeTrackerScreenTitle
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSecondaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerSpacing
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ReportsRoute(modifier: Modifier = Modifier, onEditTrackedDays: () -> Unit = {}, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = viewModel::refreshDashboard,
    )

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshForResume()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullRefresh(pullRefreshState),
    ) {
        TimeTrackerScreen(testTag = TimeTrackerTestTags.DASHBOARD_SCREEN) {
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
                WeeklyLedgerPanel(
                    ledger = state.weeklyLedger,
                    canNavigateToNextWeek = state.canNavigateToNextWeek,
                    onPreviousWeek = viewModel::previousWeek,
                    onNextWeek = viewModel::nextWeek,
                )
            }
            item {
                TimeTrackerSecondaryButton(
                    text = "Edit tracked days",
                    onClick = onEditTrackedDays,
                    modifier = Modifier.testTag(TimeTrackerTestTags.DASHBOARD_EDIT_WORKDAYS_BUTTON),
                )
            }
            item {
                HistoryPanel(
                    state = state,
                    onRangeSelected = viewModel::selectHistoryRange,
                    onCustomStartChanged = viewModel::updateCustomStartDate,
                    onCustomEndChanged = viewModel::updateCustomEndDate,
                    onSiteFilterChanged = viewModel::updateSiteFilter,
                )
            }
        }
        PullRefreshIndicator(
            refreshing = state.isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun HistoryPanel(
    state: ReportsUiState,
    onRangeSelected: (HistoryRangeMode) -> Unit,
    onCustomStartChanged: (String) -> Unit,
    onCustomEndChanged: (String) -> Unit,
    onSiteFilterChanged: (String) -> Unit,
) {
    TimeTrackerPanel {
        Text("History", color = TimeTrackerColors.PrimaryOliveDark, style = MaterialTheme.typography.titleMedium)
        TimeTrackerMutedText(state.historyTitle)
        HistoryRangeSelector(
            selectedMode = state.historyRangeMode,
            onRangeSelected = onRangeSelected,
        )
        if (state.historyRangeMode == HistoryRangeMode.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.Small)) {
                OutlinedTextField(
                    value = state.customStartDate,
                    onValueChange = onCustomStartChanged,
                    label = { Text("Start") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.customEndDate,
                    onValueChange = onCustomEndChanged,
                    label = { Text("End") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.historyMessage.isNotBlank()) {
                TimeTrackerMutedText(state.historyMessage)
            }
        }
        SummaryColumns(listOf(state.historySummary))
        HistoryCalendar(state.historyCalendar)
        OutlinedTextField(
            value = state.siteFilter,
            onValueChange = onSiteFilterChanged,
            label = { Text("Filter sites") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SiteHistoryList(state.siteHistory)
    }
}

@Composable
private fun HistoryRangeSelector(selectedMode: HistoryRangeMode, onRangeSelected: (HistoryRangeMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.Small)) {
        HistoryRangeMode.entries.chunked(RANGE_BUTTONS_PER_ROW).forEach { modes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.Small),
            ) {
                modes.forEach { mode ->
                    HistoryRangeButton(
                        mode = mode,
                        isSelected = selectedMode == mode,
                        onRangeSelected = onRangeSelected,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (modes.size < RANGE_BUTTONS_PER_ROW) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HistoryRangeButton(
    mode: HistoryRangeMode,
    isSelected: Boolean,
    onRangeSelected: (HistoryRangeMode) -> Unit,
    modifier: Modifier,
) {
    if (isSelected) {
        TimeTrackerPrimaryButton(mode.label, { onRangeSelected(mode) }, modifier = modifier)
    } else {
        TimeTrackerSecondaryButton(mode.label, { onRangeSelected(mode) }, modifier = modifier)
    }
}

@Composable
private fun HistoryCalendar(calendar: HistoryCalendarUiModel) {
    Column(verticalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.Small)) {
        Text("Calendar", color = TimeTrackerColors.PrimaryOliveDark, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.XSmall),
        ) {
            calendar.weekdayHeaders.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    color = TimeTrackerColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        calendar.weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.XSmall),
            ) {
                week.days.forEach { day ->
                    CalendarDayCell(day = day, modifier = Modifier.weight(1f))
                }
            }
        }
        DailyCalendarRows(calendar.dailyRows)
    }
}

@Composable
private fun CalendarDayCell(day: HistoryCalendarDayUiModel, modifier: Modifier = Modifier) {
    val backgroundColor = when {
        !day.isInSelectedRange -> Color.Transparent
        day.isToday -> TimeTrackerColors.SurfaceTint
        day.hasData -> TimeTrackerColors.SurfaceRaised
        else -> TimeTrackerColors.SurfaceMuted
    }
    val borderColor = if (day.isToday) TimeTrackerColors.PrimaryOlive else TimeTrackerColors.Divider
    val textColor = if (day.isInSelectedRange) TimeTrackerColors.TextPrimary else TimeTrackerColors.TextTertiary
    Column(
        modifier = modifier
            .heightIn(min = CALENDAR_DAY_MIN_HEIGHT)
            .clip(CALENDAR_DAY_SHAPE)
            .background(backgroundColor)
            .border(BorderStroke(TimeTrackerSpacing.XSmall / 4, borderColor), CALENDAR_DAY_SHAPE)
            .padding(TimeTrackerSpacing.XSmall),
        verticalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.XSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = day.dayNumber,
            color = textColor,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        if (day.hasData) {
            CalendarCellMetric(day.work)
            CalendarCellMetric("${day.miles}mi")
        }
    }
}

@Composable
private fun CalendarCellMetric(text: String) {
    Text(
        text = text,
        color = TimeTrackerColors.TextSecondary,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

@Composable
private fun DailyCalendarRows(days: List<HistoryCalendarDayUiModel>) {
    Column(verticalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.XSmall)) {
        Text("Days with saved totals", color = TimeTrackerColors.PrimaryOliveDark, style = MaterialTheme.typography.titleSmall)
        if (days.isEmpty()) {
            TimeTrackerMutedText("No saved daily totals in this range.")
            return@Column
        }
        days.forEach { day ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TimeTrackerColors.BackgroundSubtle, CALENDAR_DAY_SHAPE)
                    .padding(TimeTrackerSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.XSmall),
            ) {
                Text(day.dayLabel, color = TimeTrackerColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                TimeTrackerMutedText("Home ${day.home} | Work ${day.work} | Drive ${day.drive} | Miles ${day.miles}")
            }
        }
    }
}

@Composable
private fun SiteHistoryList(siteHistory: List<SiteHistoryGroupUiModel>) {
    if (siteHistory.isEmpty()) {
        TimeTrackerMutedText("No site visits in this range.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.Medium)) {
        siteHistory.forEach { site ->
            Column(verticalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.XSmall)) {
                Text(site.label, color = TimeTrackerColors.PrimaryOliveDark, style = MaterialTheme.typography.titleSmall)
                TimeTrackerMutedText("Total ${site.total} | Visits ${site.visitCount} | Last ${site.lastVisit}")
                TimeTrackerMutedText("Drive ${site.drive} | Miles ${site.miles}")
                site.dailyRows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.Small)) {
                        Text(row.date, modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall)
                        Text(
                            row.total,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End,
                        )
                        Text(
                            "${row.visits} visits",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            HorizontalDivider(color = TimeTrackerColors.Divider)
        }
    }
}

@Composable
private fun DashboardSummaryPanel(summaries: List<DashboardSummaryUiModel>) {
    TimeTrackerPanel(modifier = Modifier.testTag(TimeTrackerTestTags.DASHBOARD_SUMMARY_PANEL)) {
        SummaryColumns(summaries)
    }
}

@Composable
private fun SummaryColumns(summaries: List<DashboardSummaryUiModel>) {
    Row(horizontalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.Medium)) {
        summaries.forEach { summary ->
            SummaryColumn(summary = summary, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryColumn(summary: DashboardSummaryUiModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.XSmall)) {
        Text(summary.title, color = TimeTrackerColors.PrimaryOliveDark, style = MaterialTheme.typography.titleSmall)
        TimeTrackerMutedText("Home ${summary.home}")
        TimeTrackerMutedText("Work ${summary.work}")
        TimeTrackerMutedText("Drive ${summary.drive}")
        TimeTrackerMutedText("Needs review ${summary.needsReview}")
        TimeTrackerMutedText("Miles ${summary.miles}")
    }
}

@Composable
private fun WeeklyLedgerPanel(
    ledger: WeeklyLedgerUiModel,
    canNavigateToNextWeek: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    TimeTrackerPanel(modifier = Modifier.testTag(TimeTrackerTestTags.DASHBOARD_WEEKLY_LEDGER)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onPreviousWeek,
                modifier = Modifier.testTag(TimeTrackerTestTags.DASHBOARD_PREVIOUS_WEEK_BUTTON),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous week")
            }
            Text(
                ledger.title,
                color = TimeTrackerColors.PrimaryOliveDark,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            IconButton(
                onClick = onNextWeek,
                enabled = canNavigateToNextWeek,
                modifier = Modifier.testTag(TimeTrackerTestTags.DASHBOARD_NEXT_WEEK_BUTTON),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next week")
            }
        }
        if (ledger.title != ledger.dateRange) {
            TimeTrackerMutedText(ledger.dateRange)
        }
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
        LedgerCell("Idle", FontWeight.SemiBold, TextAlign.Center)
        LedgerCell("Review", FontWeight.SemiBold, TextAlign.Center)
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
        LedgerCell(row.site, weight, TextAlign.Center)
        LedgerCell(row.idle, weight, TextAlign.Center)
        LedgerCell(row.needsReview, weight, TextAlign.Center)
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

private const val RANGE_BUTTONS_PER_ROW = 2

private val CALENDAR_DAY_MIN_HEIGHT = 58.dp

private val CALENDAR_DAY_SHAPE = RoundedCornerShape(TimeTrackerSpacing.XSmall)
