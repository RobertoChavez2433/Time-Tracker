package com.robertochavez.timetracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.robertochavez.timetracker.core.common.model.WorkLocation
import com.robertochavez.timetracker.core.database.repository.RoomWorkLocationRepository
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
}
