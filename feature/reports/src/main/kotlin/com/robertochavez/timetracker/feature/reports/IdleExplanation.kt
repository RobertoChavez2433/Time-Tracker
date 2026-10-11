package com.robertochavez.timetracker.feature.reports

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
internal fun IdleExplanation() {
    var showing by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { showing = true }, modifier = Modifier.testTag("idle_definition")) {
        Text("What counts as Idle?")
    }
    if (showing) {
        AlertDialog(
            onDismissRequest = { showing = false },
            title = { Text("Idle") },
            text = { Text("Away time not identified as driving or at a saved worksite.") },
            confirmButton = { TextButton(onClick = { showing = false }) { Text("Got it") } },
        )
    }
}
