package com.beatloop.music.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val songs: List<SongItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val videos: List<VideoItem> = emptyList(),
    val suggestions: List<String> = emptyList()
)

@Serializable
data class SearchSuggestion(
    val query: String,
    val suggestions: List<String> = emptyList()
)

sealed class SearchFilter(val value: String) {
    data object All : SearchFilter("all")
    data object Songs : SearchFilter("songs")
    data object Artists : SearchFilter("artists")
    data object Albums : SearchFilter("albums")
    data object Playlists : SearchFilter("playlists")
    data object Videos : SearchFilter("videos")
}
