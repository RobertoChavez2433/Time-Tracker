package com.robertochavez.timetracker.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robertochavez.timetracker.core.designsystem.TimeTrackerColors
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags

@Composable
internal fun WeeklyLedger(ledger: WeeklyLedgerUiModel, onDay: (String) -> Unit) {
    // Preserve the seven equal-width columns and keep the entire week visible.
    Column(Modifier.fillMaxWidth().testTag(TimeTrackerTestTags.DASHBOARD_WEEKLY_LEDGER)) {
        LedgerRow(listOf("Day", "Home", "Work", "Drive", "Site", "Idle", "Miles"), emphasized = true)
        ledger.rows.forEach { row ->
            LedgerRow(
                row.values(),
                modifier = Modifier.clickable { onDay(row.dateIso) }.testTag("ledger_day_${row.dateIso}"),
            )
            HorizontalDivider(color = TimeTrackerColors.Divider)
        }
        LedgerRow(ledger.total.values(), emphasized = true)
        IdleExplanation()
    }
}

private fun WeeklyLedgerRowUiModel.values() = listOf(day, home, work, drive, site, idle, miles)

@Composable
private fun LedgerRow(values: List<String>, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Row(
        modifier.fillMaxWidth().height(32.dp).background(
            if (emphasized) TimeTrackerColors.SurfaceTint else TimeTrackerColors.SurfaceBase,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        values.forEach { value ->
            BasicText(
                value,
                Modifier.weight(1f).padding(horizontal = 3.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TimeTrackerColors.TextPrimary,
                    fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    lineHeight = TextUnit.Unspecified,
                ),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 4.sp, maxFontSize = 12.sp, stepSize = 0.25.sp),
            )
        }
    }
}
