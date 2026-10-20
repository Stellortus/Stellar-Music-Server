package top.stellortus.stellar_music_server.db.table

import org.jetbrains.exposed.sql.Table

object PlaylistsTable : Table("playlists") {
    val id          = integer("id").autoIncrement()
    val ownerId     = integer("owner_id")
    val name        = text("name")
    val description = text("description").nullable()
    val coverPath   = text("cover_path").nullable()
    val public    = bool("public").default(false)
    val createdAt   = long("created_at")
    val updatedAt   = long("updated_at")
    val trackCount = integer("track_count").default(0)

    override val primaryKey = PrimaryKey(id)
}
