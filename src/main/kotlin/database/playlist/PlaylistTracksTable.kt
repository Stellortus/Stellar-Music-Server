package top.stellortus.stellar_music_server.database.playlist

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import top.stellortus.stellar_music_server.database.track.TrackTable

object PlaylistTracksTable : Table("playlist_tracks") {
    val id         = integer("id").autoIncrement()
    val playlistId = integer("playlist_id")
        .references(PlaylistsTable.id, onDelete = ReferenceOption.CASCADE)
    val trackId    = integer("track_id")
        .references(TrackTable.id, onDelete = ReferenceOption.CASCADE)
    val addedAt    = long("added_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(playlistId, trackId)
    }
}
