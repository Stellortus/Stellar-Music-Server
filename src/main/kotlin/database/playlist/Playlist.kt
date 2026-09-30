package top.stellortus.stellar_music_server.database.playlist

data class Playlist(
    val id: Int = 0,
    val ownerId: Int,
    val name: String,
    val description: String? = null,
    val coverPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class PlaylistSummary(
    val playlist: Playlist,
    val trackCount: Int,
)

data class AddTracksResult(
    val added: Int,
    val ignored: List<Int>,
)
