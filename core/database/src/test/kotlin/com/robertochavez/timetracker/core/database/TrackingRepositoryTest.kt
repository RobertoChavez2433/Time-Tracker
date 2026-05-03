package com.robertochavez.timetracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.database.entity.WorkScheduleEntity
import com.robertochavez.timetracker.core.database.repository.RoomTrackingRepository
import com.robertochavez.timetracker.core.logging.NoopAppLogger
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
}
