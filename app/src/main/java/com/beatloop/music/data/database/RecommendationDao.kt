package com.beatloop.music.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

data class SongBehaviorMetrics(
    val songId: String,
    val totalSessions: Int,
    val totalListenedMs: Long,
    val averageCompletionRate: Double,
    val skipCount: Int,
    val completedCount: Int,
    val lastPlayedAt: Long?
)

data class SongInteractionMetrics(
    val songId: String,
    val likeCount: Double,
    val unlikeCount: Double,
    val replayCount: Double,
    val playlistAddCount: Double,
    val lastInteractionAt: Long?
)

data class SongTransitionMetric(
    val songId: String,
    val transitionCount: Int
)

@Dao
interface RecommendationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListeningEvent(event: ListeningSessionEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteraction(signal: InteractionSignal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImpressions(impressions: List<RecommendationImpression>)

    @Query(
        """
        SELECT
            songId,
            COUNT(*) AS totalSessions,
            COALESCE(SUM(listenedMs), 0) AS totalListenedMs,
            COALESCE(AVG(completionRate), 0.0) AS averageCompletionRate,
            COALESCE(SUM(CASE WHEN wasSkipped = 1 THEN 1 ELSE 0 END), 0) AS skipCount,
            COALESCE(SUM(CASE WHEN wasCompleted = 1 THEN 1 ELSE 0 END), 0) AS completedCount,
            MAX(playedAt) AS lastPlayedAt
        FROM listening_session_events
        WHERE playedAt >= :sinceMs
        GROUP BY songId
        """
    )
    suspend fun getSongBehaviorMetrics(sinceMs: Long): List<SongBehaviorMetrics>

    @Query(
        """
        SELECT
            songId,
            COALESCE(SUM(CASE WHEN signalType = 'LIKE' THEN value ELSE 0 END), 0.0) AS likeCount,
            COALESCE(SUM(CASE WHEN signalType = 'UNLIKE' THEN value ELSE 0 END), 0.0) AS unlikeCount,
            COALESCE(SUM(CASE WHEN signalType = 'REPLAY' THEN value ELSE 0 END), 0.0) AS replayCount,
            COALESCE(SUM(CASE WHEN signalType = 'PLAYLIST_ADD' THEN value ELSE 0 END), 0.0) AS playlistAddCount,
            MAX(createdAt) AS lastInteractionAt
        FROM interaction_signals
        WHERE songId IS NOT NULL AND createdAt >= :sinceMs
        GROUP BY songId
        """
    )
    suspend fun getSongInteractionMetrics(sinceMs: Long): List<SongInteractionMetrics>

    @Query(
        """
        SELECT nextSongId AS songId, COUNT(*) AS transitionCount
        FROM listening_session_events
        WHERE songId = :songId AND nextSongId IS NOT NULL AND nextSongId != ''
        GROUP BY nextSongId
        ORDER BY transitionCount DESC
        LIMIT :limit
        """
    )
    suspend fun getNextSongTransitionMetrics(songId: String, limit: Int = 30): List<SongTransitionMetric>

    @Query(
        """
        SELECT songId
        FROM listening_session_events
        ORDER BY playedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentlyListenedSongIds(limit: Int = 60): List<String>

    @Query(
        """
        SELECT songId
        FROM recommendation_impressions
        WHERE shownAt >= :sinceMs
        ORDER BY shownAt DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentlyRecommendedSongIds(sinceMs: Long, limit: Int = 80): List<String>

    @Query(
        """
        SELECT query
        FROM interaction_signals
        WHERE signalType = 'SEARCH' AND query IS NOT NULL AND query != ''
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentSearchQueries(limit: Int = 40): List<String>

    @Query("DELETE FROM listening_session_events WHERE playedAt < :beforeMs")
    suspend fun deleteOldListeningEvents(beforeMs: Long)

    @Query("DELETE FROM interaction_signals WHERE createdAt < :beforeMs")
    suspend fun deleteOldInteractionSignals(beforeMs: Long)

    @Query("DELETE FROM recommendation_impressions WHERE shownAt < :beforeMs")
    suspend fun deleteOldRecommendationImpressions(beforeMs: Long)
}
