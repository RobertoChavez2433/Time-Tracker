package com.robertochavez.timetracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ClassificationStateResetter
import com.robertochavez.timetracker.core.database.repository.RoomTrackingRepository
import com.robertochavez.timetracker.core.logging.NoopAppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ActivityIntervalWriterTest {
    private lateinit var database: TimeTrackerDatabase
    private lateinit var tracking: RoomTrackingRepository
    private val start = Instant.parse("2026-08-24T12:00:00Z")
    private var resetCount = 0

    @Before fun prepare() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TimeTrackerDatabase::class.java)
            .allowMainThreadQueries().build()
        tracking = RoomTrackingRepository(
            database,
            database.awaySessionDao(),
            database.activityIntervalDao(),
            database.workScheduleDao(),
            Clock.fixed(start.plusSeconds(3600), ZoneOffset.UTC),
            NoopAppLogger(),
            ClassificationStateResetter { resetCount++ },
        )
    }

    @After fun close() = database.close()

    @Test fun `session end clears device evidence but a stale stop is ignored`() = runTest {
        tracking.startManualSession(start)
        tracking.stopActiveAwaySession(start.minusSeconds(1))
        assertEquals(0, resetCount)
        assertTrue(tracking.observeActiveSession().first() != null)
        tracking.stopActiveAwaySession(start.plusSeconds(60))
        assertEquals(1, resetCount)
        assertEquals(null, tracking.observeActiveSession().first())
    }

    @Test fun `session and initial idle interval are stored together and repeated idle is coalesced`() = runTest {
        val session = tracking.startAwaySessionIfTrackable(start)!!
        val initial = database.activityIntervalDao().getOpenInterval(session.id)!!
        assertEquals(ActivityBucket.IDLE, initial.bucket)
        assertEquals(session.start.toEpochMilli(), initial.startEpochMillis)
        assertTrue(tracking.recordActivityTransition(ActivityBucket.IDLE, start.plusSeconds(60)))
        assertEquals(listOf(initial), database.activityIntervalDao().observeIntervals().first())
    }

    @Test fun `late events and a previous session cannot overwrite current activity`() = runTest {
        val session = tracking.startManualSession(start)
        assertTrue(tracking.recordActivityTransition(ActivityBucket.DRIVE, start.plusSeconds(60), session.id, start.plusSeconds(60)))
        val before = database.activityIntervalDao().observeIntervals().first()
        assertFalse(tracking.recordActivityTransition(ActivityBucket.IDLE, start.plusSeconds(30), session.id, start.plusSeconds(30)))
        assertFalse(tracking.recordActivityTransition(ActivityBucket.IDLE, start.plusSeconds(90), "old-session", start.plusSeconds(90)))
        assertFalse(tracking.recordActivityTransition(ActivityBucket.IDLE, start.minusSeconds(1)))
        assertEquals(before, database.activityIntervalDao().observeIntervals().first())
    }

    @Test fun `GPS backdating changes only the open interval and preserves closed rows`() = runTest {
        val session = tracking.startManualSession(start)
        tracking.recordActivityTransition(ActivityBucket.SITE, start.plusSeconds(30))
        tracking.recordActivityTransition(ActivityBucket.IDLE, start.plusSeconds(60))
        val closed = database.activityIntervalDao().observeIntervals().first().filter { it.endEpochMillis != null }
        assertTrue(tracking.recordActivityTransition(ActivityBucket.DRIVE, start.plusSeconds(40), session.id, start.plusSeconds(120)))
        val rows = database.activityIntervalDao().observeIntervals().first()
        assertEquals(closed, rows.filter { it.endEpochMillis != null })
        assertEquals(start.plusSeconds(60).toEpochMilli(), rows.last().startEpochMillis)
        assertEquals(ActivityBucket.DRIVE, rows.last().bucket)
        assertTrue(rows.all { it.endEpochMillis == null || it.endEpochMillis!! > it.startEpochMillis })
    }

    @Test fun `new classifications reject the legacy review bucket`() = runTest {
        tracking.startManualSession(start)
        assertTrue(runCatching { tracking.recordActivityTransition(ActivityBucket.NEEDS_REVIEW, start) }.isFailure)
        assertEquals(ActivityBucket.IDLE, database.activityIntervalDao().observeIntervals().first().single().bucket)
    }
}
