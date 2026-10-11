package com.robertochavez.timetracker.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton

@Composable
internal fun HubFallback(state: SettingsUiState, viewModel: SettingsViewModel) {
    if (!state.hubFallbackAllowed) {
        SettingsNote("Notion fallback is available only after OneDrive verification fails.")
        return
    }
    var dailyLog by rememberSaveable(state.hubDailyLogId) { mutableStateOf(state.hubDailyLogId) }
    var projects by rememberSaveable(state.hubProjectsId) { mutableStateOf(state.hubProjectsId) }
    var records by rememberSaveable(state.hubTimeRecordsId) { mutableStateOf(state.hubTimeRecordsId) }
    SettingsNote("Enter connection credentials to use the fallback. The token is encrypted on this phone.")
    FallbackField(viewModel.notionToken, { viewModel.notionToken = it }, "Notion integration token", secret = true)
    FallbackField(dailyLog, { dailyLog = it }, "Daily Log data source ID")
    FallbackField(projects, { projects = it }, "Projects data source ID")
    FallbackField(records, { records = it }, "Time Records data source ID")
    TimeTrackerPrimaryButton(
        "Save and use fallback",
        { viewModel.saveNotionFallback(viewModel.notionToken, dailyLog, projects, records) },
        enabled = listOf(viewModel.notionToken, dailyLog, projects, records).all { it.isNotBlank() },
    )
}

@Composable
private fun FallbackField(value: String, onChange: (String) -> Unit, label: String, secret: Boolean = false) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
    )
}
