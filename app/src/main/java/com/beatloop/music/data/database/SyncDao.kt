package com.beatloop.music.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreference(entity: UserPreferenceSyncEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreferences(entities: List<UserPreferenceSyncEntity>)

    @Query("SELECT * FROM user_preference_sync WHERE `key` = :key LIMIT 1")
    suspend fun getPreferenceByKey(key: String): UserPreferenceSyncEntity?

    @Query("SELECT * FROM user_preference_sync")
    suspend fun getAllPreferences(): List<UserPreferenceSyncEntity>

    @Query("SELECT * FROM user_preference_sync WHERE isSynced = 0")
    suspend fun getUnsyncedPreferences(): List<UserPreferenceSyncEntity>

    @Query("SELECT MAX(lastUpdatedTimestamp) FROM user_preference_sync")
    suspend fun getLatestPreferenceTimestamp(): Long?

    @Query("UPDATE user_preference_sync SET isSynced = 1, lastUpdatedTimestamp = :syncedAt WHERE `key` IN (:keys)")
    suspend fun markPreferencesSynced(keys: List<String>, syncedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM user_preference_sync")
    suspend fun clearPreferences()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeletedPlaylist(entity: DeletedPlaylistSyncEntity)

    @Query("SELECT * FROM deleted_playlist_sync WHERE syncId = :syncId LIMIT 1")
    suspend fun getDeletedPlaylist(syncId: String): DeletedPlaylistSyncEntity?

    @Query("SELECT * FROM deleted_playlist_sync WHERE isSynced = 0")
    suspend fun getUnsyncedDeletedPlaylists(): List<DeletedPlaylistSyncEntity>

    @Query("SELECT MAX(deletedAt) FROM deleted_playlist_sync")
    suspend fun getLatestDeletedPlaylistTimestamp(): Long?

    @Query("UPDATE deleted_playlist_sync SET isSynced = 1 WHERE syncId IN (:syncIds)")
    suspend fun markDeletedPlaylistsSynced(syncIds: List<String>)

    @Query("DELETE FROM deleted_playlist_sync WHERE syncId IN (:syncIds)")
    suspend fun clearDeletedPlaylists(syncIds: List<String>)

    @Query("DELETE FROM deleted_playlist_sync")
    suspend fun clearAllDeletedPlaylists()
}
