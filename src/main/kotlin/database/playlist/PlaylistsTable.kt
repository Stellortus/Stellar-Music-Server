package top.stellortus.stellar_music_server.database.playlist

import org.jetbrains.exposed.sql.Table

object PlaylistsTable : Table("playlists") {
    val id          = integer("id").autoIncrement()
    val ownerId     = integer("owner_id")
    val name        = text("name")
    val description = text("description").nullable()
    val coverPath   = text("cover_path").nullable()
    val createdAt   = long("created_at")
    val updatedAt   = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}
