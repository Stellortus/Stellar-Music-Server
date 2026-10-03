package top.stellortus.stellar_music_server.database.track

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import top.stellortus.stellar_music_common.dto.Track
import top.stellortus.stellar_music_server.exceptions.ResourceNotExistException
import java.io.File

class TrackRepository(private val json: Json = Json) {

    lateinit var database: Database
        private set

    fun init(dbPath: String) {
        File(dbPath).parentFile?.mkdirs()
        database = Database.connect(
            url = "jdbc:sqlite:$dbPath",
            driver = "org.sqlite.JDBC",
            setupConnection = { connection ->
                connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            }
        )
        transaction(database) {
            SchemaUtils.create(TrackTable)
        }
    }

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

    fun top(limit: Int, sort: TrackSort?, start: Int = 0): List<Track> = transaction(database) {
        val (column, order) = when (sort ?: TrackSort.NEWEST) {
            TrackSort.NEWEST -> TrackTable.createdAt to SortOrder.DESC
            TrackSort.OLDEST -> TrackTable.createdAt to SortOrder.ASC
            TrackSort.TITLE_ASC -> TrackTable.title to SortOrder.ASC
            TrackSort.TITLE_DESC -> TrackTable.title to SortOrder.DESC
        }

        TrackTable.selectAll()
            .orderBy(column to order)
            .limit(limit, offset = start.toLong())
            .map { it.toTrack() }
    }

    fun search(keyword: String?, offset: Int, limit: Int): List<Track> = transaction(database) {
        val query = keyword?.trim().orEmpty()
        val base = if (query.isEmpty()) {
            TrackTable.selectAll()
        } else {
            TrackTable.selectAll().where { trackPredicate(query) }
        }

        base.orderBy(TrackTable.id to SortOrder.DESC)
            .limit(limit, offset = offset.toLong())
            .map { it.toTrack() }
    }

    fun count(keyword: String?): Int = transaction(database) {
        val query = keyword?.trim().orEmpty()
        val base = if (query.isEmpty()) {
            TrackTable.selectAll()
        } else {
            TrackTable.selectAll().where { trackPredicate(query) }
        }

        base.count().toInt()
    }

    fun update(
        id: Int,
        title: String?,
        artists: List<String>?,
        coverPath: String?,
        lyricsPath: String?,
    ): Boolean = transaction(database) {
        val exists = TrackTable.selectAll()
            .where { TrackTable.id eq id }
            .limit(1)
            .firstOrNull() != null
        if (!exists) return@transaction false

        TrackTable.update({ TrackTable.id eq id }) {
            title?.let { value -> it[TrackTable.title] = value }
            artists?.let { value ->
                it[TrackTable.artists] = json.encodeToString(ListSerializer(String.serializer()), value)
            }
            coverPath?.let { value -> it[TrackTable.coverPath] = value }
            lyricsPath?.let { value -> it[TrackTable.lyricsPath] = value }
        }
        true
    }

    private fun trackPredicate(keyword: String): Op<Boolean> {
        val needle = "%$keyword%"
        return (TrackTable.title like needle) or
                (TrackTable.artists like needle) or
                (TrackTable.fileName like needle) or
                (TrackTable.uploader like needle)
    }

    fun existsByFileName(fileName: String): Boolean = transaction(database) {
        TrackTable.selectAll().where { TrackTable.fileName eq fileName }
            .limit(1)
            .firstOrNull() != null
    }

    fun get(id: Int): Track = transaction(database) {
        TrackTable.selectAll().where { TrackTable.id eq id }
            .limit(1)
            .firstOrNull()
            ?.toTrack()
    } ?: throw ResourceNotExistException("id为 $id 的歌曲")

    fun delete(id: Int): Boolean = transaction(database) {
        TrackTable.deleteWhere { TrackTable.id eq id } > 0
    }

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