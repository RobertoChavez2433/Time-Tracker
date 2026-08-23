package com.robertochavez.timetracker.core.common.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectNumberTest {
    @Test fun `normalization trims and uppercases while preserving punctuation and leading zeroes`() {
        assertEquals("001-AB 7.2", normalizeProjectNumber(" 001-Ab 7.2 "))
    }

    @Test fun `blank project number becomes null`() {
        assertNull("   ".normalizedProjectNumberOrNull())
    }
}
