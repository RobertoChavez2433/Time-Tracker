package com.robertochavez.timetracker.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.robertochavez.timetracker.core.common.model.GeofenceRadiusOptions
import com.robertochavez.timetracker.core.designsystem.TimeTrackerPrimaryButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerQuietButton
import com.robertochavez.timetracker.core.designsystem.TimeTrackerTestTags
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

@Composable
internal fun MapNetworkDisclosure(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        modifier = Modifier.testTag(TimeTrackerTestTags.WORK_MAP_DISCLOSURE_DIALOG),
        title = { Text("Load Google Maps?") },
        text = {
            Text(
                "The map sends device and network information to Google to load map tiles. " +
                    "Time Tracker does not send saved place details. Declining keeps manual address and coordinate entry available.",
            )
        },
        dismissButton = {
            TimeTrackerQuietButton(
                text = "Not now",
                onClick = onDecline,
                modifier = Modifier.testTag(TimeTrackerTestTags.WORK_MAP_DISCLOSURE_DECLINE_BUTTON),
            )
        },
        confirmButton = {
            TimeTrackerPrimaryButton(
                text = "Load Map",
                onClick = onAccept,
                modifier = Modifier.testTag(TimeTrackerTestTags.WORK_MAP_DISCLOSURE_ACCEPT_BUTTON),
            )
        },
    )
}

@Composable
internal fun WorksiteMapPicker(
    initialLatitude: Double?,
    initialLongitude: Double?,
    initialRadiusMeters: Float,
    referenceLocations: List<MapReferenceLocationUiModel>,
    onCancel: () -> Unit,
    onConfirm: (Double, Double, Float) -> Unit,
) = LocationMapPicker(
    pickerType = MapPickerType.WORK,
    initialLatitude = initialLatitude,
    initialLongitude = initialLongitude,
    initialRadiusMeters = initialRadiusMeters,
    referenceLocations = referenceLocations,
    onCancel = onCancel,
    onConfirm = onConfirm,
)

@Composable
internal fun HomeMapPicker(
    initialLatitude: Double?,
    initialLongitude: Double?,
    initialRadiusMeters: Float,
    referenceLocations: List<MapReferenceLocationUiModel>,
    onCancel: () -> Unit,
    onConfirm: (Double, Double, Float) -> Unit,
) = LocationMapPicker(
    pickerType = MapPickerType.HOME,
    initialLatitude = initialLatitude,
    initialLongitude = initialLongitude,
    initialRadiusMeters = initialRadiusMeters,
    referenceLocations = referenceLocations,
    onCancel = onCancel,
    onConfirm = onConfirm,
)

