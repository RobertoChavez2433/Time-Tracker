package com.robertochavez.timetracker.core.designsystem

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val TimeTrackerColorScheme = darkColorScheme(
    primary = TimeTrackerColors.PrimaryOlive,
    onPrimary = TimeTrackerColors.TextInverse,
    primaryContainer = TimeTrackerColors.PrimaryOliveSoft,
    onPrimaryContainer = TimeTrackerColors.PrimaryOliveDark,
    secondary = TimeTrackerColors.AccentSage,
    onSecondary = TimeTrackerColors.TextInverse,
    secondaryContainer = TimeTrackerColors.SurfaceTint,
    onSecondaryContainer = TimeTrackerColors.TextPrimary,
    tertiary = TimeTrackerColors.AccentGold,
    onTertiary = TimeTrackerColors.TextPrimary,
    background = TimeTrackerColors.BackgroundBase,
    onBackground = TimeTrackerColors.TextPrimary,
    surface = TimeTrackerColors.SurfaceBase,
    onSurface = TimeTrackerColors.TextPrimary,
    surfaceVariant = TimeTrackerColors.SurfaceMuted,
    onSurfaceVariant = TimeTrackerColors.TextSecondary,
    outline = TimeTrackerColors.Border,
    outlineVariant = TimeTrackerColors.Divider,
    error = TimeTrackerColors.StatusError,
    onError = TimeTrackerColors.TextInverse,
    errorContainer = TimeTrackerColors.DestructiveContainer,
    onErrorContainer = TimeTrackerColors.Destructive,
)

private val TimeTrackerTypography = Typography(
    headlineMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.15.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.15.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.1.sp,
        lineHeight = 20.sp,
    ),
)

private val TimeTrackerShapes = Shapes(
    extraSmall = RoundedCornerShape(TimeTrackerRadius.Small),
    small = RoundedCornerShape(TimeTrackerRadius.Medium),
    medium = RoundedCornerShape(TimeTrackerRadius.Large),
    large = RoundedCornerShape(TimeTrackerRadius.Large),
    extraLarge = RoundedCornerShape(TimeTrackerRadius.Large),
)

@Composable
fun TimeTrackerTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            val window = activity?.window ?: return@SideEffect
            window.statusBarColor = TimeTrackerColors.BackgroundBase.toArgb()
            window.navigationBarColor = TimeTrackerColors.BackgroundBase.toArgb()
            setLightSystemBars(view, enabled = false)
        }
    }

    MaterialTheme(
        colorScheme = TimeTrackerColorScheme,
        typography = TimeTrackerTypography,
        shapes = TimeTrackerShapes,
        content = content,
    )
}

@Suppress("DEPRECATION")
private fun setLightSystemBars(view: View, enabled: Boolean) {
    val window = (view.context as? Activity)?.window ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (enabled) flags else 0, flags)
    } else {
        val lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.systemUiVisibility = if (enabled) {
            window.decorView.systemUiVisibility or lightFlags
        } else {
            window.decorView.systemUiVisibility and lightFlags.inv()
        }
    }
}
