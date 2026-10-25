@file:Suppress("SpellCheckingInspection")

package top.stellortus.stellar_music_server.db.manager

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import top.stellortus.stellar_music_common.dto.*
import top.stellortus.stellar_music_server.db.table.PlaylistTracksTable
import top.stellortus.stellar_music_server.db.table.PlaylistsTable
import top.stellortus.stellar_music_server.db.table.TrackTable
import top.stellortus.stellar_music_server.exceptions.ResourceNotExistException
import top.stellortus.stellar_music_server.util.extensions.ifFalse

object PlaylistManager : DatabaseManager("tracks.db", PlaylistsTable, PlaylistTracksTable) {

    private val json: Json = Json

    fun create(ownerId: Int, request: CreatePlaylistRequest): Playlist = transaction(database) {
        val now = System.currentTimeMillis()
        val name = request.name?.takeIf { it.isNotBlank() }
            ?: "歌单${PlaylistsTable.selectAll().count() + 1}"
        val public = request.public ?: false

        val newId = PlaylistsTable.insert {
            it[PlaylistsTable.ownerId] = ownerId
            it[PlaylistsTable.name] = name
            it[description] = request.description
            it[createdAt] = now
            it[updatedAt] = now
            it[PlaylistsTable.public] = public
        } get PlaylistsTable.id

        Playlist(id = newId, ownerId = ownerId, name = name, public = public, createdAt = now, updatedAt = now, trackCount = 0)
    }

    fun listAll(): List<Playlist> = transaction(database) {
        PlaylistsTable.selectAll()
            .orderBy(PlaylistsTable.updatedAt to SortOrder.DESC)
            .map { it.toPlaylist() }
    }


    /**
     * `searchKey` may contain spaces
     * The search result **must** ignore spaces or the difference between upper/lowercase letters
     * e.g. for `searchKey="hello world"`, the following names match the `searchKey`:
     *  - HelloWorld
     *  - HeLlO WorLD
     *  - hellOWorLd
     */
    fun search(searchKey: String): List<Playlist> {
        val keyword = searchKey.filterNot { it.isWhitespace() }
        return listAll()
            .filter { it.public }
            .filter {
                it.name.filterNot { char -> char.isWhitespace() }
                    .contains(keyword, ignoreCase = true)
            }
    }

    fun myPlaylist(user: User): List<Playlist> = transaction(database) {
        PlaylistsTable.selectAll()
            .orderBy(PlaylistsTable.updatedAt to SortOrder.DESC)
            .where { PlaylistsTable.ownerId eq user.id }
            .map { it.toPlaylist() }
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

    fun countTracks(playlistId: Int): Int = get(playlistId).trackCount

    fun update(id: Int, request: UpdatePlaylistRequest) =
        transaction(database) {
            PlaylistsTable.update({ PlaylistsTable.id eq id }) {
                request.name?.let { value -> it[PlaylistsTable.name] = value }
                request.description?.let { value -> it[PlaylistsTable.description] = value }
                request.coverPath?.let { value -> it[PlaylistsTable.coverPath] = value }
                request.public?.let { value -> it[PlaylistsTable.public] = value }
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
                    with(SqlExpressionBuilder) {
                        it[trackCount] = trackCount + added
                    }
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
                with(SqlExpressionBuilder) {
                    it[trackCount] = trackCount - 1
                }
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
        public = this[PlaylistsTable.public],
        trackCount = this[PlaylistsTable.trackCount],
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
