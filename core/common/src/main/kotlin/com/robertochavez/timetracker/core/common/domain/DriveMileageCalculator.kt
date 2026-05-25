package com.robertochavez.timetracker.core.common.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class DriveMileagePoint(val latitude: Double, val longitude: Double, val accuracyMeters: Float?, val at: Instant)

data class DriveMovementSegment(val start: Instant, val end: Instant, val distanceMeters: Double, val speedMilesPerHour: Double) {
    val duration: Duration = Duration.between(start, end)
    val isVehicleLike: Boolean = speedMilesPerHour >= DriveMileageCalculator.INFERRED_DRIVE_MILES_PER_HOUR
}

data class DriveMileageCalculation(
    val baseline: DriveMileagePoint?,
    val distanceMeters: Double,
    val movementSegment: DriveMovementSegment? = null,
)

object DriveMileageCalculator {
    fun calculate(previous: DriveMileagePoint?, current: DriveMileagePoint): DriveMileageCalculation = when {
        !current.hasUsableAccuracy() -> DriveMileageCalculation(previous, distanceMeters = 0.0)
        previous == null || !previous.hasUsableAccuracy() -> DriveMileageCalculation(current, distanceMeters = 0.0)
        else -> previous.segmentTo(current)
    }

    private fun DriveMileagePoint.segmentTo(current: DriveMileagePoint): DriveMileageCalculation {
        val elapsedSeconds = Duration.between(at, current.at).seconds
        val distanceMeters = distanceTo(current)

        return when {
            elapsedSeconds <= 0 -> DriveMileageCalculation(this, distanceMeters = 0.0)
            distanceMeters < MIN_SEGMENT_METERS -> DriveMileageCalculation(this, distanceMeters = 0.0)
            distanceMeters / elapsedSeconds > MAX_REASONABLE_SPEED_METERS_PER_SECOND ->
                DriveMileageCalculation(current, distanceMeters = 0.0)
            else -> {
                val segment = DriveMovementSegment(
                    start = at,
                    end = current.at,
                    distanceMeters = distanceMeters,
                    speedMilesPerHour = distanceMeters / elapsedSeconds * METERS_PER_SECOND_TO_MILES_PER_HOUR,
                )
                DriveMileageCalculation(
                    baseline = current,
                    distanceMeters = if (segment.isVehicleLike) distanceMeters else 0.0,
                    movementSegment = segment,
                )
            }
        }
    }

    private fun DriveMileagePoint.hasUsableAccuracy(): Boolean = accuracyMeters == null || accuracyMeters <= MAX_ACCURACY_METERS

    private fun DriveMileagePoint.distanceTo(other: DriveMileagePoint): Double {
        val latitudeDelta = Math.toRadians(other.latitude - latitude)
        val longitudeDelta = Math.toRadians(other.longitude - longitude)
        val startLatitude = Math.toRadians(latitude)
        val endLatitude = Math.toRadians(other.latitude)
        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(startLatitude) * cos(endLatitude) * sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MAX_ACCURACY_METERS = 100f
    private const val MIN_SEGMENT_METERS = 15.0
    private const val MAX_REASONABLE_SPEED_METERS_PER_SECOND = 70.0
    private const val METERS_PER_SECOND_TO_MILES_PER_HOUR = 2.2369362920544
    const val INFERRED_DRIVE_MILES_PER_HOUR = 15.0
}
