package com.uploadgo.app.data.temp

import android.content.Context
import com.uploadgo.app.util.AppConstants
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the app-private directory used for extracted ZIP contents. Everything
 * lives under `cacheDir/extracted`, which is outside the user's media library
 * and can never touch the user's original files.
 */
class TempFileManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val rootDir: File get() = File(context.cacheDir, "extracted")

    fun extractionDirFor(zipId: String): File =
        File(rootDir, zipId.sanitizeDirName())

    fun ensureRoot() {
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    /** Total bytes currently used by extracted contents. */
    fun totalSizeBytes(): Long =
        rootDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }

    /** Delete everything extracted so far. Never touches the user's files. */
    fun clearAll() {
        rootDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /**
     * Schedule deferred cleanup. The receiving app may still be reading the
     * URIs right after the Sharesheet is dismissed, so deletion is delayed by a
     * grace period rather than happening immediately.
     */
    fun scheduleCleanup(dir: File, delayMillis: Long = AppConstants.SHARE_GRACE_MILLIS) {
        scope.launch {
            delay(delayMillis)
            runCatching { if (dir.exists()) dir.deleteRecursively() }
        }
    }

    /** Remove extracted files older than the retention window (on app start). */
    fun cleanupStale(maxAgeMillis: Long = AppConstants.STALE_TEMP_MILLIS) {
        val now = System.currentTimeMillis()
        rootDir.listFiles()?.forEach { f ->
            if (now - f.lastModified() > maxAgeMillis) runCatching { f.deleteRecursively() }
        }
    }

    private fun String.sanitizeDirName(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_")
}
