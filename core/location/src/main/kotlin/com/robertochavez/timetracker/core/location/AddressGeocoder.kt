package com.robertochavez.timetracker.core.location

import android.content.Context
import android.location.Geocoder
import com.robertochavez.timetracker.core.logging.AppLogger
import com.robertochavez.timetracker.core.logging.LogCategory
import com.robertochavez.timetracker.core.logging.info
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class GeocodedAddress(val latitude: Double, val longitude: Double, val displayAddress: String)

interface AddressGeocoder {
    suspend fun findAddress(query: String): GeocodedAddress?
}

@Singleton
class AndroidAddressGeocoder @Inject constructor(@ApplicationContext private val context: Context, private val logger: AppLogger) :
    AddressGeocoder {
    override suspend fun findAddress(query: String): GeocodedAddress? {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotBlank()) { "Enter an address to look up." }

        return withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            val result = Geocoder(context, Locale.getDefault())
                .getFromLocationName(normalizedQuery, ADDRESS_RESULT_LIMIT)
                ?.firstOrNull()
            result?.let { address ->
                val displayAddress = buildList {
                    repeat(address.maxAddressLineIndex + 1) { index ->
                        address.getAddressLine(index)?.takeIf(String::isNotBlank)?.let(::add)
                    }
                }.firstOrNull() ?: normalizedQuery
                logger.info(LogCategory.LOCATION, "Work address geocoded")
                GeocodedAddress(
                    latitude = address.latitude,
                    longitude = address.longitude,
                    displayAddress = displayAddress,
                )
            }
        }
    }

    private companion object {
        const val ADDRESS_RESULT_LIMIT = 1
    }
}
