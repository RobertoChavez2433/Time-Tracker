package com.robertochavez.timetracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.database.repository.RoomWorkLocationRepository
import com.robertochavez.timetracker.core.database.repository.RoomWorkSiteSessionRepository
import com.robertochavez.timetracker.core.logging.NoopAppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class WorkLocationRepositoryTest {
    private lateinit var database: TimeTrackerDatabase
    private lateinit var repository: RoomWorkLocationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TimeTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomWorkLocationRepository(
            workLocationDao = database.workLocationDao(),
            logger = NoopAppLogger(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `persists work location`() = runTest {
        val location = WorkLocation(
            latitude = 35.0,
            longitude = -80.0,
            radiusMeters = WorkLocation.MAXIMUM_RADIUS_METERS,
            updatedAt = Instant.parse("2026-05-03T12:00:00Z"),
        )

        repository.setWorkLocation(location)

        assertEquals(location, repository.getWorkLocation())
        assertEquals(location, repository.observeWorkLocation().first())
    }

    @Test
    fun `persists multiple work locations`() = runTest {
        val first = WorkLocation(
            latitude = 35.0,
            longitude = -80.0,
            radiusMeters = WorkLocation.MAXIMUM_RADIUS_METERS,
            updatedAt = Instant.parse("2026-05-03T12:00:00Z"),
            id = "work-1",
            label = "Work site 1",
        )
        val second = WorkLocation(
            latitude = 36.0,
            longitude = -81.0,
            radiusMeters = WorkLocation.MINIMUM_RADIUS_METERS,
            updatedAt = Instant.parse("2026-05-04T12:00:00Z"),
            id = "work-2",
            label = "Work site 2",
        )

        repository.setWorkLocation(first)
        repository.setWorkLocation(second)

        assertEquals(second, repository.getWorkLocation())
        assertEquals(listOf(second, first), repository.getWorkLocations())
        assertEquals(listOf(second, first), repository.observeWorkLocations().first())
    }

    @Test
    fun `renames work location`() = runTest {
        val location = WorkLocation(
            latitude = 35.0,
            longitude = -80.0,
            radiusMeters = WorkLocation.MAXIMUM_RADIUS_METERS,
            updatedAt = Instant.parse("2026-05-03T12:00:00Z"),
            id = "site",
            label = "Original Site",
        )
        repository.setWorkLocation(location)

        val renamed = repository.renameWorkLocation("site", "Warehouse")

        assertEquals("Warehouse", renamed?.label)
        assertEquals("Warehouse", repository.getWorkLocation("site")?.label)
    }

    @Test
    fun `work site session keeps label snapshot after location rename`() = runTest {
        val site = WorkLocation(
            latitude = 35.0,
            longitude = -80.0,
            radiusMeters = WorkLocation.MAXIMUM_RADIUS_METERS,
            updatedAt = Instant.parse("2026-05-03T12:00:00Z"),
            id = "site",
            label = "Original Site",
        )
        val sessionRepository = RoomWorkSiteSessionRepository(
            database = database,
            workSiteSessionDao = database.workSiteSessionDao(),
            workLocationDao = database.workLocationDao(),
            logger = NoopAppLogger(),
        )
        repository.setWorkLocation(site)

        val session = sessionRepository.startWorkSiteSession("site", Instant.parse("2026-05-03T13:00:00Z"))
        repository.renameWorkLocation("site", "Renamed Site")
        val stored = sessionRepository.observeWorkSiteSessions().first().single()

        assertEquals("Original Site", session.workLocationLabelSnapshot)
        assertEquals("Original Site", stored.workLocationLabelSnapshot)
    }
}
