package top.stellortus.stellar_music_server.database.playlist

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import top.stellortus.stellar_music_common.dto.Playlist
import top.stellortus.stellar_music_common.dto.PlaylistSummary
import top.stellortus.stellar_music_common.dto.Track
import top.stellortus.stellar_music_server.database.track.TrackTable
import top.stellortus.stellar_music_server.exceptions.ResourceNotExistException
import top.stellortus.stellar_music_server.util.extensions.ifFalse

class PlaylistRepository(
    private val database: Database,
    private val json: Json = Json,
) {

    fun init() {
        transaction(database) {
            SchemaUtils.create(PlaylistsTable, PlaylistTracksTable)
        }
    }

    fun create(ownerId: Int, requestedName: String?): Playlist = transaction(database) {
        val now = System.currentTimeMillis()
        val name = requestedName?.takeIf { it.isNotBlank() }
            ?: "歌单${PlaylistsTable.selectAll().count() + 1}"

        val newId = PlaylistsTable.insert {
            it[PlaylistsTable.ownerId] = ownerId
            it[PlaylistsTable.name] = name
            it[createdAt] = now
            it[updatedAt] = now
        } get PlaylistsTable.id

        Playlist(id = newId, ownerId = ownerId, name = name, createdAt = now, updatedAt = now)
    }

    fun listAll(): List<PlaylistSummary> = transaction(database) {
        val counts = PlaylistTracksTable
            .select(PlaylistTracksTable.playlistId, PlaylistTracksTable.id.count())
            .groupBy(PlaylistTracksTable.playlistId)
            .associate { it[PlaylistTracksTable.playlistId] to it[PlaylistTracksTable.id.count()] }

        PlaylistsTable.selectAll()
            .orderBy(PlaylistsTable.updatedAt to SortOrder.DESC)
            .map { row ->
                val playlist = row.toPlaylist()
                PlaylistSummary(playlist, (counts[playlist.id] ?: 0L).toInt())
            }
    }

    fun get(id: Int): Playlist = transaction(database) {
        PlaylistsTable.selectAll()
            .where { PlaylistsTable.id eq id }
            .limit(1)
            .firstOrNull()
            ?.toPlaylist()
    } ?: throw ResourceNotExistException("id为 $id 的歌单")

    fun tracksOf(playlistId: Int): List<Track> = transaction(database) {
        PlaylistTracksTable
            .innerJoin(TrackTable, { PlaylistTracksTable.trackId }, { TrackTable.id })
            .selectAll()
            .where { PlaylistTracksTable.playlistId eq playlistId }
            .orderBy(PlaylistTracksTable.addedAt to SortOrder.ASC)
            .map { it.toTrack() }
    }

    fun countTracks(playlistId: Int): Int = transaction(database) {
        PlaylistTracksTable.selectAll()
            .where { PlaylistTracksTable.playlistId eq playlistId }
            .count()
            .toInt()
    }

    fun update(id: Int, name: String?, description: String?, coverPath: String?) =
        transaction(database) {
            PlaylistsTable.update({ PlaylistsTable.id eq id }) {
                name?.let { value -> it[PlaylistsTable.name] = value }
                description?.let { value -> it[PlaylistsTable.description] = value }
                coverPath?.let { value -> it[PlaylistsTable.coverPath] = value }
                it[updatedAt] = System.currentTimeMillis()
            } > 0
        }.ifFalse { throw ResourceNotExistException("id为 $id 的歌单") }

    fun delete(id: Int) = transaction(database) {
        PlaylistTracksTable.deleteWhere { PlaylistTracksTable.playlistId eq id }
        PlaylistsTable.deleteWhere { PlaylistsTable.id eq id } > 0
    }.ifFalse { throw ResourceNotExistException("id为 $id 的歌单") }

    fun addTracks(playlistId: Int, trackIds: List<Int>) {
        val unique = trackIds.distinct()
        if (unique.isEmpty()) return

        return transaction(database) {
            val now = System.currentTimeMillis()

            val inLibrary = TrackTable
                .select(TrackTable.id)
                .where { TrackTable.id inList unique }
                .map { it[TrackTable.id] }
                .toSet()

            val alreadyAdded = PlaylistTracksTable
                .select(PlaylistTracksTable.trackId)
                .where { PlaylistTracksTable.playlistId eq playlistId }
                .map { it[PlaylistTracksTable.trackId] }
                .toSet()

            val ignored = mutableListOf<Int>()
            var added = 0

            for (trackId in unique) {
                if (trackId !in inLibrary || trackId in alreadyAdded) {
                    ignored += trackId
                    continue
                }

                PlaylistTracksTable.insert {
                    it[PlaylistTracksTable.playlistId] = playlistId
                    it[PlaylistTracksTable.trackId] = trackId
                    it[addedAt] = now
                }
                added++
            }

            if (added > 0) {
                PlaylistsTable.update({ PlaylistsTable.id eq playlistId }) {
                    it[updatedAt] = now
                }
            }
        }
    }

    fun removeTrack(playlistId: Int, trackId: Int): Boolean = transaction(database) {
        val removed = PlaylistTracksTable.deleteWhere {
            (PlaylistTracksTable.playlistId eq playlistId) and (PlaylistTracksTable.trackId eq trackId)
        } > 0

        if (removed) {
            PlaylistsTable.update({ PlaylistsTable.id eq playlistId }) {
                it[updatedAt] = System.currentTimeMillis()
            }
        }

        removed
    }

    private fun ResultRow.toPlaylist(): Playlist = Playlist(
        id = this[PlaylistsTable.id],
        ownerId = this[PlaylistsTable.ownerId],
        name = this[PlaylistsTable.name],
        description = this[PlaylistsTable.description],
        coverPath = this[PlaylistsTable.coverPath],
        createdAt = this[PlaylistsTable.createdAt],
        updatedAt = this[PlaylistsTable.updatedAt],
    )

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
