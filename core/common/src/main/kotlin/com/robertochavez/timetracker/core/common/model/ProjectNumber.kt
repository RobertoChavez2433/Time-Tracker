package com.robertochavez.timetracker.core.common.model

import java.util.Locale

fun normalizeProjectNumber(value: String): String = value.trim().uppercase(Locale.US)

fun String.normalizedProjectNumberOrNull(): String? = normalizeProjectNumber(this).ifBlank { null }
