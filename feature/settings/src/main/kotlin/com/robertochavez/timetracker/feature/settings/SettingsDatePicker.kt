package com.robertochavez.timetracker.feature.settings

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal fun readableSettingsDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
}.getOrDefault("Choose a date")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsDateRow(title: String, value: String, tag: String, onChange: (String) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    SettingsLink(title, readableSettingsDate(value), tag, { picking = true })
    if (picking) {
        val selected = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
        val picker = rememberDatePickerState(initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picker.selectedDateMillis?.let {
                            onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                        }
                        picking = false
                    },
                    enabled = picker.selectedDateMillis != null,
                    modifier = Modifier.testTag("settings_date_confirm"),
                ) { Text("Choose date") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = picker, title = { Text(title) })
        }
    }
}
