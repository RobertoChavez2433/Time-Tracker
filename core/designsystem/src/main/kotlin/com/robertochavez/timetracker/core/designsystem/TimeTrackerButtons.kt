package com.robertochavez.timetracker.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TimeTrackerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: TimeTrackerButtonVariant = TimeTrackerButtonVariant.Primary,
) {
    when (variant) {
        TimeTrackerButtonVariant.Primary -> PrimaryButton(text, onClick, modifier, enabled)
        TimeTrackerButtonVariant.Secondary -> SecondaryButton(text, onClick, modifier, enabled)
        TimeTrackerButtonVariant.Quiet -> QuietButton(text, onClick, modifier, enabled)
        TimeTrackerButtonVariant.Destructive -> DestructiveButton(text, onClick, modifier, enabled)
    }
}

@Composable
fun TimeTrackerPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TimeTrackerButton(text, onClick, modifier, enabled, TimeTrackerButtonVariant.Primary)
}

@Composable
fun TimeTrackerSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TimeTrackerButton(text, onClick, modifier, enabled, TimeTrackerButtonVariant.Secondary)
}

@Composable
fun TimeTrackerQuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TimeTrackerButton(text, onClick, modifier, enabled, TimeTrackerButtonVariant.Quiet)
}

@Composable
fun TimeTrackerDestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TimeTrackerButton(text, onClick, modifier, enabled, TimeTrackerButtonVariant.Destructive)
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = TimeTrackerColors.PrimaryOlive,
            contentColor = TimeTrackerColors.TextInverse,
            disabledContainerColor = TimeTrackerColors.SurfaceMuted,
            disabledContentColor = TimeTrackerColors.TextTertiary,
        ),
        contentPadding = PaddingValues(horizontal = TimeTrackerSpacing.XLarge, vertical = TimeTrackerSpacing.Medium),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(TimeTrackerSpacing.XSmall / 4, TimeTrackerColors.PrimaryOlive),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TimeTrackerColors.PrimaryOliveDark),
        contentPadding = PaddingValues(horizontal = TimeTrackerSpacing.XLarge, vertical = TimeTrackerSpacing.Medium),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.textButtonColors(contentColor = TimeTrackerColors.PrimaryOliveDark),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = TimeTrackerColors.Destructive,
            contentColor = TimeTrackerColors.TextInverse,
            disabledContainerColor = TimeTrackerColors.DestructiveContainer,
            disabledContentColor = TimeTrackerColors.TextTertiary,
        ),
        contentPadding = PaddingValues(horizontal = TimeTrackerSpacing.XLarge, vertical = TimeTrackerSpacing.Medium),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
