package com.forgebuild.forgehouse50.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Thin typed client for the existing ForgeHouse 50 Cloudflare Pages
 * Functions API (base https://forgehouse50.pages.dev/api). Route paths,
 * field names and error shapes match the functions API sources in the web repo
 * exactly — this client reimplements none of the backend logic.
 *
 * Auth: the backend issues an HttpOnly `fh50_session` cookie; the Android
 * client keeps the token in [SessionStore] (EncryptedSharedPreferences) and
 * sends it back as a Cookie header on every request.
 */
class ApiClient(private val session: SessionStore) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        // leaderboard_scripture_icon_fix_v1 / ISSUE 2: bounded timeouts so a
        // genuinely hung/slow request fails fast and the UI shows a clear error
        // instead of an infinite loading spinner. 25s request ceiling is ample
        // for the largest multi-chapter passage responses (~1.5s live median).
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 25_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 25_000
        }
        engine { config { followRedirects(true) } }
    }

    private fun HttpRequestBuilder.auth() {
        session.sessionToken?.let { header(HttpHeaders.Cookie, "fh50_session=$it") }
        header(HttpHeaders.Accept, "application/json")
    }

    private suspend inline fun <reified T> get(path: String, crossinline params: HttpRequestBuilder.() -> Unit = {}): T {
        val res: HttpResponse = client.get("$BASE$path") { auth(); params() }
        return decode(res)
    }

    private suspend inline fun <reified T> post(path: String, body: JsonObject): T {
        val res: HttpResponse = client.post("$BASE$path") {
            auth(); contentType(ContentType.Application.Json); setBody(body.toString())
        }
        return decode(res)
    }

    private suspend inline fun <reified T> put(path: String, body: JsonObject): T {
        val res: HttpResponse = client.put("$BASE$path") {
            auth(); contentType(ContentType.Application.Json); setBody(body.toString())
        }
        return decode(res)
    }

    private suspend inline fun <reified T> del(path: String, body: JsonObject? = null): T {
        val res: HttpResponse = client.delete("$BASE$path") {
            auth()
            if (body != null) { contentType(ContentType.Application.Json); setBody(body.toString()) }
        }
        return decode(res)
    }

    private suspend inline fun <reified T> decode(res: HttpResponse): T {
        val text = res.bodyAsText()
        val parsed = runCatching { json.decodeFromString<T>(text) }
        // Capture the session token from any Set-Cookie header that carries it.
        res.headers.getAll(HttpHeaders.SetCookie)?.forEach { cookie ->
            Regex("fh50_session=([a-f0-9]{64})").find(cookie)?.groupValues?.get(1)?.let {
                session.sessionToken = it
            }
        }
        if (res.status.value in 200..299) {
            if (parsed.isSuccess) return parsed.getOrThrow()
            // combined_fixes_v1 / Issue 3: a 2xx that failed to decode is a
            // RESPONSE-SHAPE problem, reported as such — never mislabeled as
            // a failed request carrying the success status code.
            throw ApiException(res.status.value,
                "Could not read the server response (HTTP ${res.status.value}). Please update the app if this persists.")
        }
        // Genuine failure: surface the backend's own message + the REAL status.
        val msg = runCatching {
            json.decodeFromString<GenericOk>(text).error
        }.getOrNull()
        throw ApiException(res.status.value, msg ?: "Request failed (HTTP ${res.status.value})")
    }

    class ApiException(val status: Int, message: String) : Exception(message)

    // ── Public endpoints ──────────────────────────────────────────────────
    suspend fun config(): ConfigResponse = get("/config")
    suspend fun programme(): ProgrammeInfo = get("/programme")
    suspend fun translations(): TranslationsResponse = get("/translations")

    // ── Auth ──────────────────────────────────────────────────────────────
    suspend fun signup(email: String, password: String, name: String, surname: String, avatarId: String): SignupResponse =
        post("/auth/signup", buildJsonObject {
            put("email", email); put("password", password); put("name", name); put("surname", surname); put("avatar_id", avatarId)
        })

    suspend fun verify(email: String, code: String): GenericOk =
        post("/auth/verify", buildJsonObject { put("email", email); put("code", code) })

    suspend fun resend(email: String): GenericOk =
        post("/auth/resend", buildJsonObject { put("email", email) })

    suspend fun login(email: String, password: String): LoginResponse =
        post("/auth/login", buildJsonObject { put("email", email); put("password", password) })

    suspend fun logout(): GenericOk = post("/auth/logout", buildJsonObject {})

    suspend fun me(): MeResponse = get("/auth/me")

    suspend fun setAvatar(avatarId: String): GenericOk =
        post("/auth/avatar", buildJsonObject { put("avatar_id", avatarId) })

    // ── Reading ───────────────────────────────────────────────────────────
    suspend fun today(): TodayResponse = get("/today")

    suspend fun day(n: Int): DayResponse = get("/read/day") { parameter("n", n) }

    suspend fun passage(book: String, cs: Int, ce: Int, translation: String): PassageResponse =
        get("/read/passage") {
            parameter("book", book); parameter("chapter_start", cs)
            parameter("chapter_end", ce); parameter("translation", translation)
        }

    suspend fun audioAvailability(book: String, cs: Int, ce: Int, translation: String): AudioAvailability =
        get("/read/audio_availability") {
            parameter("book", book); parameter("chapter_start", cs)
            parameter("chapter_end", ce); parameter("translation", translation)
        }

    suspend fun completeDay(n: Int): GenericOk =
        post("/read/complete", buildJsonObject { put("day_number", n) })

    suspend fun markAudio(n: Int): GenericOk =
        post("/read/audio", buildJsonObject { put("day_number", n) })

    suspend fun addReadingTime(n: Int, seconds: Int): GenericOk =
        post("/read/time", buildJsonObject { put("day_number", n); put("seconds", seconds) })

    // ── Quiz ──────────────────────────────────────────────────────────────
    suspend fun quiz(day: Int): QuizResponse = get("/quiz/$day")

    suspend fun submitQuiz(day: Int, answers: List<Int>): QuizSubmitResponse {
        val arr = kotlinx.serialization.json.buildJsonArray { answers.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
        val res: HttpResponse = client.post("$BASE/quiz/submit") {
            auth(); contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("day_number", day); put("answers", arr) }.toString())
        }
        return decode(res)
    }

    // ── Notes ─────────────────────────────────────────────────────────────
    suspend fun notes(q: String = "", type: String = "", day: Int = 0): NotesResponse =
        get("/notes") {
            if (q.isNotBlank()) parameter("q", q)
            if (type.isNotBlank()) parameter("type", type)
            if (day in 1..50) parameter("day", day)
        }

    suspend fun createNote(noteType: String, body: String, book: String, chapter: Int, verse: Int?, dayNumber: Int?): GenericOk =
        post("/notes", buildJsonObject {
            put("note_type", noteType); put("body", body); put("book", book); put("chapter", chapter)
            verse?.let { put("verse", it) }; dayNumber?.let { put("day_number", it) }
        })

    suspend fun updateNote(id: String, noteType: String, body: String): GenericOk =
        put("/notes/$id", buildJsonObject { put("note_type", noteType); put("body", body) })

    suspend fun deleteNote(id: String): GenericOk = del("/notes/$id")

    // ── Progress / leaderboard / finals ───────────────────────────────────
    suspend fun progress(): ProgressResponse = get("/progress")

    suspend fun leaderboard(category: String = "overall"): LeaderboardResponse =
        get("/leaderboard") { parameter("category", category) }

    suspend fun finalResults(): FinalResultsResponse = get("/final_results")

    suspend fun ackFinalResults(): GenericOk =
        post("/final_results", buildJsonObject { put("ack", true) })

    // ── Admin ─────────────────────────────────────────────────────────────
    suspend fun adminParticipants(): ParticipantsResponse = get("/admin/participants")
    suspend fun adminStats(): AdminStats = get("/admin/stats")
    suspend fun adminProgramme(): AdminProgramme = get("/admin/programme")

    suspend fun adminAssignment(day: Int, book: String, cs: Int, ce: Int): GenericOk =
        post("/admin/manage", buildJsonObject {
            put("action", "assignment"); put("day_number", day); put("book", book)
            put("chapter_start", cs); put("chapter_end", ce)
        })

    suspend fun adminBadge(userId: String, badgeId: String, grant: Boolean): GenericOk =
        post("/admin/manage", buildJsonObject {
            put("action", "badge"); put("user_id", userId); put("badge_id", badgeId); put("grant", grant)
        })

    suspend fun adminAdjustPoints(userId: String, points: Int, reason: String): GenericOk =
        post("/admin/adjust", buildJsonObject {
            put("action", "points"); put("user_id", userId); put("points", points); put("reason", reason)
        })

    suspend fun adminSetCompletion(userId: String, day: Int, completed: Boolean): GenericOk =
        post("/admin/adjust", buildJsonObject {
            put("action", "completion"); put("user_id", userId); put("day_number", day); put("completed", completed)
        })

    suspend fun adminStartProgramme(confirm: String = "START"): GenericOk =
        post("/admin/programme", buildJsonObject { put("action", "start"); put("confirm", confirm) })

    companion object {
        const val BASE = "https://forgehouse50.pages.dev/api"
    }
}
