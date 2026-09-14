package com.forgebuild.engine.files

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.io.IOException

/**
 * ForgeBuild explicit, permission-scoped save/download flow (Correction Round 2, Fix 4).
 *
 * PURPOSE: saving files must be a flow the APP controls — its own UI, its own
 * confirmation, a destination the user explicitly picks. NEVER use
 * [android.app.DownloadManager] or otherwise let a file silently land in the
 * system Downloads app outside the app's control.
 *
 * DEFAULT APPROACH — Storage Access Framework (SAF). It is user-directed and
 * permission-scoped by design: no runtime storage permission is needed at all
 * on modern Android, because the user grants access to exactly the file/tree
 * they pick:
 *
 *   - [registerCreateDocument] → `ACTION_CREATE_DOCUMENT`: the system picker
 *     asks WHERE and under WHAT NAME to save; the returned Uri is writable
 *     by this app (optionally persistable via [takePersistablePermission]).
 *   - [registerOpenTree] → `ACTION_OPEN_DOCUMENT_TREE`: the user grants a
 *     whole folder (e.g. for an app that saves many files over time); persist
 *     the grant and create documents inside it afterwards.
 *
 * LEGACY FALLBACK only where an app's minSdk genuinely requires it
 * (pre-Android-10 direct-path saves): request WRITE_EXTERNAL_STORAGE via the
 * Engine's PermissionWiring (EnginePermission.STORAGE) and write to
 * app-specific external dirs. Do not request MANAGE_EXTERNAL_STORAGE for
 * saving — it is reserved for genuine file-manager-class apps.
 *
 * Typical wiring (single-activity Compose app):
 *
 *   class SaveCoordinator(activity: ComponentActivity) {
 *       private var pending: (() -> ByteArray)? = null
 *       val saver = SafeSave.registerCreateDocument(activity, "text/plain") { uri ->
 *           if (uri == null) return@registerCreateDocument      // user cancelled — nothing was saved
 *           val bytes = pending?.invoke() ?: return@registerCreateDocument
 *           SafeSave.writeBytes(activity, uri, bytes)           // app shows its own "Saved" confirmation
 *       }
 *       fun export(data: () -> ByteArray) { pending = data; saver.launch("entry-export.txt") }
 *   }
 */
object SafeSave {

    /**
     * Register an ACTION_CREATE_DOCUMENT flow. [mimeType] filters the document
     * kind; [onResult] receives the chosen writable Uri, or null if the user
     * cancelled (then NOTHING was saved — honor that silently).
     * Launch it with the suggested file name: `launcher.launch("export.csv")` —
     * the user may rename it in the picker.
     */
    fun registerCreateDocument(
        activity: ComponentActivity,
        mimeType: String,
        onResult: (Uri?) -> Unit,
    ): ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.CreateDocument(mimeType), onResult)

    /**
     * Register an ACTION_OPEN_DOCUMENT_TREE flow for apps that save repeatedly
     * into one user-chosen folder. Persist the returned grant with
     * [takePersistablePermission] so the folder stays usable across restarts.
     */
    fun registerOpenTree(
        activity: ComponentActivity,
        onResult: (Uri?) -> Unit,
    ): ActivityResultLauncher<Uri?> =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree(), onResult)

    /** Persist a SAF Uri grant across process restarts (call for tree/document Uris the app must reuse). */
    fun takePersistablePermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    /**
     * Write bytes to a SAF Uri. Wraps the stream correctly and throws
     * [IOException] on failure so the app can show its own error UI.
     */
    @Throws(IOException::class)
    fun writeBytes(context: Context, uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(bytes)
            out.flush()
        } ?: throw IOException("could not open $uri for writing")
    }

    /**
     * Stream-copy helper for larger payloads (e.g. a downloaded response body)
     * into a SAF Uri without buffering the whole file in memory.
     */
    @Throws(IOException::class)
    fun writeStream(context: Context, uri: Uri, source: java.io.InputStream) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            source.use { it.copyTo(out, bufferSize = 64 * 1024) }
            out.flush()
        } ?: throw IOException("could not open $uri for writing")
    }
}
