package com.forgebuild.engine.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * ForgeBuild cache-first data layer (Correction Round 2, Fix 2).
 *
 * STANDING PATTERN for any generated app that fetches/syncs from a live
 * network source (a repository, an API, anything remote):
 *
 *   1. On screen/app open, render IMMEDIATELY from the local cache —
 *      zero network wait. First launch simply renders the empty state.
 *   2. Then kick off [refresh] in the background; it reconciles the cache
 *      against the live source — additions appear, removals disappear,
 *      changed items update — and the UI recomposes in place.
 *   3. The already-rendered UI NEVER drops back to a loading state because
 *      of a refresh. Show a small non-blocking "updating" affordance instead.
 *
 * Never build a from-scratch "reload everything on every open" behavior.
 *
 * This is a dependency-free reference implementation using an on-disk JSON
 * cache. A building AI may substitute Room or DataStore when the app's data
 * genuinely warrants it (relational queries, large datasets) — that choice
 * must be documented in the app's own ARCHITECTURE.md notes. The contract
 * (instant cached render → background reconcile, never re-flash to loading)
 * is what must be preserved, not this exact class.
 *
 * Typical wiring in a ViewModel:
 *
 *   class FeedViewModel(app: Application) : AndroidViewModel(app) {
 *       private val store = CacheFirstStore<FeedItem>(
 *           app, cacheFileName = "feed.json",
 *           toJson = { JSONObject().put("id", id).put("title", title) },
 *           fromJson = { FeedItem(it.getString("id"), it.getString("title")) },
 *           fetchRemote = { api.listItems() }          // suspending; may throw
 *       )
 *       val items: StateFlow<List<FeedItem>> = store.state
 *       val refreshing: StateFlow<Boolean> = store.refreshing
 *
 *       fun onScreenOpen() {          // called once per screen open
 *           store.loadFromCache()     // synchronous — render is instant
 *           viewModelScope.launch { store.refresh() }   // background
 *       }
 *   }
 *
 * Reconciliation identity: items are matched by their index in the list by
 * default; override [CacheFirstStore.reconcile] or supply [keyOf] for
 * id-based matching (recommended for anything with stable remote ids).
 */
open class CacheFirstStore<T>(
    context: Context,
    cacheFileName: String,
    private val toJson: (T) -> JSONObject,
    private val fromJson: (JSONObject) -> T,
    private val fetchRemote: suspend () -> List<T>,
    private val keyOf: ((T) -> String)? = null,
) {
    private val file: File = File(context.filesDir, cacheFileName)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<List<T>>(emptyList())
    /** Current items. Render this directly; it is populated synchronously by [loadFromCache]. */
    val state: StateFlow<List<T>> = _state

    private val _refreshing = MutableStateFlow(false)
    /** True while a background refresh is in flight — drive a subtle spinner, never a full-screen loader. */
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _lastError = MutableStateFlow<String?>(null)
    /** Last refresh error (null when the last refresh succeeded). Show as a dismissible banner; keep showing cached data. */
    val lastError: StateFlow<String?> = _lastError

    /** Synchronous, network-free: populate [state] from the on-disk cache. Call before first composition. */
    fun loadFromCache() {
        val items: List<T> = runCatching {
            if (!file.exists()) return@runCatching emptyList()
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }.getOrElse {
            Log.w(TAG, "cache unreadable, starting empty: ${it.message}")
            emptyList()
        }
        _state.value = items
    }

    /**
     * Background refresh: fetch the live source, reconcile against the cache
     * (adds/updates/removals), persist, and update [state]. Never throws —
     * failures surface via [lastError] and the cached data stays on screen.
     * Safe to call on every screen open; concurrent calls are serialized.
     */
    suspend fun refresh() {
        if (!mutex.tryLock()) return          // a refresh is already running — do not stack them
        try {
            _refreshing.value = true
            val remote = runCatching { withContext(Dispatchers.IO) { fetchRemote() } }
                .onFailure { _lastError.value = it.message ?: "refresh failed" }
                .getOrElse { return }         // keep showing cache; retry on next open
            _lastError.value = null
            val merged = reconcile(_state.value, remote)
            _state.value = merged
            withContext(Dispatchers.IO) {
                val arr = JSONArray()
                merged.forEach { arr.put(toJson(it)) }
                file.writeText(arr.toString())
            }
        } finally {
            _refreshing.value = false
            mutex.unlock()
        }
    }

    /**
     * Default reconciliation: the remote source is authoritative — additions
     * appear, removals disappear, changed items are replaced by their remote
     * version, and display order follows the remote source. [keyOf] is kept
     * for subclasses that override this to do id-aware merging (e.g.
     * preserving local-only fields on remote-matched items, tombstone
     * semantics); the default needs no key at all.
     */
    protected open fun reconcile(cached: List<T>, remote: List<T>): List<T> = remote

    private companion object { const val TAG = "CacheFirstStore" }
}
