package com.forgebuild.forgehouse50

import com.forgebuild.forgehouse50.audio.AudioChapterMeta
import com.forgebuild.forgehouse50.audio.AudioConfig
import com.forgebuild.forgehouse50.audio.AudioDownloadEngine
import com.forgebuild.forgehouse50.audio.AudioLogic
import com.forgebuild.forgehouse50.audio.AudioManifest
import com.forgebuild.forgehouse50.audio.AudioTransport
import com.forgebuild.forgehouse50.audio.BulkDownloadProgress
import com.forgebuild.forgehouse50.audio.InMemoryAudioIndexStore
import com.forgebuild.forgehouse50.data.InMemoryTimerStorage
import com.forgebuild.forgehouse50.data.ReadingTimer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest

class AudioFeatureTest {

    private fun sha256Of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun testInstantAudioAvailabilityWithDefaultCatalogAndOfflineFallback() {
        val defaultCatalog = AudioLogic.defaultVersionsCatalog()
        assertEquals(5, defaultCatalog.size)
        assertTrue(defaultCatalog.containsKey("KJV"))
        assertTrue(defaultCatalog.containsKey("NIV"))
        assertTrue(defaultCatalog.containsKey("TLB"))
        assertTrue(defaultCatalog.containsKey("GW"))
        assertTrue(defaultCatalog.containsKey("VOICE"))
        assertEquals(260, defaultCatalog["KJV"]?.chapters)

        // Instant availability on cold launch with zero network calls and empty catalog
        assertTrue(AudioLogic.isTranslationAudioAvailable("KJV", emptyMap()))
        assertTrue(AudioLogic.isTranslationAudioAvailable("versewell-niv", emptyMap()))
        assertTrue(AudioLogic.isTranslationAudioAvailable("TLB", emptyMap()))
        assertTrue(AudioLogic.isTranslationAudioAvailable("gw", emptyMap()))
        assertTrue(AudioLogic.isTranslationAudioAvailable("VOICE", emptyMap()))

        // Non-supported translations must remain false
        assertFalse(AudioLogic.isTranslationAudioAvailable("AMP", emptyMap()))
        assertFalse(AudioLogic.isTranslationAudioAvailable("versewell-nlt", emptyMap()))
        assertFalse(AudioLogic.isTranslationAudioAvailable("MSG", emptyMap()))

        // Chapter synthesis fallback for instant download queuing
        val synthMeta = AudioLogic.synthesizeChapterMeta("KJV", "1 Corinthians", 13)
        assertEquals("1-corinthians-013.m4a", synthMeta.file)
        assertEquals("1 Corinthians", synthMeta.book)
        assertEquals(13, synthMeta.chapter)
    }

    @Test
    fun testVersionsCatalogAndFiveSupportedTranslationsOnly() {
        val raw = """
            {
              "versions": [
                {"code": "AMP", "name": "Amplified", "audio": {"available": false}},
                {"code": "KJV", "name": "King James Version", "audio": {"available": true, "chapters": 260, "total_bytes": 375085200, "voice": "en-US-ChristopherNeural"}},
                {"code": "NIV", "name": "New International Version", "audio": {"available": true, "chapters": 260, "total_bytes": 366000000, "voice": "en-US-JennyNeural"}},
                {"code": "TLB", "name": "The Living Bible", "audio": {"available": true, "chapters": 260, "total_bytes": 390000000, "voice": "en-US-GuyNeural"}},
                {"code": "GW", "name": "GOD'S WORD", "audio": {"available": true, "chapters": 260, "total_bytes": 383860042, "voice": "en-US-AriaNeural"}},
                {"code": "VOICE", "name": "The Voice", "audio": {"available": true, "chapters": 260, "total_bytes": 410000000, "voice": "en-US-EricNeural"}},
                {"code": "NLT", "name": "New Living Translation", "audio": {"available": false}}
              ]
            }
        """.trimIndent()

        val catalog = AudioLogic.parseVersionsCatalog(raw)
        assertTrue(AudioLogic.isTranslationAudioAvailable("versewell-kjv", catalog))
        assertTrue(AudioLogic.isTranslationAudioAvailable("NIV", catalog))
        assertTrue(AudioLogic.isTranslationAudioAvailable("versewell-tlb", catalog))
        assertTrue(AudioLogic.isTranslationAudioAvailable("gw", catalog))
        assertTrue(AudioLogic.isTranslationAudioAvailable("versewell-voice", catalog))

        assertFalse(AudioLogic.isTranslationAudioAvailable("versewell-amp", catalog))
        assertFalse(AudioLogic.isTranslationAudioAvailable("versewell-nlt", catalog))
        assertFalse(AudioLogic.isTranslationAudioAvailable("versewell-msg", catalog))
    }

