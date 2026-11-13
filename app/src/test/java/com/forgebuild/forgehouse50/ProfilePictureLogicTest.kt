package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.data.ApiClient
import com.forgebuild.forgehouse50.data.LeaderboardEntry
import com.forgebuild.forgehouse50.data.MeResponse
import com.forgebuild.forgehouse50.data.Participant
import com.forgebuild.forgehouse50.data.ParticipantDetail
import com.forgebuild.forgehouse50.ui.profile.ProfilePictureProcessor
import com.forgebuild.forgehouse50.ui.profile.ProfilePicturePromptLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePictureLogicTest {

    @Test
    fun testProfilePictureUrlConstruction() {
        fun buildPictureUrl(userId: String?, version: Int?): String? {
            if (userId.isNullOrBlank() || version == null) return null
            return "${ApiClient.BASE}/users/$userId/picture?v=$version"
        }

        val url = buildPictureUrl("u_123", 2)
        assertEquals("https://forgehouse50.pages.dev/api/users/u_123/picture?v=2", url)

        assertNull(buildPictureUrl(null, 2))
        assertNull(buildPictureUrl("u_123", null))
        assertNull(buildPictureUrl("", 1))
    }

    @Test
    fun testPictureProcessorConstants() {
        assertEquals(256, ProfilePictureProcessor.TARGET_SIZE)
        assertEquals(100 * 1024, ProfilePictureProcessor.MAX_ALLOWED_BYTES)
        assertEquals(40 * 1024, ProfilePictureProcessor.TARGET_BYTES)
    }

    @Test
    fun testAdminAndUserRemovePictureStateReversion() {
        val userWithPhoto = MeResponse(
            id = "u_456",
            name = "Sarah",
            surname = "Adeyemi",
            avatar_id = "lion",
            picture_version = 3
        )
        assertNotNull(userWithPhoto.picture_version)
        assertEquals("lion", userWithPhoto.avatar_id)

        // Removing photo reverts picture_version to null while preserving avatar_id
        val reverted = userWithPhoto.copy(picture_version = null)
        assertNull(reverted.picture_version)
        assertEquals("lion", reverted.avatar_id)

        // Admin participant detail reversion
        val participantDetail = ParticipantDetail(
            id = "u_456",
            name = "Sarah",
            surname = "Adeyemi",
            avatar_id = "eagle",
            picture_version = 1
        )
        val adminReverted = participantDetail.copy(picture_version = null)
        assertNull(adminReverted.picture_version)
        assertEquals("eagle", adminReverted.avatar_id)
    }

    @Test
    fun testLeaderboardEntryPictureVersionIntegration() {
        val entry = LeaderboardEntry(
            rank = 1,
            user_id = "u_999",
            display_name = "David O",
            avatar_id = "flame",
            picture_version = 4,
            value = 250.0
        )
        assertEquals(4, entry.picture_version)
        assertEquals("flame", entry.avatar_id)
    }

    @Test
    fun testProfilePicturePromptShownOnlyWhenMissingAndNotRecentlyDismissed() {
        val now = 2_000_000_000_000L
        assertFalse(ProfilePicturePromptLogic.shouldShowPrompt(null, 0L, now))

        val withoutPhoto = MeResponse(id = "u_1", name = "Ada", avatar_id = "lion", picture_version = null)
        assertTrue(ProfilePicturePromptLogic.shouldShowPrompt(withoutPhoto, 0L, now))

        // Dismissed 1 day ago -> suppressed
        val oneDayAgo = now - 24L * 60 * 60 * 1000L
        assertFalse(ProfilePicturePromptLogic.shouldShowPrompt(withoutPhoto, oneDayAgo, now))

        // Dismissed 15 days ago -> gently shows again
        val fifteenDaysAgo = now - 15L * 24 * 60 * 60 * 1000L
        assertTrue(ProfilePicturePromptLogic.shouldShowPrompt(withoutPhoto, fifteenDaysAgo, now))

        // Once user uploads a photo -> never shows
        val withPhoto = withoutPhoto.copy(picture_version = 1)
        assertFalse(ProfilePicturePromptLogic.shouldShowPrompt(withPhoto, 0L, now))
        assertFalse(ProfilePicturePromptLogic.shouldShowPrompt(withPhoto, fifteenDaysAgo, now))
    }
}
