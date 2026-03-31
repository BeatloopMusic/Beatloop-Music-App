package com.beatloop.music.sync

import android.content.Context
import com.beatloop.music.data.auth.AuthManager
import com.beatloop.music.data.database.BeatloopDatabase
import com.beatloop.music.data.database.DeletedPlaylistSyncEntity
import com.beatloop.music.data.database.PlaylistEntity
import com.beatloop.music.data.database.PlaylistSongCrossRef
import com.beatloop.music.data.database.SearchHistory
import com.beatloop.music.data.database.SearchHistoryDao
import com.beatloop.music.data.database.SongDao
import com.beatloop.music.data.database.SyncDao
import com.beatloop.music.data.database.UserPreferenceSyncEntity
import com.beatloop.music.data.model.DownloadState
import com.beatloop.music.data.model.Song
import com.beatloop.music.data.preferences.PreferencesManager
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val database: BeatloopDatabase,
    private val songDao: SongDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val syncDao: SyncDao,
    private val preferencesManager: PreferencesManager
) {
    companion object {
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_LISTENING_HISTORY = "listening_history"
        private const val COLLECTION_SEARCH_HISTORY = "search_history"
        private const val COLLECTION_PLAYLISTS = "playlists"
        private const val COLLECTION_PREFERENCES = "preferences"
        private const val COLLECTION_PLAYLIST_DELETIONS = "playlist_deletions"
        private const val PROFILE_DOC = "profile"
        private const val MAX_BATCH_WRITES = 350
    }

    suspend fun mergeLocalAndRemote(): Result<Unit> = withContext(Dispatchers.IO) {
        syncFromFirebase().fold(
            onSuccess = { syncToFirebase() },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun syncToFirebase(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = authManager.getCurrentFirebaseUid() ?: return@runCatching
            val firestore = firestoreOrNull() ?: error("Firebase Firestore is not configured")

            snapshotPreferencesToRoom()

            val unsyncedSongs = songDao.getUnsyncedListeningSongs()
            val unsyncedSearchHistory = searchHistoryDao.getUnsyncedSearchHistory()
            val unsyncedPlaylists = songDao.getUnsyncedPlaylists()
            val unsyncedPreferences = syncDao.getUnsyncedPreferences()
            val unsyncedDeletedPlaylists = syncDao.getUnsyncedDeletedPlaylists()

            if (
                unsyncedSongs.isEmpty() &&
                unsyncedSearchHistory.isEmpty() &&
                unsyncedPlaylists.isEmpty() &&
                unsyncedPreferences.isEmpty() &&
                unsyncedDeletedPlaylists.isEmpty()
            ) {
                return@runCatching
            }

            val userRef = firestore.collection(COLLECTION_USERS).document(userId)
            var batch = firestore.batch()
            var operationCount = 0

            suspend fun commitIfNeeded(force: Boolean = false) {
                if (operationCount >= MAX_BATCH_WRITES || (force && operationCount > 0)) {
                    batch.commit().await()
                    batch = firestore.batch()
                    operationCount = 0
                }
            }

            unsyncedSongs.forEach { song ->
                val payload = mapOf(
                    "songId" to song.id,
                    "title" to song.title,
                    "artistsText" to song.artistsText,
                    "playCount" to song.playCount,
                    "lastPlayedAt" to (song.lastPlayedAt ?: 0L),
                    "lastUpdatedTimestamp" to song.lastUpdatedTimestamp
                )
                batch.set(
                    userRef.collection(COLLECTION_LISTENING_HISTORY).document(song.id),
                    payload,
                    SetOptions.merge()
                )
                operationCount++
                commitIfNeeded()
            }

            unsyncedSearchHistory.forEach { item ->
                val docId = stableId(item.query)
                val payload = mapOf(
                    "query" to item.query,
                    "searchCount" to item.searchCount,
                    "lastSearchedAt" to item.timestamp,
                    "lastUpdatedTimestamp" to item.lastUpdatedTimestamp
                )
                batch.set(
                    userRef.collection(COLLECTION_SEARCH_HISTORY).document(docId),
                    payload,
                    SetOptions.merge()
                )
                operationCount++
                commitIfNeeded()
            }

            unsyncedPlaylists.forEach { playlist ->
                val payload = mapOf(
                    "syncId" to playlist.syncId,
                    "name" to playlist.name,
                    "songIds" to playlist.songIds(),
                    "lastUpdatedTimestamp" to playlist.lastUpdatedTimestamp
                )
                batch.set(
                    userRef.collection(COLLECTION_PLAYLISTS).document(playlist.syncId),
                    payload,
                    SetOptions.merge()
                )
                operationCount++
                commitIfNeeded()
            }

            unsyncedDeletedPlaylists.forEach { deletion ->
                batch.delete(userRef.collection(COLLECTION_PLAYLISTS).document(deletion.syncId))
                operationCount++
                commitIfNeeded()

                batch.set(
                    userRef.collection(COLLECTION_PLAYLIST_DELETIONS).document(deletion.syncId),
                    mapOf(
                        "syncId" to deletion.syncId,
                        "deletedAt" to deletion.deletedAt,
                        "lastUpdatedTimestamp" to deletion.deletedAt
                    ),
                    SetOptions.merge()
                )
                operationCount++
                commitIfNeeded()
            }

            if (unsyncedPreferences.isNotEmpty()) {
                val preferenceMap = unsyncedPreferences.associate { it.key to it.value }
                val timestamp = unsyncedPreferences.maxOf { it.lastUpdatedTimestamp }

                batch.set(
                    userRef.collection(COLLECTION_PREFERENCES).document(PROFILE_DOC),
                    mapOf(
                        "values" to preferenceMap,
                        "lastUpdatedTimestamp" to timestamp
                    ),
                    SetOptions.merge()
                )
                operationCount++
            }

            commitIfNeeded(force = true)

            if (unsyncedSongs.isNotEmpty()) {
                songDao.markListeningSongsSynced(unsyncedSongs.map { it.id })
            }
            if (unsyncedSearchHistory.isNotEmpty()) {
                searchHistoryDao.markSynced(unsyncedSearchHistory.map { it.query })
            }
            if (unsyncedPlaylists.isNotEmpty()) {
                songDao.markPlaylistsSynced(unsyncedPlaylists.map { it.syncId })
            }
            if (unsyncedPreferences.isNotEmpty()) {
                syncDao.markPreferencesSynced(unsyncedPreferences.map { it.key })
            }
            if (unsyncedDeletedPlaylists.isNotEmpty()) {
                syncDao.markDeletedPlaylistsSynced(unsyncedDeletedPlaylists.map { it.syncId })
            }
        }
    }

    suspend fun syncFromFirebase(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = authManager.getCurrentFirebaseUid() ?: return@runCatching
            val firestore = firestoreOrNull() ?: error("Firebase Firestore is not configured")
            val userRef = firestore.collection(COLLECTION_USERS).document(userId)

            val latestListening = songDao.getLatestListeningUpdatedTimestamp() ?: 0L
            val listeningDocs = userRef.collection(COLLECTION_LISTENING_HISTORY)
                .whereGreaterThan("lastUpdatedTimestamp", latestListening)
                .get()
                .await()

            listeningDocs.documents.forEach { doc ->
                val remoteTimestamp = doc.getLong("lastUpdatedTimestamp") ?: 0L
                if (remoteTimestamp <= 0L) return@forEach

                val songId = doc.id
                val title = doc.getString("title").orEmpty().ifBlank { "Unknown Title" }
                val artists = doc.getString("artistsText").orEmpty().ifBlank { "Unknown Artist" }
                val playCount = (doc.getLong("playCount") ?: 0L).toInt().coerceAtLeast(0)
                val lastPlayedAt = doc.getLong("lastPlayedAt")?.takeIf { it > 0L }

                val local = songDao.getSongById(songId)
                if (local == null) {
                    songDao.insert(
                        Song(
                            id = songId,
                            title = title,
                            artistsText = artists,
                            playCount = playCount,
                            lastPlayedAt = lastPlayedAt,
                            downloadState = DownloadState.NOT_DOWNLOADED,
                            lastUpdatedTimestamp = remoteTimestamp,
                            isSynced = true
                        )
                    )
                } else if (remoteTimestamp > local.lastUpdatedTimestamp) {
                    songDao.updateListeningAggregate(
                        songId = songId,
                        title = title,
                        artistsText = artists,
                        playCount = playCount,
                        lastPlayedAt = lastPlayedAt,
                        lastUpdatedTimestamp = remoteTimestamp,
                        isSynced = true
                    )
                }
            }

            val latestSearch = searchHistoryDao.getLatestSearchUpdatedTimestamp() ?: 0L
            val searchDocs = userRef.collection(COLLECTION_SEARCH_HISTORY)
                .whereGreaterThan("lastUpdatedTimestamp", latestSearch)
                .get()
                .await()

            searchDocs.documents.forEach { doc ->
                val query = doc.getString("query").orEmpty()
                if (query.isBlank()) return@forEach

                val remoteTimestamp = doc.getLong("lastUpdatedTimestamp") ?: 0L
                if (remoteTimestamp <= 0L) return@forEach

                val localEntry = searchHistoryDao.getSearchHistoryEntry(query)
                if (localEntry == null || remoteTimestamp > localEntry.lastUpdatedTimestamp) {
                    searchHistoryDao.insert(
                        SearchHistory(
                            query = query,
                            timestamp = doc.getLong("lastSearchedAt") ?: remoteTimestamp,
                            searchCount = (doc.getLong("searchCount") ?: 1L).toInt().coerceAtLeast(1),
                            lastUpdatedTimestamp = remoteTimestamp,
                            isSynced = true
                        )
                    )
                }
            }

            val latestDeletion = syncDao.getLatestDeletedPlaylistTimestamp() ?: 0L
            val deletionDocs = userRef.collection(COLLECTION_PLAYLIST_DELETIONS)
                .whereGreaterThan("lastUpdatedTimestamp", latestDeletion)
                .get()
                .await()

            deletionDocs.documents.forEach { doc ->
                val deletedAt = doc.getLong("deletedAt") ?: doc.getLong("lastUpdatedTimestamp") ?: 0L
                val syncId = doc.id
                if (syncId.isBlank() || deletedAt <= 0L) return@forEach

                songDao.getPlaylistBySyncId(syncId)?.let { playlist ->
                    songDao.deletePlaylist(playlist.id)
                }
                syncDao.upsertDeletedPlaylist(
                    DeletedPlaylistSyncEntity(
                        syncId = syncId,
                        deletedAt = deletedAt,
                        isSynced = true
                    )
                )
            }

            val latestPlaylist = songDao.getLatestPlaylistUpdatedTimestamp() ?: 0L
            val playlistDocs = userRef.collection(COLLECTION_PLAYLISTS)
                .whereGreaterThan("lastUpdatedTimestamp", latestPlaylist)
                .get()
                .await()

            playlistDocs.documents.forEach { doc ->
                val syncId = doc.id
                val remoteTimestamp = doc.getLong("lastUpdatedTimestamp") ?: 0L
                if (syncId.isBlank() || remoteTimestamp <= 0L) return@forEach

                val deletedMarker = syncDao.getDeletedPlaylist(syncId)
                if (deletedMarker != null && deletedMarker.deletedAt >= remoteTimestamp) {
                    return@forEach
                }

                val name = doc.getString("name").orEmpty().ifBlank { "Playlist" }
                val songIds = (doc.get("songIds") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()

                val existing = songDao.getPlaylistBySyncId(syncId)
                if (existing == null) {
                    val newPlaylistId = songDao.createPlaylist(
                        PlaylistEntity(
                            syncId = syncId,
                            name = name,
                            createdAt = remoteTimestamp,
                            lastUpdatedTimestamp = remoteTimestamp,
                            isSynced = true
                        )
                    )
                    songIds.forEach { songId ->
                        songDao.addSongToPlaylistEntry(
                            PlaylistSongCrossRef(
                                playlistId = newPlaylistId,
                                songId = songId,
                                addedAt = remoteTimestamp
                            )
                        )
                    }
                    songDao.markPlaylistsSynced(listOf(syncId))
                } else if (remoteTimestamp > existing.lastUpdatedTimestamp) {
                    songDao.applyRemotePlaylistUpdate(
                        playlistId = existing.id,
                        name = name,
                        updatedAt = remoteTimestamp
                    )
                    songDao.clearPlaylistSongs(existing.id)
                    songIds.forEach { songId ->
                        songDao.addSongToPlaylistEntry(
                            PlaylistSongCrossRef(
                                playlistId = existing.id,
                                songId = songId,
                                addedAt = remoteTimestamp
                            )
                        )
                    }
                    songDao.markPlaylistsSynced(listOf(syncId))
                }
            }

            val remotePreferences = userRef.collection(COLLECTION_PREFERENCES)
                .document(PROFILE_DOC)
                .get()
                .await()

            if (remotePreferences.exists()) {
                val remoteTimestamp = remotePreferences.getLong("lastUpdatedTimestamp") ?: 0L
                val localTimestamp = syncDao.getLatestPreferenceTimestamp() ?: 0L
                if (remoteTimestamp > localTimestamp) {
                    val values = (remotePreferences.get("values") as? Map<*, *>)
                        ?.mapNotNull { (k, v) ->
                            val key = k as? String ?: return@mapNotNull null
                            val value = v?.toString() ?: return@mapNotNull null
                            key to value
                        }
                        ?.toMap()
                        ?: emptyMap()

                    if (values.isNotEmpty()) {
                        preferencesManager.applySyncPreferences(values)
                        syncDao.upsertPreferences(
                            values.map { (key, value) ->
                                UserPreferenceSyncEntity(
                                    key = key,
                                    value = value,
                                    lastUpdatedTimestamp = remoteTimestamp,
                                    isSynced = true
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    suspend fun mergeGuestDataToCurrentUser(
        guestUserId: String,
        targetUserId: String,
        deleteSource: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (guestUserId.isBlank() || targetUserId.isBlank() || guestUserId == targetUserId) {
                return@runCatching
            }

            val firestore = firestoreOrNull() ?: error("Firebase Firestore is not configured")
            val sourceUserRef = firestore.collection(COLLECTION_USERS).document(guestUserId)
            val targetUserRef = firestore.collection(COLLECTION_USERS).document(targetUserId)

            mergeCollectionByLatest(sourceUserRef, targetUserRef, COLLECTION_LISTENING_HISTORY)
            mergeCollectionByLatest(sourceUserRef, targetUserRef, COLLECTION_SEARCH_HISTORY)
            mergeCollectionByLatest(sourceUserRef, targetUserRef, COLLECTION_PLAYLISTS)
            mergeCollectionByLatest(sourceUserRef, targetUserRef, COLLECTION_PLAYLIST_DELETIONS)
            mergeCollectionByLatest(sourceUserRef, targetUserRef, COLLECTION_PREFERENCES)

            if (deleteSource) {
                deleteUserDocument(sourceUserRef)
            }
        }
    }

    suspend fun deleteMyData(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val firebaseUid = authManager.getCurrentFirebaseUid()
            val firestore = firestoreOrNull()

            if (!firebaseUid.isNullOrBlank() && firestore != null) {
                val userRef = firestore.collection(COLLECTION_USERS).document(firebaseUid)
                deleteUserDocument(userRef)
            }

            database.clearAllTables()
            authManager.signOutAndResetIdentity()
        }
    }

    private suspend fun snapshotPreferencesToRoom() {
        val now = System.currentTimeMillis()
        val values = preferencesManager.exportSyncPreferences()
        val changed = buildList {
            values.forEach { (key, value) ->
                val existing = syncDao.getPreferenceByKey(key)
                if (existing == null || existing.value != value) {
                    add(
                        UserPreferenceSyncEntity(
                            key = key,
                            value = value,
                            lastUpdatedTimestamp = now,
                            isSynced = false
                        )
                    )
                }
            }
        }
        if (changed.isNotEmpty()) {
            syncDao.upsertPreferences(changed)
        }
    }

    private suspend fun mergeCollectionByLatest(
        sourceUserRef: com.google.firebase.firestore.DocumentReference,
        targetUserRef: com.google.firebase.firestore.DocumentReference,
        collectionName: String
    ) {
        val sourceDocs = sourceUserRef.collection(collectionName).get().await()
        if (sourceDocs.isEmpty) return

        val firestore = sourceUserRef.firestore
        var batch = firestore.batch()
        var operationCount = 0

        suspend fun commitIfNeeded(force: Boolean = false) {
            if (operationCount >= MAX_BATCH_WRITES || (force && operationCount > 0)) {
                batch.commit().await()
                batch = firestore.batch()
                operationCount = 0
            }
        }

        sourceDocs.documents.forEach { sourceDoc ->
            val data = sourceDoc.data ?: return@forEach
            val targetDocRef = targetUserRef.collection(collectionName).document(sourceDoc.id)
            val targetSnapshot = targetDocRef.get().await()

            val sourceTimestamp = (data["lastUpdatedTimestamp"] as? Number)?.toLong()
                ?: (data["deletedAt"] as? Number)?.toLong()
                ?: 0L
            val targetTimestamp = targetSnapshot.getLong("lastUpdatedTimestamp")
                ?: targetSnapshot.getLong("deletedAt")
                ?: Long.MIN_VALUE

            if (!targetSnapshot.exists() || sourceTimestamp >= targetTimestamp) {
                batch.set(targetDocRef, data, SetOptions.merge())
                operationCount++
                commitIfNeeded()
            }
        }

        commitIfNeeded(force = true)
    }

    private suspend fun deleteUserDocument(userRef: com.google.firebase.firestore.DocumentReference) {
        val firestore = userRef.firestore
        val collections = listOf(
            COLLECTION_LISTENING_HISTORY,
            COLLECTION_SEARCH_HISTORY,
            COLLECTION_PLAYLISTS,
            COLLECTION_PREFERENCES,
            COLLECTION_PLAYLIST_DELETIONS
        )

        var batch = firestore.batch()
        var operationCount = 0

        suspend fun commitIfNeeded(force: Boolean = false) {
            if (operationCount >= MAX_BATCH_WRITES || (force && operationCount > 0)) {
                batch.commit().await()
                batch = firestore.batch()
                operationCount = 0
            }
        }

        collections.forEach { collectionName ->
            val docs = userRef.collection(collectionName).get().await()
            docs.documents.forEach { doc ->
                batch.delete(doc.reference)
                operationCount++
                commitIfNeeded()
            }
        }

        batch.delete(userRef)
        operationCount++
        commitIfNeeded(force = true)
    }

    private fun firestoreOrNull(): FirebaseFirestore? {
        val initialized = runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseApp.getApps(context).isNotEmpty()
        }.getOrDefault(false)

        if (!initialized) return null
        return runCatching { FirebaseFirestore.getInstance() }.getOrNull()
    }

    private fun stableId(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
