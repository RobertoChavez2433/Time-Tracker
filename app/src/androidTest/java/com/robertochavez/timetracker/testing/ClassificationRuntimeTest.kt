package com.robertochavez.timetracker.testing

import android.location.Location
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.robertochavez.timetracker.core.common.domain.MotionActivity
import com.robertochavez.timetracker.core.common.domain.MotionTransition
import com.robertochavez.timetracker.core.common.model.ActivityBucket
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Manually invoked on the S21 preview only; never feeds device-wide mock providers. */
@RunWith(AndroidJUnit4::class)
class ClassificationRuntimeTest {
    @Test fun productionTrackerCombinesGpsAndActivityWithoutReview() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.robertochavez.timetracker.uiqa") { "Only the isolated preview may run this scenario." }
        check(Build.MODEL == "SM-G996U") { "This evidence scenario requires the S21." }
        val services = EntryPointAccessors.fromApplication(context, ClassificationTestEntryPoint::class.java)
        val tracking = services.tracking()
        val database = services.database()
        val classifier = services.classification()
        val mileage = services.mileage()
        assertNull(tracking.observeActiveSession().first())
        val originalIds = database.awaySessionDao().observeSessions().first().map { it.id }.toSet()
        val session = tracking.startManualSession(Instant.now())
        // Logical future timestamps keep unrelated real sensor callbacks from changing the controlled sequence.
        val start = session.start.plusSeconds(60)
        try {
            mileage.clearBaseline()
            mileage.startTracking()
            var longitude = 0.0
            suspend fun sample(seconds: Long, deltaLongitude: Double = 0.0) {
                longitude += deltaLongitude
                mileage.recordLocation(
                    Location("gps").apply {
                        latitude = 0.0
                        this.longitude = longitude
                        accuracy = 1f
                        time = start.plusSeconds(seconds).toEpochMilli()
                    },
                )
            }
            fun motion(activity: MotionActivity, transition: MotionTransition, seconds: Long) =
                com.robertochavez.timetracker.core.common.domain.ClassificationEvent.Motion(
                    activity,
                    transition,
                    start.plusSeconds(seconds),
                )
            suspend fun assertBucket(expected: ActivityBucket) {
                assertEquals(expected, database.activityIntervalDao().getOpenInterval(session.id)?.bucket)
            }

            sample(0)
            sample(30, 0.003)
            assertBucket(ActivityBucket.IDLE)
            sample(60, 0.003)
            assertBucket(ActivityBucket.DRIVE)
            assertEquals(start.toEpochMilli(), database.activityIntervalDao().getOpenInterval(session.id)?.startEpochMillis)
            val exitStill = motion(MotionActivity.STILL, MotionTransition.EXIT, 61)
            classifier.motion(exitStill.activity, exitStill.transition, exitStill.at)
            assertBucket(ActivityBucket.DRIVE)
            classifier.motion(MotionActivity.IN_VEHICLE, MotionTransition.ENTER, start.plusSeconds(62))
            sample(90, 0.0005)
            assertBucket(ActivityBucket.DRIVE)
            classifier.motion(MotionActivity.IN_VEHICLE, MotionTransition.EXIT, start.plusSeconds(91))
            assertBucket(ActivityBucket.IDLE)
            classifier.motion(MotionActivity.WALKING, MotionTransition.ENTER, start.plusSeconds(92))
            sample(120, 0.003)
            assertBucket(ActivityBucket.IDLE)
            sample(150, 0.003)
            sample(180, 0.003)
            assertBucket(ActivityBucket.DRIVE)
            classifier.motion(MotionActivity.RUNNING, MotionTransition.ENTER, start.plusSeconds(181))
            assertBucket(ActivityBucket.IDLE)
            val intervals = database.activityIntervalDao().observeIntervalsForSession(session.id).first()
            check(intervals.none { it.bucket == ActivityBucket.NEEDS_REVIEW })
            check(intervals.zipWithNext().all { (first, second) -> first.endEpochMillis == second.startEpochMillis })
        } finally {
            mileage.stopTracking()
            database.openHelper.writableDatabase.execSQL("DELETE FROM away_sessions WHERE id = ?", arrayOf(session.id))
        }
        assertEquals(originalIds, database.awaySessionDao().observeSessions().first().map { it.id }.toSet())
    }
}
