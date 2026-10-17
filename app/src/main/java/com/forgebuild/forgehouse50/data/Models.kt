package com.forgebuild.forgehouse50.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * combined_fixes_v1 / Issue 3 (root cause of "Request failed (200)"): rows
 * returned straight from D1 (reading_progress, etc.) carry SQLite INTEGER
 * 0/1 for booleans, not JSON true/false. kotlinx.serialization refuses 0/1
 * for Boolean, so a SUCCESSFUL HTTP 200 body failed to decode and the old
 * error path re-labeled that parse failure as "Request failed (200)". This
 * serializer accepts both shapes.
 */
object IntBooleanSerializer : JsonTransformingSerializer<Boolean>(Boolean.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element is JsonPrimitive) {
            element.booleanOrNull?.let { return JsonPrimitive(it) }
            element.intOrNull?.let { return JsonPrimitive(it != 0) }
        }
        return element
    }
}

// DTOs matching the ForgeHouse 50 Pages Functions API exactly
// (source of truth: github.com/vjumbo264/forgehouse50, functions-api sources).

@Serializable
data class ProgrammeInfo(
    val started: Boolean = false,
    val start_at: String? = null,
    val start_date: String? = null,
    val start_formatted: String? = null,
    val join_window_days: Int = 10,
    val join_window_closes_at: String? = null,
    val join_window_last_day: String? = null,
    val join_window_last_day_formatted: String? = null,
    val join_window_closes_label: String? = null,
    val days_remaining: Int = 0,
    val registration_open: Boolean = false,
    val status_summary: String? = null,
    val signup_label: String? = null,
    val programme_end_date: String? = null,
    val concluded: Boolean = false,
    val final_snapshot_at: String? = null,
    val today: String? = null,
)

@Serializable
data class ConfigResponse(
    val whatsapp_group_url: String = "",
    val programme_start: String = "",
    val app_name: String = "ForgeHouse 50",
    val email_provider: String = "Brevo",
    val programme: ProgrammeInfo = ProgrammeInfo(),
)

@Serializable
data class Translation(
    val id: String,
    val code: String = "",
    val name: String = "",
    val attribution: String = "",
    val versewell: Boolean = false,
    val has_footnotes: Boolean = false,
    val has_intros: Boolean = false,
)

@Serializable
data class TranslationsResponse(
    val translations: List<Translation> = emptyList(),
    val versewell_live: Boolean = false,
)

@Serializable
data class SignupResponse(
    val ok: Boolean = false,
    val user_id: String? = null,
    val email: String? = null,
    val email_sent: Boolean = false,
    val email_error: String? = null,
    val error: String? = null,
    val needs_verification: Boolean = false,
)

@Serializable
data class AdminLoginResponse(
    val ok: Boolean = false,
    val token: String? = null,
    val role: String? = null,
    val username: String? = null,
    val error: String? = null,
    val locked: Boolean = false,
    val retry_after_seconds: Int? = null,
)

@Serializable
data class LoginResponse(
    val ok: Boolean = false,
    val name: String? = null,
    val surname: String? = null,
    val role: String? = null,
    val error: String? = null,
    val needs_verification: Boolean = false,
    val email: String? = null,
)

@Serializable
data class UserStats(
    val days_completed: Int = 0,
    val chapters: Int = 0,
    val reading_seconds: Long = 0,
    val notes_total: Int = 0,
    val observations: Int = 0,
    val questions: Int = 0,
    val audio_sessions: Int = 0,
    val streak_current: Int = 0,
    val streak_longest: Int = 0,
    val points: Long = 0,
)

@Serializable
data class Badge(
    val id: String,
    val name: String = "",
    val description: String = "",
    val awarded_at: String? = null,
)

@Serializable
data class MeProgramme(
    val started: Boolean = false,
    val concluded: Boolean = false,
    val programme_end_date: String? = null,
)

@Serializable
data class MeResponse(
    val id: String,
    val email: String = "",
    val name: String = "",
    val surname: String = "",
    val avatar_url: String? = null,
    val avatar_id: String? = null,
    val picture_version: Int? = null,
    val role: String = "member",
    val email_verified: Boolean = false,
    val created_at: String? = null,
    val stats: UserStats = UserStats(),
    val badges: List<Badge> = emptyList(),
    val programme: MeProgramme = MeProgramme(),
)

