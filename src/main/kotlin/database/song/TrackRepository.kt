package database.song

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class TrackRepository(private val json: Json = Json) {

    /** 初始化数据库连接并建表 */
    fun init(dbPath: String) {
        Database.connect(
            url = "jdbc:sqlite:$dbPath",
            driver = "org.sqlite.JDBC"
        )
        transaction {
            SchemaUtils.create(TrackTable)
        }
    }

    // ---------------- 上传（插入） ----------------
    fun upload(track: Track): Track = transaction {
        val newId = TrackTable.insert {
            it[title] = track.title
            it[artists] = json.encodeToString(ListSerializer(String.serializer()), track.artists)
            it[fileName] = track.fileName
            it[uploader] = track.uploader
            it[coverPath] = track.coverPath
            it[lyricsPath] = track.lyricsPath
            it[createdAt] = track.createdAt
        } get TrackTable.id

        track.copy(id = newId)
    }

    // ---------------- 查询：指定排序下前 x 个 ----------------
    fun top(limit: Int, sort: TrackSort = TrackSort.NEWEST): List<Track> = transaction {
        val (column, order) = when (sort) {
            TrackSort.NEWEST -> TrackTable.createdAt to SortOrder.DESC
            TrackSort.OLDEST -> TrackTable.createdAt to SortOrder.ASC
            TrackSort.TITLE_ASC -> TrackTable.title to SortOrder.ASC
            TrackSort.TITLE_DESC -> TrackTable.title to SortOrder.DESC
        }

        TrackTable.selectAll()
            .orderBy(column to order)
            .limit(limit)
            .map { it.toTrack() }
    }

    fun get(id: Int): Track? = transaction {
        TrackTable.selectAll().where { TrackTable.id eq id }
            .limit(1)
            .firstOrNull()
            ?.toTrack()
    }

    fun delete(id: Int): Boolean = transaction {
        TrackTable.deleteWhere { TrackTable.id eq id } > 0
    }

    // ---------------- 行 → 模型 ----------------
    private fun ResultRow.toTrack(): Track = Track(
        id = this[TrackTable.id],
        title = this[TrackTable.title],
        artists = json.decodeFromString(ListSerializer(String.serializer()), this[TrackTable.artists]),
        fileName = this[TrackTable.fileName],
        uploader = this[TrackTable.uploader],
        coverPath = this[TrackTable.coverPath],
        lyricsPath = this[TrackTable.lyricsPath],
        createdAt = this[TrackTable.createdAt]
    )
}