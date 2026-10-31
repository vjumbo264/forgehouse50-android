package com.forgebuild.forgehouse50.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.forgebuild.forgehouse50.data.ReaderErrorSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class AudioPlayerUiState(
    val activeChapterKey: String? = null,
    val versionCode: String = "",
    val book: String = "",
    val chapter: Int = 0,
    val isPlaying: Boolean = false,
    val isEnded: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val error: String? = null,
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toDouble() / durationMs.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
}

/**
 * Local-file-only Media3 ExoPlayer controller for chapter audio (Item i10).
 *
 * Integrity guarantees:
 * - Plays exclusively from verified local `.m4a` files (zero network streaming; works offline).
 * - Single chapter item with `REPEAT_MODE_OFF`: stops at chapter end (no auto-advance).
 * - Zero coupling to [com.forgebuild.forgehouse50.data.ReadingTimer] or scroll state (no auto-scroll, zero reading credit).
 * - Foreground-only by default ([AudioConfig.ALLOW_BACKGROUND_PLAYBACK] == false): pauses on background / screen lock.
 */
class AudioPlayerController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var exoPlayer: ExoPlayer? = null
    private var tickerJob: Job? = null

    private val _uiState = MutableStateFlow(AudioPlayerUiState())
    val uiState: StateFlow<AudioPlayerUiState> = _uiState.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val p = exoPlayer ?: return
            val dur = p.duration.takeIf { it > 0L } ?: _uiState.value.durationMs
            val pos = p.currentPosition.coerceAtLeast(0L)
            _uiState.value = _uiState.value.copy(
                isPlaying = isPlaying,
                positionMs = pos.coerceAtMost(maxOf(dur, pos)),
                durationMs = dur,
            )
            if (isPlaying) startPositionTicker() else stopPositionTicker()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val p = exoPlayer ?: return
            val dur = p.duration.takeIf { it > 0L } ?: _uiState.value.durationMs
            if (playbackState == Player.STATE_ENDED) {
                // Strictly stop at end of current chapter: NO auto-advance.
                p.playWhenReady = false
                stopPositionTicker()
                _uiState.value = _uiState.value.copy(
                    isPlaying = false,
                    isEnded = true,
                    positionMs = dur,
                    durationMs = dur,
                )
            } else if (playbackState == Player.STATE_READY) {
                _uiState.value = _uiState.value.copy(
                    durationMs = dur,
                    positionMs = p.currentPosition.coerceAtLeast(0L),
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            stopPositionTicker()
            val friendly = ReaderErrorSanitizer.toUserMessage(
                error,
                fallback = "Could not play this audio file. Try deleting and re-downloading the chapter."
            ) ?: "Could not play this audio file."
            _uiState.value = _uiState.value.copy(
                isPlaying = false,
                error = friendly,
            )
        }
    }

    private fun ensurePlayerOnMain(): ExoPlayer {
        exoPlayer?.let { return it }
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        return ExoPlayer.Builder(appContext)
            .setAudioAttributes(attrs, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(listener)
                exoPlayer = this
            }
    }

    fun playOrToggleLocalChapter(
        localFile: File,
        versionCodeOrId: String,
        book: String,
        chapter: Int,
        fallbackDurationMs: Long = 0L,
    ) {
        mainScope.launch {
            val codeUpper = AudioLogic.normalizeVersionCode(versionCodeOrId)
            val key = AudioLogic.chapterKey(codeUpper, book, chapter)
            val cur = _uiState.value
            if (cur.activeChapterKey == key && exoPlayer != null) {
                togglePlayPause()
                return@launch
            }
            if (!localFile.exists() || !localFile.isFile || localFile.length() <= 0L) {
                _uiState.value = cur.copy(
                    activeChapterKey = key,
                    versionCode = codeUpper,
                    book = book,
                    chapter = chapter,
                    isPlaying = false,
                    error = "Downloaded audio file was not found on device.",
                )
                return@launch
            }

            val player = ensurePlayerOnMain()
            val speed = AudioLogic.clampPlaybackSpeed(cur.speed)
            player.stop()
            player.clearMediaItems()
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(localFile)))
            player.setPlaybackSpeed(speed)
            _uiState.value = AudioPlayerUiState(
                activeChapterKey = key,
                versionCode = codeUpper,
                book = book,
                chapter = chapter,
                isPlaying = true,
                isEnded = false,
                positionMs = 0L,
                durationMs = fallbackDurationMs.coerceAtLeast(0L),
                speed = speed,
                error = null,
            )
            player.prepare()
            player.play()
        }
    }

    fun togglePlayPause() {
        mainScope.launch {
            val player = exoPlayer ?: return@launch
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_ENDED || _uiState.value.isEnded) {
                    player.seekTo(0L)
                    _uiState.value = _uiState.value.copy(isEnded = false, positionMs = 0L)
                }
                player.play()
            }
        }
    }

    fun pause() {
        mainScope.launch {
            exoPlayer?.pause()
            stopPositionTicker()
            if (_uiState.value.isPlaying) {
                _uiState.value = _uiState.value.copy(isPlaying = false)
            }
        }
    }

    fun seekToFraction(fraction: Float) {
        mainScope.launch {
            val player = exoPlayer ?: return@launch
            val dur = player.duration.takeIf { it > 0L } ?: _uiState.value.durationMs
            if (dur <= 0L) return@launch
            val targetMs = (dur.toDouble() * fraction.coerceIn(0f, 1f).toDouble()).toLong().coerceIn(0L, dur)
            player.seekTo(targetMs)
            _uiState.value = _uiState.value.copy(
                positionMs = targetMs,
                isEnded = targetMs >= dur && !player.isPlaying,
            )
        }
    }

    fun cycleSpeed() {
        val next = AudioLogic.nextPlaybackSpeed(_uiState.value.speed)
        setSpeed(next)
    }

    fun setSpeed(speed: Float) {
        mainScope.launch {
            val clamped = AudioLogic.clampPlaybackSpeed(speed)
            exoPlayer?.setPlaybackSpeed(clamped)
            _uiState.value = _uiState.value.copy(speed = clamped)
        }
    }

    /**
     * Enforces [AudioConfig.ALLOW_BACKGROUND_PLAYBACK] when app leaves foreground or screen locks.
     */
    fun onAppBackgroundedOrLocked() {
        if (AudioLogic.shouldPauseForBackground(AudioConfig.ALLOW_BACKGROUND_PLAYBACK)) {
            pause()
        }
    }

    fun stopAndDismiss() {
        mainScope.launch {
            stopPositionTicker()
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            _uiState.value = AudioPlayerUiState(speed = _uiState.value.speed)
        }
    }

    fun release() {
        stopPositionTicker()
        exoPlayer?.let { p ->
            p.removeListener(listener)
            p.release()
        }
        exoPlayer = null
        mainScope.cancel()
    }

    private fun startPositionTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = mainScope.launch {
            while (isActive) {
                val p = exoPlayer
                if (p != null && p.isPlaying) {
                    val dur = p.duration.takeIf { it > 0L } ?: _uiState.value.durationMs
                    val pos = p.currentPosition.coerceAtLeast(0L)
                    _uiState.value = _uiState.value.copy(
                        positionMs = if (dur > 0L) pos.coerceAtMost(dur) else pos,
                        durationMs = dur,
                    )
                }
                delay(250L)
            }
        }
    }

    private fun stopPositionTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }
}
