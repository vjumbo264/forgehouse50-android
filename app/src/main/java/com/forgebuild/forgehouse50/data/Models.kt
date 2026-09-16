package com.forgebuild.forgehouse50.data

import kotlinx.serialization.Serializable
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
    val join_window_days: Int = 10,
    val join_window_closes_at: String? = null,
    val registration_open: Boolean = false,
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
    val email: String = "",
    val role: String = "member",
    val avatar_id: String? = null,
    val created_at: String? = null,
    val days_completed: Int = 0,
    val chapters: Int = 0,
    val reading_seconds: Long = 0,
    val points: Long = 0,
    val notes_count: Int = 0,
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
    val join_window_days: Int = 10,
    val join_window_closes_at: String? = null,
    val registration_open: Boolean = false,
    val programme_end_date: String? = null,
    val concluded: Boolean = false,
    val final_snapshot_at: String? = null,
    val today: String = "",
    val eligible_participants: Int = 0,
    val end_date_preview: String? = null,
)

@Serializable
data class GenericOk(val ok: Boolean = false, val error: String? = null, val points_awarded: Int = 0, val message: String? = null)

// ── Bundled KJV asset (bible/kjv.json.gz) — compact field names to keep the
// bundled file small; decoded then mapped to the full Verse/Intro DTOs. ────
@Serializable
data class BundledVerse(val n: Int, val t: String, val f: List<String> = emptyList())

@Serializable
data class BundledIntro(val s: Int = 0, val e: Int = 0, val t: String = "")

@Serializable
data class BundledChapter(val v: List<BundledVerse> = emptyList(), val i: List<BundledIntro> = emptyList())
