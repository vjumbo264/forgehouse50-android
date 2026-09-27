package com.forgebuild.forgehouse50.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * catchup_widget_links_v1 / ISSUE 1 — the two DELIBERATELY DISTINCT WhatsApp
 * actions:
 *
 *  1. Join the ForgeHouse WhatsApp COMMUNITY GROUP (chat.whatsapp.com invite).
 *  2. Support ForgeHouse Global — a DIRECT chat with the admin's personal
 *     number via the standard wa.me deep-link (international format, no
 *     plus/spaces per the wa.me convention), so tapping opens WhatsApp with a
 *     chat to that number ready to send.
 *
 * Joining the community and contacting the admin to support the project are
 * different actions with different intents — they are never merged.
 */
object CommunityLinks {
    const val WHATSAPP_GROUP_URL = "https://chat.whatsapp.com/KNt6BxuzOWwFZ9p4LL2Dfq"
    const val SUPPORT_WA_ME_URL = "https://wa.me/2349139095481" // admin +234 913 909 5481

    fun open(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}

/** "Join ForgeHouse WhatsApp Group" — community invite action. */
@Composable
fun JoinWhatsAppGroupButton(context: Context, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = { CommunityLinks.open(context, CommunityLinks.WHATSAPP_GROUP_URL) },
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Groups, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Join ForgeHouse WhatsApp Group")
    }
}

/** "Support ForgeHouse Global" — direct wa.me chat with the admin. */
@Composable
fun SupportWhatsAppButton(context: Context, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = { CommunityLinks.open(context, CommunityLinks.SUPPORT_WA_ME_URL) },
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Favorite, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Support ForgeHouse Global")
    }
}
