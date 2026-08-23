package com.robertochavez.timetracker.core.sync

import com.robertochavez.timetracker.core.common.model.HubDailySummary
import com.robertochavez.timetracker.core.common.model.ProjectDaySummary
import org.json.JSONObject
import java.math.BigDecimal
import javax.inject.Inject

class HubPayloadBuilder @Inject constructor() {
    fun daily(summary: HubDailySummary): String = JSONObject()
        .put("sourceId", summary.sourceId)
        .put("type", "dailySummary")
        .put("date", summary.date.toString())
        .put("totalWorkMinutes", summary.totalWorkMinutes)
        .put("driveMinutes", summary.driveMinutes)
        .put("siteMinutes", summary.siteMinutes)
        .put("idleMinutes", summary.idleMinutes)
        .put("needsReviewMinutes", summary.needsReviewMinutes)
        .put("drivenMiles", BigDecimal.valueOf(summary.drivenMiles).stripTrailingZeros().toPlainString())
        .toString()

    fun projectDay(summary: ProjectDaySummary): String = JSONObject()
        .put("sourceId", summary.sourceId)
        .put("type", "projectDay")
        .put("date", summary.date.toString())
        .put("workLocationId", summary.workLocationId)
        .put("projectNumber", summary.projectNumber)
        .put("siteLabel", summary.siteLabel)
        .put("firstArrival", summary.firstArrival.toString())
        .put("lastDeparture", summary.lastDeparture.toString())
        .put("onsiteMinutes", summary.onsiteMinutes)
        .put("visitCount", summary.visitCount)
        .put("status", summary.status.name.lowercase())
        .toString()
}
