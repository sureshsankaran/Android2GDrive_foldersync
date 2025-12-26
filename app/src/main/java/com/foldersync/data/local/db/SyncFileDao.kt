package com.foldersync.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.foldersync.data.local.entity.SyncFileEntity
import com.foldersync.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncFileDao {

    @Query("SELECT * FROM sync_files ORDER BY fileName ASC")
    fun getAllFiles(): Flow<List<SyncFileEntity>>

    @Query("SELECT * FROM sync_files WHERE localPath = :localPath")
    suspend fun getByLocalPath(localPath: String): SyncFileEntity?

    @Query("SELECT * FROM sync_files WHERE driveFileId = :driveFileId")
    suspend fun getByDriveId(driveFileId: String): SyncFileEntity?

    @Query("SELECT * FROM sync_files WHERE syncStatus = :status")
    fun getByStatus(status: SyncStatus): Flow<List<SyncFileEntity>>

    @Query("SELECT * FROM sync_files WHERE syncStatus IN (:statuses)")
    suspend fun getByStatuses(statuses: List<SyncStatus>): List<SyncFileEntity>

    @Query("SELECT * FROM sync_files WHERE syncStatus = 'CONFLICT'")
    fun getConflicts(): Flow<List<SyncFileEntity>>

    @Query("SELECT COUNT(*) FROM sync_files WHERE syncStatus = 'CONFLICT'")
    fun getConflictCount(): Flow<Int>

    @Query("SELECT * FROM sync_files WHERE isDirectory = 0")
    suspend fun getAllFilesOnly(): List<SyncFileEntity>

    @Query("SELECT * FROM sync_files WHERE isDirectory = 1")
    suspend fun getAllDirectories(): List<SyncFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: SyncFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<SyncFileEntity>)

    @Update
    suspend fun update(file: SyncFileEntity)

    @Delete
    suspend fun delete(file: SyncFileEntity)

    @Query("DELETE FROM sync_files WHERE localPath = :localPath")
    suspend fun deleteByLocalPath(localPath: String)

    @Query("DELETE FROM sync_files WHERE driveFileId = :driveFileId")
    suspend fun deleteByDriveId(driveFileId: String)

    @Query("DELETE FROM sync_files")
    suspend fun deleteAll()

    @Query("UPDATE sync_files SET syncStatus = :status WHERE localPath = :localPath")
    suspend fun updateStatus(localPath: String, status: SyncStatus)

    @Query("UPDATE sync_files SET lastSyncTime = :timestamp WHERE localPath = :localPath")
    suspend fun updateLastSyncTime(localPath: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM sync_files")
    suspend fun getCount(): Int

    @Query("SELECT SUM(CASE WHEN isDirectory = 0 THEN 1 ELSE 0 END) FROM sync_files")
    suspend fun getFileCount(): Int
}
