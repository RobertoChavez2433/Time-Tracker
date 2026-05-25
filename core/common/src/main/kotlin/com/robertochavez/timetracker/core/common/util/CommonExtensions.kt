package com.robertochavez.timetracker.core.common.util

import java.time.Duration

const val METERS_PER_MILE: Double = 1_609.344

fun Duration.coerceNonNegative(): Duration = if (isNegative) Duration.ZERO else this

fun String.safePrefix(length: Int = DEFAULT_SAFE_PREFIX_LENGTH): String = take(length)

private const val DEFAULT_SAFE_PREFIX_LENGTH = 8
