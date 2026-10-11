package com.robertochavez.timetracker.feature.home

import com.robertochavez.timetracker.core.common.model.GeofenceRadiusOptions
import com.robertochavez.timetracker.core.common.model.HomeLocation
import com.robertochavez.timetracker.core.common.model.WorkLocation
import kotlin.math.roundToInt

enum class LocationField {
    LABEL,
    PROJECT_NUMBER,
    ADDRESS,
    LATITUDE,
    LONGITUDE,
    RADIUS_METERS,
}

internal fun HomeLocation.radiusLabel(): String = GeofenceRadiusOptions.nearest(radiusMeters).label

internal fun WorkLocation.radiusLabel(): String {
    val miles = (radiusMeters / GeofenceRadiusOptions.ONE_MILE_METERS).roundToInt()
    return "$miles ${if (miles == 1) "mile" else "miles"}"
}