@Composable
@Suppress(
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
    "CognitiveComplexMethod",
    "ComplexCondition",
)
private fun LocationMapPicker(
    pickerType: MapPickerType,
    initialLatitude: Double?,
    initialLongitude: Double?,
    initialRadiusMeters: Float,
    referenceLocations: List<MapReferenceLocationUiModel>,
    onCancel: () -> Unit,
    onConfirm: (Double, Double, Float) -> Unit,
) {
    val initialPin = if (
        initialLatitude != null &&
        initialLongitude != null &&
        initialLatitude in -90.0..90.0 &&
        initialLongitude in -180.0..180.0
    ) {
        LatLng(initialLatitude, initialLongitude)
    } else {
        null
    }
    var draftPin by remember { mutableStateOf(initialPin) }
    var draftRadiusMeters by remember {
        mutableFloatStateOf(
            pickerType.normalizedRadius(initialRadiusMeters),
        )
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            initialPin ?: LatLng(39.8283, -98.5795),
            if (initialPin == null) 3.5f else 14f,
        )
    }
    val markerState = rememberMarkerState(position = initialPin ?: LatLng(39.8283, -98.5795))
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val mapsMarkerFactoryReady = remember(context) {
        runCatching {
            MapsInitializer.initialize(context)
        }.isSuccess
    }
    val referenceMarkers = remember(referenceLocations, density, mapsMarkerFactoryReady) {
        if (mapsMarkerFactoryReady) {
            referenceLocations.mapIndexed { index, location ->
                location to createLabeledReferenceMarker(
                    label = location.label,
                    density = density,
                    placement = location.markerLabelPlacement(index),
                )
            }
        } else {
            emptyList()
        }
    }
    LaunchedEffect(draftPin) {
        draftPin?.let { markerState.position = it }
    }
    LaunchedEffect(markerState) {
        snapshotFlow { markerState.position }.collectLatest {
            if (draftPin != null) draftPin = it
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag(pickerType.pickerTag),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.fillMaxSize()) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    onMapClick = { draftPin = it },
                ) {
                    draftPin?.let { position ->
                        Marker(
                            state = markerState,
                            title = pickerType.pinTitle,
                            draggable = true,
                        )
                        Circle(
                            center = position,
                            radius = draftRadiusMeters.toDouble(),
                            fillColor = Color(0x334E6B3A),
                            strokeColor = Color(0xFF4E6B3A),
                            strokeWidth = 3f,
                        )
                    }
                    referenceMarkers.forEach { (location, marker) ->
                        Marker(
                            state = rememberMarkerState(
                                key = location.id,
                                position = LatLng(location.latitude, location.longitude),
                            ),
                            title = location.label,
                            icon = marker.descriptor,
                            anchor = marker.anchor,
                            zIndex = 2f,
                            onClick = { true },
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(tonalElevation = 4.dp, shadowElevation = 4.dp) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(pickerType.instructions)
                            if (pickerType == MapPickerType.WORK) {
                                val draftMiles = draftRadiusMeters / GeofenceRadiusOptions.ONE_MILE_METERS
                                androidx.compose.material3.Slider(
                                    value = draftMiles,
                                    onValueChange = {
                                        draftRadiusMeters =
                                            it.roundToInt().coerceIn(1, 25) * GeofenceRadiusOptions.ONE_MILE_METERS
                                    },
                                    valueRange = 1f..25f,
                                    steps = 23,
                                    modifier = Modifier.testTag(TimeTrackerTestTags.WORK_RADIUS_FIELD),
                                )
                                Text("${draftMiles.roundToInt()} mile worksite radius")
                            } else {
                                RadiusSelector(
                                    title = "Home radius",
                                    selectedRadiusMeters = draftRadiusMeters.toString(),
                                    tagPrefix = "home_map",
                                    onRadiusSelected = { draftRadiusMeters = it },
                                )
                            }
                            Text(
                                pickerType.radiusHelp,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            ) {
                                TimeTrackerQuietButton(
                                    text = "Cancel",
                                    onClick = onCancel,
                                    modifier = Modifier.testTag(pickerType.cancelTag),
                                )
                                TimeTrackerPrimaryButton(
                                    text = "Use This Pin",
                                    onClick = {
                                        draftPin?.let {
                                            onConfirm(
                                                it.latitude,
                                                it.longitude,
                                                draftRadiusMeters,
                                            )
                                        }
                                    },
                                    enabled = draftPin != null,
                                    modifier = Modifier.testTag(pickerType.confirmTag),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class MapPickerType(
    val pickerTag: String,
    val cancelTag: String,
    val confirmTag: String,
    val pinTitle: String,
    val instructions: String,
    val radiusHelp: String,
) {
    HOME(
        pickerTag = TimeTrackerTestTags.HOME_MAP_PICKER,
        cancelTag = TimeTrackerTestTags.HOME_MAP_CANCEL_BUTTON,
        confirmTag = TimeTrackerTestTags.HOME_MAP_CONFIRM_BUTTON,
        pinTitle = "Home",
        instructions = "Tap the map or drag the pin. Nothing is saved until you confirm here and then save Home.",
        radiusHelp = "Choose how close you need to be for automatic Home detection.",
    ),
    WORK(
        pickerTag = TimeTrackerTestTags.WORK_MAP_PICKER,
        cancelTag = TimeTrackerTestTags.WORK_MAP_CANCEL_BUTTON,
        confirmTag = TimeTrackerTestTags.WORK_MAP_CONFIRM_BUTTON,
        pinTitle = "Worksite",
        instructions = "Tap the map or drag the pin. Nothing is saved until you confirm here and then save the worksite.",
        radiusHelp = "Large radii can classify nearby travel as worksite time. " +
            "If the map is unavailable, cancel and use manual entry.",
    ),
    ;

    fun normalizedRadius(radiusMeters: Float): Float = when (this) {
        HOME -> GeofenceRadiusOptions.nearest(radiusMeters).meters
        WORK -> (
            (radiusMeters / GeofenceRadiusOptions.ONE_MILE_METERS)
                .roundToInt()
                .coerceIn(1, 25) *
                GeofenceRadiusOptions.ONE_MILE_METERS
            )
    }
}

private data class LabeledReferenceMarker(val descriptor: BitmapDescriptor, val anchor: Offset)

private fun MapReferenceLocationUiModel.markerLabelPlacement(index: Int): MarkerLabelPlacement = when (id) {
    MapReferenceLocationUiModel.CURRENT_LOCATION_ID -> MarkerLabelPlacement.RIGHT
    MapReferenceLocationUiModel.CURRENT_HOME_ID -> MarkerLabelPlacement.LEFT
    else -> if (index % 2 == 0) MarkerLabelPlacement.ABOVE else MarkerLabelPlacement.BELOW
}

private enum class MarkerLabelPlacement {
    LEFT,
    RIGHT,
    ABOVE,
    BELOW,
}

@Suppress("CyclomaticComplexMethod")
private fun createLabeledReferenceMarker(label: String, density: Float, placement: MarkerLabelPlacement): LabeledReferenceMarker {
    val textSize = 14f * density
    val dotRadius = 6f * density
    val outlineWidth = 2f * density
    val horizontalPadding = 8f * density
    val labelGap = 5f * density
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(30, 55, 75)
        this.textSize = textSize
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val outlinePaint = Paint(textPaint).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    val textWidth = textPaint.measureText(label)
    val textHeight = textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent
    val horizontalLabel = placement == MarkerLabelPlacement.LEFT || placement == MarkerLabelPlacement.RIGHT
    val width = if (horizontalLabel) {
        horizontalPadding + dotRadius * 2 + labelGap + textWidth + horizontalPadding
    } else {
        horizontalPadding + maxOf(dotRadius * 2, textWidth) + horizontalPadding
    }.roundToInt().coerceAtLeast(1)
    val height = if (horizontalLabel) {
        textSize * 1.7f
    } else {
        horizontalPadding + textHeight + labelGap + dotRadius * 2 + horizontalPadding
    }.roundToInt().coerceAtLeast(1)
    val dotCenterX = when (placement) {
        MarkerLabelPlacement.LEFT -> width - horizontalPadding - dotRadius
        MarkerLabelPlacement.RIGHT -> horizontalPadding + dotRadius
        MarkerLabelPlacement.ABOVE, MarkerLabelPlacement.BELOW -> width / 2f
    }
    val dotCenterY = when (placement) {
        MarkerLabelPlacement.LEFT, MarkerLabelPlacement.RIGHT -> height / 2f
        MarkerLabelPlacement.ABOVE -> height - horizontalPadding - dotRadius
        MarkerLabelPlacement.BELOW -> horizontalPadding + dotRadius
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(25, 118, 210)
        style = Paint.Style.FILL
    }
    val dotOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = outlineWidth
    }
    canvas.drawCircle(dotCenterX, dotCenterY, dotRadius, dotPaint)
    canvas.drawCircle(dotCenterX, dotCenterY, dotRadius, dotOutlinePaint)
    val textX = when (placement) {
        MarkerLabelPlacement.LEFT -> horizontalPadding
        MarkerLabelPlacement.RIGHT -> dotCenterX + dotRadius + labelGap
        MarkerLabelPlacement.ABOVE, MarkerLabelPlacement.BELOW -> (width - textWidth) / 2f
    }
    val baseline = when (placement) {
        MarkerLabelPlacement.LEFT, MarkerLabelPlacement.RIGHT ->
            dotCenterY - (textPaint.ascent() + textPaint.descent()) / 2f

        MarkerLabelPlacement.ABOVE -> horizontalPadding - textPaint.ascent()
        MarkerLabelPlacement.BELOW -> dotCenterY + dotRadius + labelGap - textPaint.ascent()
    }
    canvas.drawText(label, textX, baseline, outlinePaint)
    canvas.drawText(label, textX, baseline, textPaint)
    return LabeledReferenceMarker(
        descriptor = BitmapDescriptorFactory.fromBitmap(bitmap),
        anchor = Offset(dotCenterX / width, dotCenterY / height),
    )
}
