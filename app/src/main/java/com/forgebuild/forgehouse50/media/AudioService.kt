package com.forgebuild.forgehouse50.media

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Background-capable audio playback for VerseWell chapter audio.
 *
 * A MediaSessionService so playback continues with the screen off or the
 * app in the background, driven by the system media notification — the
 * correct Android pattern for this, since users keep listening outside the
 * app. Playback sources are either a downloaded on-device file (offline
 * replay) or the VerseWell URL (streams once; the Read screen offers to
 * persist it via the repository's offline audio store).
 */
class AudioService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setSeekBackIncrementMs(5_000)      // spec: skip-back 5s
            .setSeekForwardIncrementMs(5_000)   // spec: skip-forward 5s
            .setHandleAudioBecomingNoisy(true)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        fun mediaItemFor(source: String, title: String): MediaItem =
            MediaItem.Builder()
                .setUri(source)
                .setMediaId(source)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist("ForgeHouse 50")
                        .build()
                )
                .build()
    }
}
