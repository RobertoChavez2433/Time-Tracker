package com.robertochavez.timetracker.core.common.model

import org.junit.Assert.assertTrue
import org.junit.Test

class PayPeriodSettingsContractTest {
    @Test
    fun `pay period exposes cadence and anchor start date`() {
        val methodNames = PayPeriodSettings::class.java.methods.map { it.name }.toSet()

        assertTrue("PayPeriodSettings must expose getCadence", "getCadence" in methodNames)
        assertTrue("PayPeriodSettings must expose getAnchorStartDate", "getAnchorStartDate" in methodNames)
    }

    @Test
    fun `pay period supports weekly and biweekly cadence`() {
        val cadenceClass = Class.forName(
            "com.robertochavez.timetracker.core.common.model.PayPeriodCadence",
        )
        val enumNames = cadenceClass.enumConstants.map { (it as Enum<*>).name }.toSet()

        assertTrue("WEEKLY cadence is required", "WEEKLY" in enumNames)
        assertTrue("BIWEEKLY cadence is required", "BIWEEKLY" in enumNames)
    }
}
