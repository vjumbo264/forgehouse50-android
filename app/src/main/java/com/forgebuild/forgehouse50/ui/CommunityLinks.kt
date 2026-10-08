package com.forgebuild.forgehouse50.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Community actions & reusable pill-shaped action rows matching the premium visual design.
 */
object CommunityLinks {
    const val WHATSAPP_GROUP_URL = "https://chat.whatsapp.com/KNt6BxuzOWwFZ9p4LL2Dfq"
    // streak_reader_support_fix_v1 / Issue 4: pre-filled message for support chat
    const val SUPPORT_MESSAGE = "Hi, I'd like to support ForgeHouse 50"
    const val SUPPORT_WA_ME_URL = "https://wa.me/2349139095481?text=Hi%2C%20I%27d%20like%20to%20support%20ForgeHouse%2050" // admin +234 913 909 5481

    fun open(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}

/**
 * Reusable full-width pill-shaped action row with leading icon chip and trailing chevron.
 */
@Composable
fun ActionPillRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = iconContainerColor,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** "Join ForgeHouse WhatsApp Group" — community invite action. */
@Composable
fun JoinWhatsAppGroupButton(context: Context, modifier: Modifier = Modifier) {
    ActionPillRow(
        icon = Icons.Filled.Groups,
        iconTint = MaterialTheme.colorScheme.secondary,
        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        label = "Join ForgeHouse WhatsApp Group",
        onClick = { CommunityLinks.open(context, CommunityLinks.WHATSAPP_GROUP_URL) },
        modifier = modifier,
    )
}

/** "Support ForgeHouse Global" — direct wa.me chat with admin. */
@Composable
fun SupportWhatsAppButton(context: Context, modifier: Modifier = Modifier) {
    ActionPillRow(
        icon = Icons.Filled.Favorite,
        iconTint = MaterialTheme.colorScheme.tertiary,
        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
        label = "Support ForgeHouse Global",
        onClick = { CommunityLinks.open(context, CommunityLinks.SUPPORT_WA_ME_URL) },
        modifier = modifier,
    )
}
