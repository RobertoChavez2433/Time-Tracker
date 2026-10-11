package com.robertochavez.timetracker.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.robertochavez.timetracker.core.common.domain.ReportCalculator
import com.robertochavez.timetracker.core.common.model.PayPeriodSettings
import com.robertochavez.timetracker.core.common.model.WorkSchedule
import com.robertochavez.timetracker.core.database.TimeTrackerDatabase
import com.robertochavez.timetracker.core.testing.FakeAppSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Opt-in replay: private phone backups never become checked-in test fixtures. */
@RunWith(RobolectricTestRunner::class)
class HistoricalBackupReplayTest {
    @Test fun `saved August week partitions exactly and retains every exported record`() = runBlocking {
        val path = System.getenv("TIMETRACKER_REPLAY_BACKUP")
        assumeTrue("Set TIMETRACKER_REPLAY_BACKUP to the saved August 30 plain backup", !path.isNullOrBlank())
        val backup = File(requireNotNull(path)).readBytes()
        val envelope = JSONObject(backup.toString(Charsets.UTF_8))
        val now = Instant.parse(envelope.getJSONObject("manifest").getString("exportedAt"))
        val zone = ZoneId.of("America/New_York")
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TimeTrackerDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val settings = FakeAppSettingsRepository()
            BackupImporter(database, BackupCipher(), settings).import(backup)
            val sessions = database.awaySessionDao().observeSessions().first().map { it.toModel() }
            val intervals = database.activityIntervalDao().observeIntervals().first().mapNotNull { it.toModelOrNull(now) }
            val visits = database.workSiteSessionDao().observeWorkSiteSessions().first().map { it.toModel() }
            val calculator = ReportCalculator(
                zone,
                WorkSchedule(DayOfWeek.entries.toSet()),
                PayPeriodSettings(LocalDate.of(2026, 8, 24)),
            )
            val week = calculator.weekly(LocalDate.of(2026, 8, 24), sessions, intervals, now, visits)
            assertEquals(222_767_618L, week.work.toMillis())
            assertEquals(91_068_441L, week.drive.toMillis())
            assertEquals(Duration.ZERO, week.site)
            assertEquals(131_699_177L, week.idle.toMillis())
            assertEquals(Duration.ZERO, week.needsReview)
            week.dailyReports.forEach { assertEquals(it.work, it.drive.plus(it.site).plus(it.idle)) }
            assertEquals(
                sessions.sumOf {
                    it.drivenMiles
                },
                database.awaySessionDao().observeSessions().first().sumOf { it.drivenMiles },
                0.0,
            )
            val after = JSONObject(BackupExporter(database, Clock.fixed(now, zone), settings).export().toString(Charsets.UTF_8))
            assertEquals(canonical(envelope.getJSONObject("tables")), canonical(after.getJSONObject("tables")))
            assertEquals(
                canonical(envelope.getJSONObject("appSettings")),
                canonical(after.getJSONObject("appSettings")),
            )
            println("Saved week verified: work=${week.work}, drive=${week.drive}, site=${week.site}")
            println("Idle=${week.idle}, miles=${week.drivenMiles}; every backup row and setting preserved")
        } finally {
            database.close()
        }
    }
}

private fun canonical(value: Any?): Any? = when (value) {
    null, JSONObject.NULL -> null
    is JSONObject -> value.keys().asSequence().sorted().associateWith { canonical(value.get(it)) }
    is JSONArray -> (0 until value.length()).map { canonical(value.get(it)) }
    is Number -> value.toString().toBigDecimal().stripTrailingZeros()
    else -> value
}
