package com.uploadgo.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** What a history entry represents — never a claim about remote delivery. */
enum class HistoryType { PREPARED, SHARED }

/**
 * A locally-stored record of what UploadGo prepared or handed to the Android
 * Sharesheet. It deliberately does not claim Telegram (or any other app)
 * successfully delivered the files.
 */
@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val category: String,
    val itemCount: Int,
    val detail: String,
    val fileNames: String,
    val totalBytes: Long,
)
