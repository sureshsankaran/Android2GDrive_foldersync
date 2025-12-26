package com.foldersync.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.foldersync.data.local.entity.SyncHistoryEntity
import com.foldersync.domain.model.SyncAction
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncHistoryDao {

    @Query("SELECT * FROM sync_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<SyncHistoryEntity>>

    @Query("SELECT * FROM sync_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<SyncHistoryEntity>>

    @Query("SELECT * FROM sync_history WHERE filePath = :filePath ORDER BY timestamp DESC")
    fun getHistoryForFile(filePath: String): Flow<List<SyncHistoryEntity>>

    @Query("SELECT * FROM sync_history WHERE action = :action ORDER BY timestamp DESC")
    fun getHistoryByAction(action: SyncAction): Flow<List<SyncHistoryEntity>>

    @Query("SELECT * FROM sync_history WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getHistorySince(since: Long): List<SyncHistoryEntity>

    @Query("SELECT * FROM sync_history WHERE status = 'ERROR' ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentErrors(limit: Int): Flow<List<SyncHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: SyncHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(histories: List<SyncHistoryEntity>)

    @Query("DELETE FROM sync_history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int

    @Query("DELETE FROM sync_history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM sync_history")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM sync_history WHERE status = 'SUCCESS'")
    suspend fun getSuccessCount(): Int

    @Query("SELECT COUNT(*) FROM sync_history WHERE status = 'ERROR'")
    suspend fun getErrorCount(): Int
}
