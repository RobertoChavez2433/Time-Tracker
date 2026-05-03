package com.robertochavez.timetracker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.robertochavez.timetracker.core.database.repository.RoomWorkPresenceRepository
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
class WorkPresenceRepositoryTest {
    private lateinit var database: TimeTrackerDatabase
    private lateinit var repository: RoomWorkPresenceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TimeTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomWorkPresenceRepository(
            workPresenceDao = database.workPresenceDao(),
            logger = NoopAppLogger(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `defaults to away from work`() = runTest {
        assertEquals(false, repository.getWorkPresence().atWork)
    }

    @Test
    fun `persists work presence`() = runTest {
        val updatedAt = Instant.parse("2026-05-03T12:00:00Z")

        repository.setAtWork(true, updatedAt)

        assertEquals(true, repository.getWorkPresence().atWork)
        assertEquals(updatedAt, repository.observeWorkPresence().first().updatedAt)
    }
}
