package database.song

import org.jetbrains.exposed.sql.Table

object TrackTable : Table("songs") {
    val id         = integer("id").autoIncrement()
    val title      = varchar("title", 255)
    val artists    = text("artists")                    // 存 JSON 数组字符串
    val fileName   = varchar("file_name", 512).uniqueIndex()
    val uploader   = varchar("uploader", 128)
    val coverPath  = varchar("cover_path", 512)
    val lyricsPath = varchar("lyrics_path", 512)
    val createdAt  = long("created_at")

    override val primaryKey = PrimaryKey(id)
}