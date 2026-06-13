package com.robertochavez.timetracker.privacy

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class ManifestPrivacyGuardTest {
    @Test
    fun `manifests do not declare texting or device-input interception capabilities`() {
        val manifestText = MANIFEST_PATHS
            .mapNotNull { path -> File(path).takeIf(File::isFile)?.readText() }
            .joinToString(separator = "\n")

        DISALLOWED_MANIFEST_MARKERS.forEach { marker ->
            assertFalse("Manifest must not contain $marker", manifestText.contains(marker))
        }
    }

    private companion object {
        val MANIFEST_PATHS = listOf(
            "src/main/AndroidManifest.xml",
            "../core/location/src/main/AndroidManifest.xml",
            "../core/notifications/src/main/AndroidManifest.xml",
            "../feature/home/src/main/AndroidManifest.xml",
            "../feature/reports/src/main/AndroidManifest.xml",
            "../feature/settings/src/main/AndroidManifest.xml",
            "../feature/tracking/src/main/AndroidManifest.xml",
        )

        val DISALLOWED_MANIFEST_MARKERS = listOf(
            "android.permission.SEND_SMS",
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.CALL_PHONE",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.permission.BIND_INPUT_METHOD",
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
            "android.permission.BIND_SCREENING_SERVICE",
            "AccessibilityService",
            "InputMethodService",
            "NotificationListenerService",
            "CallScreeningService",
        )
    }
}
