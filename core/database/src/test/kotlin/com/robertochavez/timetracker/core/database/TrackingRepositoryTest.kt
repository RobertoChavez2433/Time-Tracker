package com.robertochavez.timetracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.database.entity.ActivityIntervalEntity
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
    fun `atomic correction rejects invalid fields without changing the stored row`() = runTest {
        val start = Instant.parse("2026-05-04T12:00:00Z")
        val session = repository.startManualSession(start)
        val invalid = runCatching { repository.saveCorrection(session.id, start.plusSeconds(60), start, 42.0, false) }
        assertEquals(true, invalid.isFailure)
        assertEquals(session, repository.getSession(session.id))
        val end = start.plusSeconds(3600)
        repository.saveCorrection(session.id, start, end, 42.0, false)
        val saved = repository.getSession(session.id)!!
        assertEquals(end, saved.end)
        assertEquals(42.0, saved.drivenMiles, 0.0)
        assertEquals(false, saved.countsTowardTotals)
        assertEquals(true, saved.manuallyAdjusted)
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

        val interval = repository.observeActivityIntervals().first().single { it.bucket == ActivityBucket.DRIVE }

        assertEquals(started.id, interval.sessionId)
        assertEquals(ActivityBucket.DRIVE, interval.bucket)
        assertEquals(Instant.parse("2026-05-04T12:00:00Z"), interval.end)
    }

    @Test
    fun `ignores future open activity intervals until clock catches up`() = runTest {
        repository.startManualSession(Instant.parse("2026-05-04T11:30:00Z"))
        repository.recordActivityTransition(ActivityBucket.DRIVE, Instant.parse("2026-05-04T12:05:00Z"))

        val intervals = repository.observeActivityIntervals().first()

        assertEquals(listOf(ActivityBucket.IDLE), intervals.map { it.bucket })
    }

    @Test
    fun `observes sessions overlapping requested range`() = runTest {
        val before = repository.startManualSession(Instant.parse("2026-05-03T10:00:00Z"))
        repository.updateSessionWindow(
            before.id,
            Instant.parse("2026-05-03T10:00:00Z"),
            Instant.parse("2026-05-03T12:00:00Z"),
        )
        val overlappingStart = repository.startManualSession(Instant.parse("2026-05-03T23:00:00Z"))
        repository.updateSessionWindow(
            overlappingStart.id,
            Instant.parse("2026-05-03T23:00:00Z"),
            Instant.parse("2026-05-04T01:00:00Z"),
        )
        val inside = repository.startManualSession(Instant.parse("2026-05-04T12:00:00Z"))
        repository.updateSessionWindow(
            inside.id,
            Instant.parse("2026-05-04T12:00:00Z"),
            Instant.parse("2026-05-04T13:00:00Z"),
        )
        val active = repository.startManualSession(Instant.parse("2026-05-04T20:00:00Z"))
        val after = repository.startManualSession(Instant.parse("2026-05-05T01:00:00Z"))
        repository.updateSessionWindow(
            after.id,
            Instant.parse("2026-05-05T01:00:00Z"),
            Instant.parse("2026-05-05T02:00:00Z"),
        )

        val sessions = repository.observeSessions(
            startInclusive = Instant.parse("2026-05-04T00:00:00Z"),
            endExclusive = Instant.parse("2026-05-05T00:00:00Z"),
        ).first()

        assertEquals(setOf(overlappingStart.id, inside.id, active.id), sessions.map { it.id }.toSet())
    }

    @Test
    fun `observes activity intervals overlapping requested range`() = runTest {
        val session = repository.startManualSession(Instant.parse("2026-05-03T00:00:00Z"))
        database.activityIntervalDao().deleteForSession(session.id)
        database.activityIntervalDao().upsert(
            ActivityIntervalEntity(
                id = "before",
                sessionId = session.id,
                bucket = ActivityBucket.DRIVE,
                startEpochMillis = Instant.parse("2026-05-03T10:00:00Z").toEpochMilli(),
                endEpochMillis = Instant.parse("2026-05-03T11:00:00Z").toEpochMilli(),
            ),
        )
        database.activityIntervalDao().upsert(
            ActivityIntervalEntity(
                id = "overlapping",
                sessionId = session.id,
                bucket = ActivityBucket.DRIVE,
                startEpochMillis = Instant.parse("2026-05-03T23:30:00Z").toEpochMilli(),
                endEpochMillis = Instant.parse("2026-05-04T00:30:00Z").toEpochMilli(),
            ),
        )
        database.activityIntervalDao().upsert(
            ActivityIntervalEntity(
                id = "inside",
                sessionId = session.id,
                bucket = ActivityBucket.IDLE,
                startEpochMillis = Instant.parse("2026-05-04T12:00:00Z").toEpochMilli(),
                endEpochMillis = Instant.parse("2026-05-04T13:00:00Z").toEpochMilli(),
            ),
        )

        val intervals = repository.observeActivityIntervals(
            startInclusive = Instant.parse("2026-05-04T00:00:00Z"),
            endExclusive = Instant.parse("2026-05-05T00:00:00Z"),
        ).first()

        assertEquals(setOf("overlapping", "inside"), intervals.map { it.id }.toSet())
    }
}
