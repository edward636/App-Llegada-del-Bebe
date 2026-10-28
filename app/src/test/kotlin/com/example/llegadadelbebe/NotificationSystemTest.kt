package com.example.llegadadelbebe

import com.example.llegadadelbebe.core.notification.NotificationHelper
import com.example.llegadadelbebe.core.update.UpdateInfo
import com.example.llegadadelbebe.core.util.OemOptimizationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSystemTest {

    @Test
    fun testNotificationChannels_ConstantsConfigured() {
        assertEquals("prenatal_medications_channel", NotificationHelper.CHANNEL_MEDICATIONS)
        assertEquals("ota_updates_channel", NotificationHelper.CHANNEL_OTA_UPDATES)
        assertEquals("baby_engagement_channel", NotificationHelper.CHANNEL_ENGAGEMENT)
        assertEquals(1001, NotificationHelper.NOTIF_ID_OTA)
        assertEquals(2001, NotificationHelper.NOTIF_ID_ENGAGEMENT)
    }

    @Test
    fun testOtaUpdateNotification_ContentFormatting() {
        val update = UpdateInfo(
            hasUpdate = true,
            latestVersionName = "1.4.0",
            latestVersionCode = 18,
            apkUrl = "https://example.com/apk",
            changelog = "Mejoras en notificaciones y módulo 3"
        )

        assertNotNull(update)
        assertEquals("1.4.0", update.latestVersionName)
        assertEquals(18, update.latestVersionCode)
    }

    @Test
    fun testOemOptimizationHelper_DetectedOemString() {
        val detected = OemOptimizationHelper.detectedOemName
        assertNotNull(detected)
        assertTrue(detected.isNotBlank())
    }
}
