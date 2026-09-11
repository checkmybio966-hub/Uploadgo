package com.uploadgo.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.uploadgo.app.data.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}
