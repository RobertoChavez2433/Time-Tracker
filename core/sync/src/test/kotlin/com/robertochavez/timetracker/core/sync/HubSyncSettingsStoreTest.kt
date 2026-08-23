package com.robertochavez.timetracker.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HubSyncSettingsStoreTest {
    private lateinit var context: Context
    private lateinit var store: HubSyncSettingsStore

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("work_hub_sync", Context.MODE_PRIVATE).edit().clear().commit()
        store = HubSyncSettingsStore(context)
    }

    @After fun tearDown() {
        context.getSharedPreferences("work_hub_sync", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `direct fallback requires a failed provider gate`() {
        assertThrows(IllegalArgumentException::class.java) { store.setTransport(HubTransportKind.DIRECT_NOTION) }

        store.recordBasicProviderCheck(false)
        store.setTransport(HubTransportKind.DIRECT_NOTION)

        assertEquals(HubProviderGateState.BASIC_FAILED, store.current().providerGateState)
        assertEquals(HubTransportKind.DIRECT_NOTION, store.current().transportKind)
    }

    @Test fun `basic pass does not claim full provider gate`() {
        store.recordBasicProviderCheck(true)

        assertEquals(HubProviderGateState.BASIC_PASSED, store.current().providerGateState)

        store.recordFullProviderGate(true)
        assertEquals(HubProviderGateState.FULL_PASSED, store.current().providerGateState)
    }
}
