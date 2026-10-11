package com.robertochavez.timetracker.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsScreen(
    title: String,
    onBack: (() -> Unit)?,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.imePadding().testTag("settings_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("settings_content"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface),
            content = content,
        )
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
internal fun SettingsLink(
    title: String,
    summary: String,
    tag: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    warning: Boolean = false,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).testTag(tag)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsRowText(title, summary, Modifier.weight(1f), warning)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
    }
}

@Composable
internal fun SettingsToggle(
    title: String,
    summary: String,
    checked: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).testTag(tag)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowText(title, summary, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun SettingsRowText(title: String, summary: String, modifier: Modifier = Modifier, warning: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (summary.isNotBlank()) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SettingsNote(text: String, warning: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
