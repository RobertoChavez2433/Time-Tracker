package com.robertochavez.timetracker.core.location.activity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.robertochavez.timetracker.core.common.domain.DriveMovementSegment
import com.robertochavez.timetracker.core.common.domain.MotionActivity
import com.robertochavez.timetracker.core.common.domain.MotionTransition
import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.AwaySession
import com.robertochavez.timetracker.core.logging.NoopAppLogger
import com.robertochavez.timetracker.core.testing.FakeTrackingRepository
import com.robertochavez.timetracker.core.testing.FakeWorkPresenceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ActivityClassificationCoordinatorTest {
    private val start = Instant.parse("2026-08-24T12:00:00Z")
    private val session = AwaySession("session", start, null)
    private val tracking = FakeTrackingRepository(listOf(session))
    private val work = FakeWorkPresenceRepository()
    private lateinit var context: Context

    @Before fun clearState() {
        context = ApplicationProvider.getApplicationContext()
        ClassificationStateStore(context).clear()
    }

    @Test fun `vehicle evidence survives coordinator and preference store recreation`() = runTest {
        coordinator().motion(MotionActivity.IN_VEHICLE, MotionTransition.ENTER, start)
        val recreated = coordinator()
        val still = recreated.motion(MotionActivity.STILL, MotionTransition.EXIT, start.plusSeconds(20))
        assertEquals(ActivityBucket.DRIVE, still?.bucket)
        assertEquals(ActivityBucket.DRIVE, recreated.position(segment(20, 80, 5.0), false, start.plusSeconds(80))?.bucket)
    }

    @Test fun `GPS candidate survives restart and later non-driving evidence resets it`() = runTest {
        assertEquals(ActivityBucket.IDLE, coordinator().position(segment(0, 30, 20.0), false, start.plusSeconds(30))?.bucket)
        val recreated = coordinator()
        assertEquals(ActivityBucket.DRIVE, recreated.position(segment(30, 60, 20.0), false, start.plusSeconds(60))?.bucket)
        assertEquals(ActivityBucket.IDLE, recreated.motion(MotionActivity.WALKING, MotionTransition.ENTER, start.plusSeconds(70))?.bucket)
        assertEquals(ActivityBucket.DRIVE, recreated.position(segment(70, 130, 20.0), false, start.plusSeconds(130))?.bucket)
    }

    @Test fun `new session and restore reset cannot reuse old vehicle state`() = runTest {
        val coordinator = coordinator()
        coordinator.motion(MotionActivity.IN_VEHICLE, MotionTransition.ENTER, start)
        tracking.sessions.value = listOf(AwaySession("new-session", start.plusSeconds(100), null))
        assertEquals(ActivityBucket.IDLE, coordinator.motion(MotionActivity.STILL, MotionTransition.EXIT, start.plusSeconds(101))?.bucket)
        coordinator.motion(MotionActivity.IN_VEHICLE, MotionTransition.ENTER, start.plusSeconds(102))
        coordinator.reset()
        assertEquals(ActivityBucket.IDLE, coordinator().motion(MotionActivity.STILL, MotionTransition.EXIT, start.plusSeconds(103))?.bucket)
    }

    @Test fun `no active session clears transient state and delayed events are ignored`() = runTest {
        val coordinator = coordinator()
        assertNull(coordinator.motion(MotionActivity.IN_VEHICLE, MotionTransition.ENTER, start.minusSeconds(1)))
        coordinator.motion(MotionActivity.IN_VEHICLE, MotionTransition.ENTER, start.plusSeconds(60))
        assertNull(coordinator.motion(MotionActivity.STILL, MotionTransition.ENTER, start.plusSeconds(30)))
        assertEquals(1, tracking.activityTransitions.size)
        tracking.sessions.value = emptyList()
        assertNull(coordinator.position(null, true, start.plusSeconds(120)))
        assertNull(ClassificationStateStore(context).read(session))
    }

    private fun coordinator() = ActivityClassificationCoordinator(tracking, work, ClassificationStateStore(context), NoopAppLogger())

    private fun segment(from: Long, to: Long, speed: Double) =
        DriveMovementSegment(start.plusSeconds(from), start.plusSeconds(to), 100.0, speed)
}
