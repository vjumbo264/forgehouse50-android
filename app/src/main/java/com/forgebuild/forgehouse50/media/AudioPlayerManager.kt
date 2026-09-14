package com.forgebuild.forgehouse50.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App-side handle to the [AudioService] MediaSession. Exposes simple
 * StateFlows the Read screen renders, and the seek/skip-5s controls the
 * spec calls for. A single shared controller means playback keeps going
 * (with its media notification) when the user leaves the screen.
 */
class AudioPlayerManager(private val context: Context) {

    data class PlaybackUiState(
        val isConnected: Boolean = false,
        val isPlaying: Boolean = false,
        val currentSource: String? = null,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val bufferedMs: Long = 0,
    )

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state

    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = push()
        override fun onPlaybackStateChanged(playbackState: Int) = push()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = push()
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, AudioService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get().also {
                it.addListener(listener)
                push()
            }
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        _state.value = PlaybackUiState()
    }

    /** Start (or switch to) a source: a local file path or an https URL. */
    fun play(source: String, title: String, resumePositionMs: Long = 0) {
        val c = controller ?: return
        if (_state.value.currentSource == source && c.playbackState != Player.STATE_IDLE) {
            c.play(); push(); return
        }
        c.setMediaItem(AudioService.mediaItemFor(source, title), resumePositionMs)
        c.prepare()
        c.play()
        push()
    }

    fun pause() { controller?.pause(); push() }
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() }; push() }
    fun seekTo(ms: Long) { controller?.seekTo(ms.coerceAtLeast(0)); push() }
    fun skipBack5s() { controller?.seekBack(); push() }
    fun skipForward5s() { controller?.seekForward(); push() }

    /** Poll position while playing so the slider and auto-scroll stay live. */
    suspend fun tickPosition() {
        while (true) {
            push()
            delay(500)
        }
    }

    private fun push() {
        val c = controller
        _state.value = if (c == null) PlaybackUiState() else PlaybackUiState(
            isConnected = true,
            isPlaying = c.isPlaying,
            currentSource = c.currentMediaItem?.mediaId,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.coerceAtLeast(0),
            bufferedMs = c.bufferedPosition.coerceAtLeast(0),
        )
    }
}
