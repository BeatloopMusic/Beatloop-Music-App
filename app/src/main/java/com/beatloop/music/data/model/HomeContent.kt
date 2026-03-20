package com.beatloop.music.data.model

import kotlinx.serialization.Serializable

@Serializable
data class HomeContent(
    val greeting: String,
    val motivationMessage: String? = null,
    val quickPicks: List<SongItem> = emptyList(),
    val personalizedRecommendations: List<SongItem> = emptyList(),
    val recentlyPlayed: List<SongItem> = emptyList(),
    val topArtists: List<String> = emptyList(),
    val trendingSongs: List<SongItem> = emptyList(),
    val newReleases: List<AlbumItem> = emptyList(),
    val recommendedPlaylists: List<PlaylistItem> = emptyList(),
    val moodsAndGenres: List<MoodGenreItem> = emptyList(),
    val charts: List<ChartItem> = emptyList()
)

@Serializable
data class MoodGenreItem(
    val title: String,
    val params: String,
    val thumbnailUrl: String? = null,
    val color: String? = null
)

@Serializable
data class ChartItem(
    val title: String,
    val playlistId: String,
    val thumbnailUrl: String? = null,
    val country: String? = null
)

// Non-serializable class for internal use
data class HomeSection(
    val title: String,
    val songs: List<SongItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val browseId: String? = null
)
