package top.stellortus.stellar_music_server.database.track

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

class TrackRepository(private val json: Json = Json) {

    /** 本仓库专属的数据库连接（不依赖 Exposed 全局默认数据库，避免与认证库冲突） */
    lateinit var database: Database
        private set

    /** 初始化数据库连接并建表（目录不存在时自动创建） */
    fun init(dbPath: String) {
        File(dbPath).parentFile?.mkdirs()
        database = Database.connect(
            url = "jdbc:sqlite:$dbPath",
            driver = "org.sqlite.JDBC"
        )
        transaction(database) {
            SchemaUtils.create(TrackTable)
        }
    }

    // ---------------- 上传（插入） ----------------
    fun upload(track: Track): Track = transaction(database) {
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
    fun top(limit: Int, sort: TrackSort = TrackSort.NEWEST): List<Track> = transaction(database) {
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

    /** 文件名在库里带唯一索引，上传前先用它挡掉重名，避免写入磁盘后再撞库约束 */
    fun existsByFileName(fileName: String): Boolean = transaction(database) {
        TrackTable.selectAll().where { TrackTable.fileName eq fileName }
            .limit(1)
            .firstOrNull() != null
    }

    fun get(id: Int): Track? = transaction(database) {
        TrackTable.selectAll().where { TrackTable.id eq id }
            .limit(1)
            .firstOrNull()
            ?.toTrack()
    }

    fun delete(id: Int): Boolean = transaction(database) {
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