package com.forgebuild.forgehouse50.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.forgebuild.forgehouse50.data.ApiClient

/**
 * Reusable user avatar with profile photo support (Item 7b).
 * - If user has a photo (`pictureVersion != null`), loads through Coil with session cookie header
 * - Uses version-busted URL for deterministic cache updates
 * - Circular crop applied by UI (the stored picture is a plain 256x256 square)
 * - Falls back to bundled illustration avatar while loading or on failure
 * - If user has no photo (`pictureVersion == null`), renders bundled illustration avatar immediately
 */
@Composable
fun UserAvatar(
    avatarId: String?,
    userId: String?,
    pictureVersion: Int?,
    sessionToken: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null,
) {
    val fallbackPainter = painterResource(AvatarAssets.resFor(avatarId))

    if (pictureVersion != null && !userId.isNullOrBlank()) {
        val context = LocalContext.current
        val pictureUrl = remember(userId, pictureVersion) {
            "${ApiClient.BASE}/users/$userId/picture?v=$pictureVersion"
        }
        val request = remember(pictureUrl, sessionToken) {
            ImageRequest.Builder(context)
                .data(pictureUrl)
                .apply {
                    if (!sessionToken.isNullOrBlank()) {
                        addHeader("Cookie", "fh50_session=$sessionToken")
                    }
                }
                .crossfade(true)
                .build()
        }

        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            placeholder = fallbackPainter,
            error = fallbackPainter,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Image(
            painter = fallbackPainter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
        )
    }
}