    @Test
    fun testPartialManifestChapterLookupAndUrlGeneration() {
        val manifestJson = """
            {
              "version": "KJV",
              "voice": "en-US-ChristopherNeural",
              "chapters": [
                {
                  "file": "matthew-001.m4a",
                  "book": "Matthew",
                  "book_slug": "matthew",
                  "chapter": 1,
                  "bytes": 1147125,
                  "sha256": "ca3c474c853a03e44fe39746b8160144c215673c94e3170df20d983aaf118201",
                  "duration_ms": 196000,
                  "verses": [{"verse": 1, "start_ms": 3268, "end_ms": 9004}]
                },
                {
                  "file": "1-corinthians-013.m4a",
                  "book": "1 Corinthians",
                  "book_slug": "1-corinthians",
                  "chapter": 13,
                  "bytes": 620000,
                  "sha256": "59ce340b58b3cf1f89795a068c879cfb25297ffe948da0141316b4f6f2e2b054",
                  "duration_ms": 110000
                }
              ]
            }
        """.trimIndent()

        val manifest = AudioLogic.parseManifest(manifestJson)
        val matt1 = AudioLogic.findChapterInManifest(manifest, "Matthew", 1)
        assertNotNull(matt1)
        assertEquals("matthew-001.m4a", matt1!!.file)

        val cor13 = AudioLogic.findChapterInManifest(manifest, "1 corinthians", 13)
        assertNotNull(cor13)
        assertEquals(
            "https://versewell-api.vjumbo264.workers.dev/api/v1/audio/kjv/1-corinthians/13",
            AudioLogic.chapterAudioUrl("versewell-kjv", cor13!!),
        )

        // Chapter not yet in manifest -> returns null (UI hides audio controls)
        assertNull(AudioLogic.findChapterInManifest(manifest, "Matthew", 2))
        assertNull(AudioLogic.findChapterInManifest(null, "Matthew", 1))
    }

