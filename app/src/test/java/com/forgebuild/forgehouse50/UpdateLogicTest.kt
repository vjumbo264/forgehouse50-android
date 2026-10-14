package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.update.UpdateLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateLogicTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testVersionComparison() {
        // Installed is 21
        val installed = 21

        // Remote higher -> update available
        assertTrue(UpdateLogic.isUpdateAvailable(installed, remoteVersionCode = 22, remoteVersion = 22))
        assertTrue(UpdateLogic.isUpdateAvailable(installed, remoteVersionCode = 30, remoteVersion = 30))

        // Remote same -> no update
        assertFalse(UpdateLogic.isUpdateAvailable(installed, remoteVersionCode = 21, remoteVersion = 21))

        // Remote lower -> no update
        assertFalse(UpdateLogic.isUpdateAvailable(installed, remoteVersionCode = 20, remoteVersion = 20))

        // Fallback to remoteVersion when versionCode is null
        assertTrue(UpdateLogic.isUpdateAvailable(installed, remoteVersionCode = null, remoteVersion = 22))
        assertFalse(UpdateLogic.isUpdateAvailable(installed, remoteVersionCode = null, remoteVersion = 21))

        // Nulls -> no update
        assertFalse(UpdateLogic.isUpdateAvailable(installed, remoteVersionCode = null, remoteVersion = null))
    }

    @Test
    fun testThrottling() {
        var simulatedClock = 1_000_000_000L

        // First check (lastCheck = 0) -> allowed
        assertTrue(UpdateLogic.shouldCheckNetwork(lastCheckEpochMs = 0L, nowEpochMs = simulatedClock))

        val lastCheck = simulatedClock

        // 10 minutes later -> throttled
        val tenMinutesLater = lastCheck + (10 * 60 * 1000L)
        assertFalse(UpdateLogic.shouldCheckNetwork(lastCheckEpochMs = lastCheck, nowEpochMs = tenMinutesLater))

        // 29 minutes later -> throttled
        val twentyNineMinutesLater = lastCheck + (29 * 60 * 1000L)
        assertFalse(UpdateLogic.shouldCheckNetwork(lastCheckEpochMs = lastCheck, nowEpochMs = twentyNineMinutesLater))

        // 30 minutes later -> allowed
        val thirtyMinutesLater = lastCheck + (30 * 60 * 1000L)
        assertTrue(UpdateLogic.shouldCheckNetwork(lastCheckEpochMs = lastCheck, nowEpochMs = thirtyMinutesLater))

        // 2 hours later -> allowed
        val twoHoursLater = lastCheck + (120 * 60 * 1000L)
        assertTrue(UpdateLogic.shouldCheckNetwork(lastCheckEpochMs = lastCheck, nowEpochMs = twoHoursLater))
    }

    @Test
    fun testLaterReappearanceSchedule() {
        val dismissedAt = 5_000_000_000L

        // Immediately after dismiss -> should not reappear in use
        assertFalse(UpdateLogic.shouldReopenDialogInUse(laterDismissedAtEpochMs = dismissedAt, nowEpochMs = dismissedAt + 1000L))

        // 1 hour later in use -> should not reappear
        val oneHourLater = dismissedAt + (60 * 60 * 1000L)
        assertFalse(UpdateLogic.shouldReopenDialogInUse(laterDismissedAtEpochMs = dismissedAt, nowEpochMs = oneHourLater))

        // 1 hour 59 mins later -> should not reappear
        val almostTwoHours = dismissedAt + (119 * 60 * 1000L)
        assertFalse(UpdateLogic.shouldReopenDialogInUse(laterDismissedAtEpochMs = dismissedAt, nowEpochMs = almostTwoHours))

        // 2 hours later in use -> should reappear!
        val twoHoursLater = dismissedAt + (2 * 60 * 60 * 1000L)
        assertTrue(UpdateLogic.shouldReopenDialogInUse(laterDismissedAtEpochMs = dismissedAt, nowEpochMs = twoHoursLater))

        // 3 hours later in use -> should reappear
        val threeHoursLater = dismissedAt + (3 * 60 * 60 * 1000L)
        assertTrue(UpdateLogic.shouldReopenDialogInUse(laterDismissedAtEpochMs = dismissedAt, nowEpochMs = threeHoursLater))
    }

    @Test
    fun testDailyNotificationThrottling() {
        val lastNotif = 10_000_000_000L

        // No prior notification -> post immediately
        assertTrue(UpdateLogic.shouldPostDailyNotification(lastNotifEpochMs = 0L, nowEpochMs = lastNotif))

        // 4 hours later -> throttled
        assertFalse(UpdateLogic.shouldPostDailyNotification(lastNotifEpochMs = lastNotif, nowEpochMs = lastNotif + (4 * 3600 * 1000L)))

        // 23 hours later -> throttled
        assertFalse(UpdateLogic.shouldPostDailyNotification(lastNotifEpochMs = lastNotif, nowEpochMs = lastNotif + (23 * 3600 * 1000L)))

        // 24 hours later -> allowed
        assertTrue(UpdateLogic.shouldPostDailyNotification(lastNotifEpochMs = lastNotif, nowEpochMs = lastNotif + (24 * 3600 * 1000L)))

        // 48 hours later -> allowed
        assertTrue(UpdateLogic.shouldPostDailyNotification(lastNotifEpochMs = lastNotif, nowEpochMs = lastNotif + (48 * 3600 * 1000L)))
    }

    @Test
    fun testChecksumVerification() {
        val testFile = tempFolder.newFile("test_app.apk")
        val sampleBytes = "FORGEHOUSE50_SIGNED_TEST_APK_BYTES_12345".toByteArray()
        testFile.writeBytes(sampleBytes)

        val actualSha = UpdateLogic.computeSha256(testFile)
        val actualSize = testFile.length()

        // Exact match -> pass
        assertTrue(UpdateLogic.verifyApkFile(testFile, expectedSizeBytes = actualSize, expectedSha256 = actualSha))
        // Case-insensitive hash match -> pass
        assertTrue(UpdateLogic.verifyApkFile(testFile, expectedSizeBytes = actualSize, expectedSha256 = actualSha.uppercase()))

        // Wrong SHA-256 -> fail
        val wrongSha = "0000000000000000000000000000000000000000000000000000000000000000"
        assertFalse(UpdateLogic.verifyApkFile(testFile, expectedSizeBytes = actualSize, expectedSha256 = wrongSha))

        // Wrong size -> fail
        assertFalse(UpdateLogic.verifyApkFile(testFile, expectedSizeBytes = actualSize + 10, expectedSha256 = actualSha))

        // Non-existent file -> fail
        val nonExistent = File(tempFolder.root, "does_not_exist.apk")
        assertFalse(UpdateLogic.verifyApkFile(nonExistent, expectedSizeBytes = 100L, expectedSha256 = actualSha))
    }

    @Test
    fun testChangelogAdminFilter() {
        val rawItems = listOf(
            "Password reset via email verification code",
            "Admin area redesign with new dashboard",
            "In-app update checks and notifications",
            "Delete user functionality added for system administrators",
            "ADMIN credentials protected",
            "Smooth scrolling in Bible reader",
            "Please delete user records after testing"
        )

        val filtered = UpdateLogic.filterChangelogItems(rawItems)

        // Only user-facing non-admin items remain
        assertEquals(3, filtered.size)
        assertEquals("Password reset via email verification code", filtered[0])
        assertEquals("In-app update checks and notifications", filtered[1])
        assertEquals("Smooth scrolling in Bible reader", filtered[2])

        // Verify none contains "admin" or "delete user" (case-insensitive)
        for (item in filtered) {
            val lower = item.lowercase()
            assertFalse(lower.contains("admin"))
            assertFalse(lower.contains("delete user"))
        }
    }
}
