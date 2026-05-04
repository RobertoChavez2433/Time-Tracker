package com.robertochavez.timetracker.core.common.model

data class GeofenceRadiusOption(val label: String, val meters: Float, val testTagSuffix: String)

object GeofenceRadiusOptions {
    const val FIFTY_FEET_METERS = 15.24f
    const val FIVE_MILES_METERS = 8046.72f

    val all: List<GeofenceRadiusOption> = listOf(
        GeofenceRadiusOption("50 ft", FIFTY_FEET_METERS, "50_ft"),
        GeofenceRadiusOption("1/4 mi", 402.336f, "quarter_mile"),
        GeofenceRadiusOption("1/2 mi", 804.672f, "half_mile"),
        GeofenceRadiusOption("3/4 mi", 1207.008f, "three_quarter_mile"),
        GeofenceRadiusOption("1 mi", 1609.344f, "one_mile"),
        GeofenceRadiusOption("2 mi", 3218.688f, "two_miles"),
        GeofenceRadiusOption("5 mi", FIVE_MILES_METERS, "five_miles"),
    )

    val default: GeofenceRadiusOption = all.first()

    fun nearest(meters: Float): GeofenceRadiusOption = all.minBy { option -> kotlin.math.abs(option.meters - meters) }
}
