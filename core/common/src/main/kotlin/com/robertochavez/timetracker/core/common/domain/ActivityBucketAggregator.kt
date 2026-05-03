package com.robertochavez.timetracker.core.common.domain

import com.robertochavez.timetracker.core.common.model.ActivityBucket
import com.robertochavez.timetracker.core.common.model.ActivityInterval
import java.time.Duration

object ActivityBucketAggregator {
    fun aggregate(intervals: List<ActivityInterval>): Map<ActivityBucket, Duration> {
        val totals = ActivityBucket.entries.associateWith { Duration.ZERO }.toMutableMap()
        intervals.forEach { interval ->
            totals[interval.bucket] = totals.getValue(interval.bucket).plus(Duration.between(interval.start, interval.end))
        }
        return totals.toMap()
    }
}
