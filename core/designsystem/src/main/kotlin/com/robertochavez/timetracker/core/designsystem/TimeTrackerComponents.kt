package com.robertochavez.timetracker.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun TimeTrackerScreen(modifier: Modifier = Modifier, testTag: String? = null, content: LazyListScope.() -> Unit) {
    val taggedModifier = if (testTag == null) modifier else modifier.testTag(testTag)
    LazyColumn(
        modifier = taggedModifier
            .fillMaxSize()
            .background(TimeTrackerColors.BackgroundBase)
            .padding(horizontal = TimeTrackerSpacing.ScreenHorizontal, vertical = TimeTrackerSpacing.ScreenVertical),
        verticalArrangement = Arrangement.spacedBy(TimeTrackerDensity.PanelSpacing),
        contentPadding = PaddingValues(bottom = TimeTrackerSpacing.Large),
        content = content,
    )
}

@Composable
fun TimeTrackerPanel(
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = TimeTrackerColors.SurfaceBase,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(width = TimeTrackerSpacing.XSmall / 4, color = TimeTrackerColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = TimeTrackerElevation.Raised),
        content = {
            Column(
                modifier = Modifier.padding(TimeTrackerSpacing.Large),
                verticalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.Medium),
                content = content,
            )
        },
    )
}

@Composable
fun TimeTrackerCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    TimeTrackerPanel(modifier = modifier, content = content)
}

@Composable
fun TimeTrackerScreenTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.XSmall)) {
        Text(title, color = TimeTrackerColors.PrimaryOliveDark, style = MaterialTheme.typography.headlineMedium)
        if (!subtitle.isNullOrBlank()) {
            TimeTrackerMutedText(subtitle)
        }
    }
}

@Composable
fun TimeTrackerSectionTitle(text: String) {
    Text(text, color = TimeTrackerColors.PrimaryOliveDark, style = MaterialTheme.typography.titleLarge)
}

@Composable
fun TimeTrackerMutedText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = TimeTrackerColors.TextSecondary,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun TimeTrackerStatusText(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = TimeTrackerColors.StatusSuccess,
) {
    AnimatedVisibility(
        visible = text.isNotBlank(),
        enter = fadeIn(tween(TimeTrackerMotion.STANDARD_MS)) + slideInVertically { -it / 4 },
        exit = fadeOut(tween(TimeTrackerMotion.QUICK_MS)) + slideOutVertically { -it / 4 },
    ) {
        TimeTrackerPanel(modifier = modifier, containerColor = TimeTrackerColors.SurfaceTint) {
            Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun TimeTrackerMetricRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeTrackerMutedText(label)
        Text(value, color = TimeTrackerColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun TimeTrackerSettingSection(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    TimeTrackerPanel {
        Text(title, color = TimeTrackerColors.PrimaryOliveDark, style = MaterialTheme.typography.titleMedium)
        if (!subtitle.isNullOrBlank()) {
            TimeTrackerMutedText(subtitle)
        }
        HorizontalDivider(color = TimeTrackerColors.Divider)
        content()
    }
}

@Composable
fun TimeTrackerSettingRow(
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = TimeTrackerDensity.RowVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.Large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TimeTrackerSpacing.XSmall)) {
            Text(label, color = TimeTrackerColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
            if (!supportingText.isNullOrBlank()) {
                TimeTrackerMutedText(supportingText)
            }
        }
        trailing()
    }
}

enum class TimeTrackerButtonVariant {
    Primary,
    Secondary,
    Quiet,
    Destructive,
}