@Serializable
data class Assignment(
    val book: String,
    val chapter_start: Int,
    val chapter_end: Int,
    val chapter_count: Int = 0,
    val est_minutes: Int = 0,
)

@Serializable
data class DayAssignment(
    val day_number: Int,
    val assignments: List<Assignment> = emptyList(),
    val chapter_count: Int = 0,
    val est_minutes: Int = 0,
    val summary: String = "",
)

@Serializable
data class TodayBlock(
    val day_number: Int,
    @Serializable(with = IntBooleanSerializer::class) val completed: Boolean = false,
    val reading_seconds: Long = 0,
    val assignment: DayAssignment? = null,
)

@Serializable
data class NextReadingDay(
    val day_number: Int,
    val date: String = "",
    val assignment: DayAssignment? = null,
)

@Serializable
data class TodayResponse(
    val date: String = "",
    val start_date: String? = null,
    val elapsed_days: Int = 0,
    val status: String = "rest",
    val is_reading_day: Boolean = false,
    val today: TodayBlock? = null,
    val next_reading_day: NextReadingDay? = null,
    val stats: UserStats = UserStats(),
    val programme: ProgrammeTotals = ProgrammeTotals(),
)

@Serializable
data class ProgrammeTotals(val total_days: Int = 50, val total_chapters: Int = 260)

@Serializable
data class DayProgress(
    @Serializable(with = IntBooleanSerializer::class) val completed: Boolean = false,
    val completed_at: String? = null,
    val reading_seconds: Long = 0,
    val chapters_read: Int = 0,
)

@Serializable
data class DayResponse(
    val day_number: Int,
    val date: String = "",
    val assignments: List<Assignment> = emptyList(),
    val chapter_count: Int = 0,
    val est_minutes: Int = 0,
    val progress: DayProgress = DayProgress(),
)

@Serializable
data class Verse(
    val chapter: Int,
    val verse: Int,
    val text: String,
    val footnotes: List<String> = emptyList(),
    val heading_before: String? = null,
)

@Serializable
data class Intro(
    val chapter: Int,
    val start_verse: Int = 0,
    val end_verse: Int = 0,
    val text: String = "",
)

@Serializable
data class PassageResponse(
    val reference: String = "",
    val verses: List<Verse> = emptyList(),
    val intros: List<Intro> = emptyList(),
    val attribution: String = "",
    val translation: String = "",
    val source: String = "",
)


@Serializable
data class QuizQuestion(val index: Int, val q: String, val options: List<String>)

@Serializable
data class QuizAttempt(
    val score: Int = 0,
    val total: Int = 0,
    val passed: Boolean = false,
    val at: String? = null,
    val final: Boolean = true,
)

@Serializable
data class QuizResponse(
    val day_number: Int,
    val date: String? = null,
    val questions: List<QuizQuestion> = emptyList(),
    val total: Int = 0,
    val quiz_points_possible: Int = 35, // combined_fixes_v1 Issue 11: ×7
    val info_only: Boolean = true,
    val completed: Boolean = false,
    val prev_completed: Boolean = false,
    val elapsed_days: Int = 0,
    val can_attempt: Boolean = false,
    val block_reason: String? = null,
    val attempt: QuizAttempt? = null,
)

@Serializable
data class QuizSubmitResponse(
    val ok: Boolean = false,
    val day_number: Int = 0,
    val score: Int = 0,
    val total: Int = 0,
    val passed: Boolean = false,
    val final: Boolean = true,
    val quiz_points: Int = 0,
    val quiz_points_possible: Int = 35, // combined_fixes_v1 Issue 11: ×7
    val message: String = "",
    val error: String? = null,
)

@Serializable
data class NoteReply(
    val text: String = "",
    val created_at: String? = null,
)

@Serializable
data class Note(
    val id: String = "",
    val note_type: String = "observation",
    val body: String = "",
    val book: String = "",
    val chapter: Int = 0,
    val verse: Int? = null,
    val day_number: Int? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val sent_to_leaders: Boolean = false,
    val inbox_status: String? = null,
    val show_name: Boolean = false,
    val reply: NoteReply? = null,
)

@Serializable
data class CommunityPost(
    val id: String = "",
    val type: String = "observation",
    val reference: String = "",
    val text: String = "",
    val author_name: String = "Anonymous",
    val avatar_id: String? = null,
    val is_anonymous: Boolean = true,
    val created_at: String = "",
    val reply: CommunityReply = CommunityReply(),
)

