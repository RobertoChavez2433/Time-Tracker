package com.robertochavez.timetracker.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

object TimeTrackerSpacing {
    val XSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val XLarge = 20.dp
    val XXLarge = 24.dp
    val ScreenHorizontal = 18.dp
    val ScreenVertical = 18.dp
}

object TimeTrackerRadius {
    val Small = 4.dp
    val Medium = 6.dp
    val Large = 8.dp
}

object TimeTrackerElevation {
    val Resting = 0.dp
    val Raised = 1.dp
    val Dialog = 6.dp
}

object TimeTrackerMotion {
    const val QUICK_MS = 120
    const val STANDARD_MS = 220
    const val EMPHASIS_MS = 320
    val EaseOut = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val EaseInOut = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
}

object TimeTrackerDensity {
    val PanelSpacing = TimeTrackerSpacing.Medium
    val RowVerticalPadding = TimeTrackerSpacing.Medium
    val SectionSpacing = TimeTrackerSpacing.Large
}