    @Test
    fun testDownloadSkipIfValidResumeFromPartAndSha256Rejection() = runBlocking {
        val tempRoot = Files.createTempDirectory("fh50_audio_test").toFile()
        try {
            val ch1Bytes = ByteArray(40_000) { (it * 7 + 3).toByte() }
            val ch2Bytes = ByteArray(50_000) { (it * 13 + 9).toByte() }
            val ch1Sha = sha256Of(ch1Bytes)
            val ch2Sha = sha256Of(ch2Bytes)

            val meta1 = AudioChapterMeta(
                file = "matthew-001.m4a",
                book = "Matthew",
                book_slug = "matthew",
                chapter = 1,
                bytes = ch1Bytes.size.toLong(),
                sha256 = ch1Sha,
                duration_ms = 60_000L,
            )
            val meta2 = AudioChapterMeta(
                file = "matthew-002.m4a",
                book = "Matthew",
                book_slug = "matthew",
                chapter = 2,
                bytes = ch2Bytes.size.toLong(),
                sha256 = ch2Sha,
                duration_ms = 75_000L,
            )
            val manifest = AudioManifest(
                version = "KJV",
                voice = "en-US-ChristopherNeural",
                chapters = listOf(meta1, meta2),
            )

            var streamCalls = 0
            var lastResumeOffset = -1L
            var serveCorruptedBytes = false

            val fakeTransport = object : AudioTransport {
                override suspend fun fetchText(url: String): String = ""
                override suspend fun streamToFile(
                    url: String,
                    destPartFile: File,
                    expectedTotalBytes: Long,
                    onProgress: (bytesOnDisk: Long, totalBytes: Long) -> Unit,
                ) {
                    streamCalls++
                    destPartFile.parentFile?.mkdirs()
                    val payload = when {
                        serveCorruptedBytes -> ByteArray(expectedTotalBytes.toInt()) { 0x7F }
                        url.endsWith("/matthew/1") -> ch1Bytes
                        url.endsWith("/matthew/2") -> ch2Bytes
                        else -> error("Unexpected url $url")
                    }
                    val offset = if (destPartFile.exists()) destPartFile.length().toInt() else 0
                    lastResumeOffset = offset.toLong()
                    FileOutputStream(destPartFile, offset > 0).use { out ->
                        out.write(payload, offset, payload.size - offset)
                    }
                    onProgress(payload.size.toLong(), payload.size.toLong())
                }
            }

            val indexStore = InMemoryAudioIndexStore()
            val engine = AudioDownloadEngine(tempRoot, indexStore, fakeTransport)

            // 1. Pre-seed partial .part file for chapter 1 (simulating interrupted download at 15,000 bytes)
            val part1 = engine.partFile("KJV", meta1.file)
            part1.parentFile?.mkdirs()
            part1.writeBytes(ch1Bytes.copyOfRange(0, 15_000))

            // Resume chapter 1 -> must resume from offset 15,000, verify full SHA-256, and promote .part -> .m4a
            val f1 = engine.downloadChapter("versewell-kjv", meta1)
            assertEquals(15_000L, lastResumeOffset)
            assertEquals(1, streamCalls)
            assertFalse(part1.exists())
            assertTrue(f1.exists())
            assertTrue(AudioLogic.verifyAudioFile(f1, meta1.bytes, meta1.sha256))

            // 2. Bulk translation download: chapter 1 is ALREADY valid on disk -> must skip chapter 1 (0 network calls for ch1)
            //    and only download chapter 2!
            val progressSnapshots = mutableListOf<BulkDownloadProgress>()
            engine.downloadTranslation("KJV", manifest) { progressSnapshots.add(it) }
            assertEquals("Chapter 1 must be skipped via SHA-256 check; only chapter 2 fetched", 2, streamCalls)
            assertTrue(progressSnapshots.isNotEmpty())
            assertEquals(2, progressSnapshots.last().chaptersDone)
            assertEquals((ch1Bytes.size + ch2Bytes.size).toLong(), progressSnapshots.last().bytesDownloaded)
            assertNotNull(engine.resolveVerifiedLocalFile("KJV", "Matthew", 2, verifyHashNow = true))

            // 3. Corrupted bytes (SHA-256 mismatch) MUST be rejected, deleted, and never indexed
            val badMeta = AudioChapterMeta(
                file = "matthew-003.m4a",
                book = "Matthew",
                book_slug = "matthew",
                chapter = 3,
                bytes = 12_000L,
                sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                duration_ms = 30_000L,
            )
            serveCorruptedBytes = true
            var threw = false
            try {
                engine.downloadChapter("KJV", badMeta)
            } catch (_: IllegalStateException) {
                threw = true
            }
            assertTrue("Tampered audio must fail SHA-256 verification", threw)
            assertFalse(engine.chapterFile("KJV", badMeta.file).exists())
            assertFalse(engine.partFile("KJV", badMeta.file).exists())
            assertNull(indexStore.getChapter(AudioLogic.chapterKey("KJV", "Matthew", 3)))

            // 4. Delete translation audio frees all .m4a files and clears index
            engine.deleteTranslationAudio("KJV")
            assertTrue(indexStore.getForVersion("kjv").isEmpty())
            assertFalse(engine.chapterFile("KJV", meta1.file).exists())
            assertFalse(engine.chapterFile("KJV", meta2.file).exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun testPlaybackSpeedBoundsAndIntegrityFlags() {
        assertEquals(0.75f, AudioLogic.clampPlaybackSpeed(0.25f), 0.001f)
        assertEquals(1.5f, AudioLogic.clampPlaybackSpeed(2.5f), 0.001f)
        assertEquals(1.0f, AudioLogic.clampPlaybackSpeed(1.0f), 0.001f)

        // Speed cycle: 1.0 -> 1.25 -> 1.5 -> 0.75 -> 1.0
        var speed = 1.0f
        speed = AudioLogic.nextPlaybackSpeed(speed)
        assertEquals(1.25f, speed, 0.001f)
        speed = AudioLogic.nextPlaybackSpeed(speed)
        assertEquals(1.5f, speed, 0.001f)
        speed = AudioLogic.nextPlaybackSpeed(speed)
        assertEquals(0.75f, speed, 0.001f)
        speed = AudioLogic.nextPlaybackSpeed(speed)
        assertEquals(1.0f, speed, 0.001f)

        // Foreground-only default behind single config flag
        assertFalse(AudioConfig.ALLOW_BACKGROUND_PLAYBACK)
        assertTrue(AudioLogic.shouldPauseForBackground(AudioConfig.ALLOW_BACKGROUND_PLAYBACK))
        assertFalse(AudioLogic.shouldPauseForBackground(allowBackgroundPlayback = true))

        // Strict integrity invariants
        assertFalse(AudioLogic.shouldAutoAdvanceOnChapterEnd())
        assertFalse(AudioLogic.shouldAutoScrollReader())
        assertFalse(AudioLogic.shouldCountPlaybackTowardsReadingTimer())
    }

    @Test
    fun testAudioPlaybackNeverAdvancesReadingTimerOrCompletion() {
        val clock = FakeMonotonicClock(1_000L)
        val timer = ReadingTimer(
            userId = "member_audio_test",
            day = 5,
            storage = InMemoryTimerStorage(),
            clock = clock,
            wallClock = FakeWallClock(),
            idleTimeoutMs = 60_000L,
        )
        timer.start()

        // User opens reader and plays a 10-minute chapter audio WITHOUT touching/scrolling the Scripture text.
        // Initial start allows at most 60s before idle timeout pauses the timer; after 60s, 9 more minutes of
        // audio play with zero touch interaction.
        var lastSynced = timer.getReadingSeconds()
        var totalServerSyncedDuringIdle = 0
        repeat(600) { secIndex ->
            clock.advance(1_000L)
            val currentSec = timer.tick()
            // After initial 60s idle grace window, no new reading seconds may ever accrue or sync
            if (secIndex == 65) {
                lastSynced = currentSec
            }
            if (secIndex > 65 && secIndex % 30 == 0) {
                val delta = AudioLogic.computeSyncedReadingDelta(currentSec, lastSynced)
                totalServerSyncedDuringIdle += delta
                lastSynced = currentSec
            }
        }

        assertTrue("Timer must enter idle-paused state when screen is untouched during audio playback", timer.isIdlePaused())
        assertFalse("Playing audio untouched must NEVER reach the 5-minute completion threshold", timer.hasReachedMinimum())
        assertEquals("Zero reading seconds may sync to server while audio plays on an untouched screen", 0, totalServerSyncedDuringIdle)
    }

    @Test
    fun testFloatingAudioLogicResolvesAllStatesAndProgressRing() {
        val hidden = FloatingAudioLogic.resolve(
            isAudioSupported = false,
            currentBook = "Genesis",
            currentChapter = 1,
            activeAudioCode = "ESV",
            currentChapterKey = "ESV:genesis:1",
            isCurrentDownloaded = false,
            currentDownloadState = ChapterAudioDownloadState.Idle,
            manifestMeta = null,
            localRow = null,
            playerState = AudioPlayerUiState(),
        )
        assertEquals(FloatingAudioMode.HIDDEN, hidden.mode)

        val needsDl = FloatingAudioLogic.resolve(
            isAudioSupported = true,
            currentBook = "Genesis",
            currentChapter = 1,
            activeAudioCode = "KJV",
            currentChapterKey = "KJV:genesis:1",
            isCurrentDownloaded = false,
            currentDownloadState = ChapterAudioDownloadState.Idle,
            manifestMeta = AudioChapterMeta(file = "genesis-001.m4a", book = "Genesis", chapter = 1, bytes = 1_200_000L),
            localRow = null,
            playerState = AudioPlayerUiState(),
        )
        assertEquals(FloatingAudioMode.NEEDS_DOWNLOAD, needsDl.mode)
        assertEquals(0f, needsDl.ringProgress, 0.001f)

        val downloading = FloatingAudioLogic.resolve(
            isAudioSupported = true,
            currentBook = "Genesis",
            currentChapter = 1,
            activeAudioCode = "KJV",
            currentChapterKey = "KJV:genesis:1",
            isCurrentDownloaded = false,
            currentDownloadState = ChapterAudioDownloadState.Downloading(500L, 1000L),
            manifestMeta = null,
            localRow = null,
            playerState = AudioPlayerUiState(),
        )
        assertEquals(FloatingAudioMode.DOWNLOADING, downloading.mode)
        assertEquals(0.5f, downloading.ringProgress, 0.001f)

        val playing = FloatingAudioLogic.resolve(
            isAudioSupported = true,
            currentBook = "Genesis",
            currentChapter = 1,
            activeAudioCode = "KJV",
            currentChapterKey = "KJV:genesis:1",
            isCurrentDownloaded = true,
            currentDownloadState = ChapterAudioDownloadState.Downloaded,
            manifestMeta = null,
            localRow = null,
            playerState = AudioPlayerUiState(
                activeChapterKey = "KJV:genesis:1",
                versionCode = "KJV",
                book = "Genesis",
                chapter = 1,
                isPlaying = true,
                positionMs = 30_000L,
                durationMs = 120_000L,
                speed = 1.25f,
            ),
        )
        assertEquals(FloatingAudioMode.PLAYING, playing.mode)
        assertEquals(0.25f, playing.ringProgress, 0.001f)
        assertEquals("1.25x", playing.speedLabel)
    }
}