@Serializable
data class CommunityReply(
    val text: String = "",
    val created_at: String? = null,
)

@Serializable
data class CommunityPostsResponse(
    val ok: Boolean = true,
    val posts: List<CommunityPost> = emptyList(),
    val next_cursor: String? = null,
    val has_more: Boolean = false,
)

@Serializable
data class CommunityLatestResponse(
    val ok: Boolean = true,
    val post: CommunityPost? = null,
)

@Serializable
data class LeaderInboxItem(
    val id: String = "",
    val note_id: String = "",
    val author_user_id: String = "",
    val author_full_name: String = "",
    val author_email: String? = null,
    val type: String = "observation",
    val reference: String = "",
    val text: String = "",
    val show_name: Boolean = false,
    val status: String = "new",
    val created_at: String = "",
    val updated_at: String = "",
    val version_count: Int = 0,
    val reply: LeaderInboxReply? = null,
)

@Serializable
data class LeaderInboxReply(
    val id: String = "",
    val text: String = "",
    val admin_id: String = "",
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class LeaderInboxResponse(
    val ok: Boolean = true,
    val items: List<LeaderInboxItem> = emptyList(),
    val page: Int = 1,
    val limit: Int = 30,
    val total: Int = 0,
    val has_more: Boolean = false,
)

@Serializable
data class NotesResponse(val notes: List<Note> = emptyList())

@Serializable
data class ProgressDay(
    val day_number: Int,
    val date: String = "",
    val assignment: String = "",
    val chapter_count: Int = 0,
    @Serializable(with = IntBooleanSerializer::class) val completed: Boolean = false,
    val quiz_taken: Boolean = false,
    val quiz_score: Int? = null,
    val quiz_total: Int? = null,
    val quiz_passed_info: Boolean = false,
    val is_future: Boolean = false,
    val reading_seconds: Long = 0,
    val notes_count: Int = 0,
)

@Serializable
data class ProgressTotals(
    val chapters_completed: Int = 0,
    val percent_completed: Double = 0.0,
    val reading_days_completed: Int = 0,
    val streak_current: Int = 0,
    val streak_longest: Int = 0,
    val reading_seconds_total: Long = 0,
)

@Serializable
data class ProgressResponse(
    val start_date: String? = null,
    val days: List<ProgressDay> = emptyList(),
    val totals: ProgressTotals = ProgressTotals(),
    val programme: ProgrammeTotals = ProgrammeTotals(),
)

@Serializable
data class LeaderboardEntry(
    val rank: Int,
    val user_id: String = "",
    val display_name: String = "Member",
    val avatar_url: String? = null,
    val avatar_id: String? = null,
    val picture_version: Int? = null,
    val value: Double = 0.0,
    val streak_current: Int? = null,
    val streak_longest: Int? = null,
    val elapsed_days: Int? = null,
    val total_points: Long? = null,
)

@Serializable
data class LeaderboardResponse(
    val category: String = "overall",
    val entries: List<LeaderboardEntry> = emptyList(),
    val eligible_count: Int = 0,
)

@Serializable
data class FinalRanking(
    val rank: Int,
    val user_id: String = "",
    val display_name: String = "Member",
    val avatar_id: String? = null,
    val picture_version: Int? = null,
    val total_points: Long = 0,
    val finished_at: String? = null,
)

@Serializable
data class FinalResultsMe(
    val rank: Int = 0,
    val total_points: Long = 0,
    val celebration_pending: Boolean = false,
)

@Serializable
data class FinalResultsResponse(
    val concluded: Boolean = false,
    val started: Boolean = false,
    val run_id: String? = null,
    val snapshot_at: String? = null,
    val programme_end_date: String? = null,
    val rankings: List<FinalRanking> = emptyList(),
    val me: FinalResultsMe? = null,
)

@Serializable
data class Participant(
    val id: String,
    val name: String = "",
    val surname: String = "",
    val full_name: String = "",
    val short_tag: String = "",
    val status: String = "member",
    val email: String = "",
    val role: String = "member",
    val avatar_id: String? = null,
    val picture_version: Int? = null,
    val created_at: String? = null,
    val days_completed: Int = 0,
    val chapters: Int = 0,
    val reading_seconds: Long = 0,
    val points: Long = 0,
    val notes_count: Int = 0,
) {
    val displayName: String
        get() = full_name.ifBlank {
            listOf(name, surname).filter { it.isNotBlank() }.joinToString(" ").ifBlank { name.ifBlank { "Member" } }
        }
    val displayTag: String
        get() = if (short_tag.isNotBlank()) "#$short_tag" else "#${id.takeLast(4)}"
    val isInactive: Boolean
        get() = points == 0L && days_completed == 0 && notes_count == 0
}

@Serializable
data class ParticipantDayRow(
    val day_number: Int,
    val date: String? = null,
    val status: String = "pending",
    val completed: Boolean = false,
    val completed_at: String? = null,
    val reading_seconds: Long = 0,
    val quiz_score: String? = null,
    val points_earned: Int = 0,
    val is_latest_completed: Boolean = false,
) {
    val readingTimeFormatted: String
        get() {
            val hrs = reading_seconds / 3600
            val mins = (reading_seconds % 3600) / 60
            val secs = reading_seconds % 60
            return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m ${secs}s"
        }
}

@Serializable
data class ParticipantDetail(
    val id: String,
    val name: String = "",
    val surname: String = "",
    val full_name: String = "",
    val email: String = "",
    val role: String = "member",
    val avatar_id: String? = null,
    val picture_version: Int? = null,
    val created_at: String? = null,
    val programme_start_date: String? = null,
    val total_points: Long = 0,
    val days_completed: Int = 0,
    val total_reading_seconds: Long = 0,
    val average_reading_seconds_per_completed_day: Long = 0,
    val notes_count: Int = 0,
    val observations_count: Int = 0,
    val questions_count: Int = 0,
    val latest_completed_day: Int? = null,
    val days: List<ParticipantDayRow> = emptyList(),
) {
    val displayName: String
        get() = full_name.ifBlank {
            listOf(name, surname).filter { it.isNotBlank() }.joinToString(" ").ifBlank { name.ifBlank { "Member" } }
        }

    val totalReadingTimeFormatted: String
        get() {
            val hrs = total_reading_seconds / 3600
            val mins = (total_reading_seconds % 3600) / 60
            return "${hrs} h ${mins} m read"
        }

    val avgReadingTimeFormatted: String
        get() {
            val mins = average_reading_seconds_per_completed_day / 60
            val secs = average_reading_seconds_per_completed_day % 60
            return "${mins}m ${secs}s / day"
        }
}

@Serializable
data class ParticipantDetailResponse(
    val participant: ParticipantDetail? = null,
)

@Serializable
data class ResetLatestDayResponse(
    val ok: Boolean = false,
    val action: String = "",
    val target_user_id: String = "",
    val target_display_name: String = "",
    val day_number: Int = 0,
    val points_reversed: Int = 0,
    val previous_reading_seconds: Long = 0,
    val previous_quiz_score: String? = null,
    val error: String? = null,
)

@Serializable
data class BatchDeleteResultItem(
    val id: String = "",
    val status: String = "",
    val reason: String? = null,
    val error: String? = null,
    val display_name: String? = null,
)

@Serializable
data class BatchDeleteResponse(
    val ok: Boolean = false,
    val total: Int = 0,
    val deleted_count: Int = 0,
    val skipped_count: Int = 0,
    val failed_count: Int = 0,
    val results: List<BatchDeleteResultItem> = emptyList(),
    val error: String? = null,
)

@Serializable
data class ParticipantsResponse(val participants: List<Participant> = emptyList())

@Serializable
data class AdminStats(
    val total_participants: Int = 0,
    val active_participants: Int = 0,
    val completed_today: Int = 0,
    val average_completion_percent: Double = 0.0,
    val total_chapters_completed: Int = 0,
    val users_behind_schedule: Int = 0,
    val quiz_attempts_total: Int = 0,
    val quiz_avg_score_pct: Double = 0.0,
    val users_quizzed: Int = 0,
    val today_date: String = "",
)

@Serializable
data class AdminProgramme(
    val started: Boolean = false,
    val start_at: String? = null,
    val start_date: String? = null,
    val start_formatted: String? = null,
    val join_window_days: Int = 10,
    val join_window_closes_at: String? = null,
    val join_window_last_day: String? = null,
    val join_window_last_day_formatted: String? = null,
    val join_window_closes_label: String? = null,
    val days_remaining: Int = 0,
    val registration_open: Boolean = false,
    val status_summary: String? = null,
    val signup_label: String? = null,
    val programme_end_date: String? = null,
    val concluded: Boolean = false,
    val final_snapshot_at: String? = null,
    val today: String = "",
    val eligible_participants: Int = 0,
    val end_date_preview: String? = null,
    val reset_enabled: Boolean = true,
    val reset_disabled_reason: String? = null,
)

@Serializable
data class GenericOk(val ok: Boolean = false, val error: String? = null, val points_awarded: Int = 0, val message: String? = null)

// ── Bundled KJV asset (bible/kjv.json.gz) — compact field names to keep the
// bundled file small; decoded then mapped to the full Verse/Intro DTOs. ────
object BundledFootnoteListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = ListSerializer(String.serializer()).descriptor

    override fun serialize(encoder: Encoder, value: List<String>) {
        ListSerializer(String.serializer()).serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder ?: return emptyList()
        val element = jsonDecoder.decodeJsonElement()
        if (element !is JsonArray) return emptyList()
        val result = mutableListOf<String>()
        for (item in element) {
            when (item) {
                is JsonPrimitive -> {
                    if (item.isString) result.add(item.content)
                }
                is JsonObject -> {
                    val marker = item["marker"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
                    val text = item["text"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
                    val note = if (marker.isNotEmpty() && text.isNotEmpty()) ". " else text.ifEmpty { marker }
                    if (note.isNotEmpty()) result.add(note)
                }
                else -> {}
            }
        }
        return result
    }
}

object BundledVerseListSerializer : KSerializer<List<BundledVerse>> {
    override val descriptor: SerialDescriptor = ListSerializer(BundledVerse.serializer()).descriptor

    override fun serialize(encoder: Encoder, value: List<BundledVerse>) {
        ListSerializer(BundledVerse.serializer()).serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<BundledVerse> {
        val jsonDecoder = decoder as? JsonDecoder ?: return emptyList()
        val element = jsonDecoder.decodeJsonElement()
        if (element !is JsonArray) return emptyList()
        val result = mutableListOf<BundledVerse>()
        for (item in element) {
            try {
                val verse = jsonDecoder.json.decodeFromJsonElement(BundledVerse.serializer(), item)
                result.add(verse)
            } catch (e: Throwable) {
                // Log and skip bad verse so one bad verse never crashes a chapter or download
            }
        }
        return result
    }
}

@Serializable
data class BundledVerse(
    val n: Int,
    val t: String,
    @Serializable(with = BundledFootnoteListSerializer::class)
    val f: List<String> = emptyList(),
    val h: String? = null
)

@Serializable
data class BundledIntro(val s: Int = 0, val e: Int = 0, val t: String = "")

@Serializable
data class BundledChapter(
    @Serializable(with = BundledVerseListSerializer::class)
    val v: List<BundledVerse> = emptyList(),
    val i: List<BundledIntro> = emptyList()
)

// ── testing_phase_and_reader_fixes_v1 / ISSUE 3: one-file-per-translation
// scripture bundles hosted as static assets at {BASE}/bundles/<code>.json.gz ──
@Serializable
data class BundleEntry(
    val id: String,
    val code: String = "",
    val name: String = "",
    val url: String = "",
    val bytes: Long = 0,
    val chapters: Int = 0,
)

@Serializable
data class BundleManifest(
    val generated: String = "",
    val bundles: List<BundleEntry> = emptyList(),
)

// ── Item 5: Admin Observation and Question Notes ────────────────────────
@Serializable
data class AdminNoteItem(
    val id: String = "",
    val user_id: String = "",
    val author_full_name: String = "",
    val author_name: String = "",
    val type: String = "observation",
    val note_type: String = "observation",
    val text: String = "",
    val body: String = "",
    val book: String = "",
    val chapter: Int = 1,
    val verse: Int? = null,
    val reference: String = "",
    val day: Int? = null,
    val day_number: Int? = null,
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class AdminNotesResponse(
    val ok: Boolean = true,
    val notes: List<AdminNoteItem> = emptyList(),
    val page: Int = 1,
    val limit: Int = 25,
    val total: Int = 0,
    val has_more: Boolean = false,
)

@Serializable
data class Announcement(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val status: String = "active",
    val created_by: String = "",
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class AnnouncementLatestResponse(
    val ok: Boolean = true,
    val announcement: Announcement? = null,
)

@Serializable
data class AnnouncementsResponse(
    val ok: Boolean = true,
    val announcements: List<Announcement> = emptyList(),
    val next_cursor: String? = null,
    val has_more: Boolean = false,
)
