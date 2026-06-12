package com.robertochavez.timetracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.database.entity.WorkScheduleEntity
import com.robertochavez.timetracker.core.database.repository.RoomTrackingRepository
import com.robertochavez.timetracker.core.logging.NoopAppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class TrackingRepositoryTest {
    private lateinit var database: TimeTrackerDatabase
    private lateinit var repository: RoomTrackingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TimeTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomTrackingRepository(
            database = database,
            awaySessionDao = database.awaySessionDao(),
            activityIntervalDao = database.activityIntervalDao(),
            workScheduleDao = database.workScheduleDao(),
            clock = Clock.fixed(Instant.parse("2026-05-04T12:00:00Z"), ZoneId.of("America/New_York")),
            logger = NoopAppLogger(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `starts only on trackable days`() = runTest {
        database.workScheduleDao().upsertAll(WorkScheduleEntity.fromSchedule(WorkSchedule(setOf(DayOfWeek.MONDAY))))

        val monday = repository.startAwaySessionIfTrackable(Instant.parse("2026-05-04T12:00:00Z"))
        val sunday = repository.startAwaySessionIfTrackable(Instant.parse("2026-05-03T12:00:00Z"))

        assertNotNull(monday)
        assertNull(sunday)
    }

    @Test
    fun `stops active away session`() = runTest {
        val started = repository.startManualSession(Instant.parse("2026-05-04T12:00:00Z"))

        val stopped = repository.stopActiveAwaySession(Instant.parse("2026-05-04T14:00:00Z"))

        assertEquals(started.id, stopped?.id)
        assertEquals(Instant.parse("2026-05-04T14:00:00Z"), stopped?.end)
    }

    @Test
    fun `adds driven distance to active away session miles without storing speed history`() = runTest {
        val started = repository.startManualSession(Instant.parse("2026-05-04T12:00:00Z"))

        val updated = repository.addDrivenDistanceMeters(1_609.344)

        assertEquals(started.id, updated?.id)
        assertEquals(1.0, updated?.drivenMiles ?: 0.0, 0.001)
    }

    @Test
    fun `observes open activity intervals using current clock as temporary end`() = runTest {
        val started = repository.startManualSession(Instant.parse("2026-05-04T11:30:00Z"))
        repository.recordActivityTransition(ActivityBucket.DRIVE, Instant.parse("2026-05-04T11:45:00Z"))

        val interval = repository.observeActivityIntervals().first().single()

        assertEquals(started.id, interval.sessionId)
        assertEquals(ActivityBucket.DRIVE, interval.bucket)
        assertEquals(Instant.parse("2026-05-04T12:00:00Z"), interval.end)
    }

    @Test
    fun `ignores future open activity intervals until clock catches up`() = runTest {
        repository.startManualSession(Instant.parse("2026-05-04T11:30:00Z"))
        repository.recordActivityTransition(ActivityBucket.DRIVE, Instant.parse("2026-05-04T12:05:00Z"))

        val intervals = repository.observeActivityIntervals().first()

        assertEquals(0, intervals.size)
    }
}
