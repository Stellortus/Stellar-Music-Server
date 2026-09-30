package top.stellortus.stellar_music_server.playlist

import kotlinx.serialization.Serializable
import top.stellortus.stellar_music_server.database.playlist.Playlist
import top.stellortus.stellar_music_server.database.playlist.PlaylistSummary
import top.stellortus.stellar_music_server.database.track.Track

@Serializable
data class CreatePlaylistRequest(
    val name: String? = null,
    val description: String? = null,
    val coverPath: String? = null,
)

@Serializable
data class UpdatePlaylistRequest(
    val name: String? = null,
    val description: String? = null,
    val coverPath: String? = null,
)

@Serializable
data class AddTracksRequest(val trackIds: List<Int> = emptyList())

@Serializable
data class AddTracksResponse(val message: String, val added: Int, val ignored: List<Int>)

@Serializable
data class PlaylistResponse(
    val id: Int,
    val ownerId: Int,
    val name: String,
    val description: String?,
    val coverPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Int,
)

@Serializable
data class PlaylistDetailResponse(
    val id: Int,
    val ownerId: Int,
    val name: String,
    val description: String?,
    val coverPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val tracks: List<Track>,
)

fun Playlist.toResponse(trackCount: Int): PlaylistResponse = PlaylistResponse(
    id = id,
    ownerId = ownerId,
    name = name,
    description = description,
    coverPath = coverPath,
    createdAt = createdAt,
    updatedAt = updatedAt,
    trackCount = trackCount,
)

fun PlaylistSummary.toResponse(): PlaylistResponse = playlist.toResponse(trackCount)

fun Playlist.toDetailResponse(tracks: List<Track>): PlaylistDetailResponse = PlaylistDetailResponse(
    id = id,
    ownerId = ownerId,
    name = name,
    description = description,
    coverPath = coverPath,
    createdAt = createdAt,
    updatedAt = updatedAt,
    tracks = tracks,
)
