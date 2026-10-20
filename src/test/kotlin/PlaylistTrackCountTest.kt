package top.stellortus

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import top.stellortus.stellar_music_common.dto.CreatePlaylistRequest
import top.stellortus.stellar_music_server.db.manager.DatabaseManager
import top.stellortus.stellar_music_server.db.manager.PlaylistManager
import top.stellortus.stellar_music_server.db.manager.TrackManager
import top.stellortus.stellar_music_server.db.table.PlaylistTracksTable
import top.stellortus.stellar_music_server.db.table.PlaylistsTable
import top.stellortus.stellar_music_server.db.table.TrackTable
import top.stellortus.stellar_music_server.update.AddPlaylistTrackCountColumn
import java.nio.file.Files
import kotlin.test.*

class PlaylistTrackCountTest {
    private lateinit var database: Database
    private lateinit var databaseFile: java.nio.file.Path
    private val databaseField = DatabaseManager::class.java.getDeclaredField("database").apply {
        isAccessible = true
    }
    private var previousPlaylistDatabase: Any? = null
    private var previousTrackDatabase: Any? = null

    @BeforeTest
    fun setUp() {
        databaseFile = Files.createTempFile("playlist-track-count-", ".db")
        database = Database.connect(
            "jdbc:sqlite:$databaseFile",
            driver = "org.sqlite.JDBC",
            setupConnection = { connection ->
                connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            }
        )
        previousPlaylistDatabase = databaseField.get(PlaylistManager)
        previousTrackDatabase = databaseField.get(TrackManager)
        databaseField.set(PlaylistManager, database)
        databaseField.set(TrackManager, database)
        transaction(database) {
            SchemaUtils.create(TrackTable, PlaylistsTable, PlaylistTracksTable)
        }
    }

    @AfterTest
    fun tearDown() {
        databaseField.set(PlaylistManager, previousPlaylistDatabase)
        databaseField.set(TrackManager, previousTrackDatabase)
        TransactionManager.closeAndUnregister(database)
        Files.deleteIfExists(databaseFile)
    }

    private fun addLibraryTrack(): Int = transaction(database) {
        TrackTable.insert {
            it[title] = "Track"
            it[artists] = "[]"
            it[fileName] = "${System.nanoTime()}.mp3"
            it[uploader] = "tester"
            it[coverPath] = ""
            it[lyricsPath] = ""
            it[createdAt] = 0
        } get TrackTable.id
    }

    @Test
    fun `count follows actual additions and removals`() {
        val playlist = PlaylistManager.create(1, CreatePlaylistRequest(name = "Test"))
        assertEquals(0, playlist.trackCount)
        assertEquals(0, PlaylistManager.get(playlist.id).trackCount)
        val first = addLibraryTrack()
        val second = addLibraryTrack()

        PlaylistManager.addTracks(playlist.id, listOf(first, first, second, Int.MAX_VALUE))
        PlaylistManager.addTracks(playlist.id, listOf(first))
        PlaylistManager.addTracks(playlist.id, emptyList())
        assertEquals(2, PlaylistManager.get(playlist.id).trackCount)
        assertEquals(2, PlaylistManager.listAll().single().trackCount)
        assertEquals(2, PlaylistManager.countTracks(playlist.id))

        assertFalse(PlaylistManager.removeTrack(playlist.id, Int.MAX_VALUE))
        assertTrue(PlaylistManager.removeTrack(playlist.id, first))
        assertFalse(PlaylistManager.removeTrack(playlist.id, first))
        assertEquals(1, PlaylistManager.get(playlist.id).trackCount)
        assertTrue(PlaylistManager.removeTrack(playlist.id, second))
        assertEquals(0, PlaylistManager.get(playlist.id).trackCount)
    }

    @Test
    fun `library deletion updates every affected playlist`() {
        val first = PlaylistManager.create(1, CreatePlaylistRequest(name = "First"))
        val second = PlaylistManager.create(1, CreatePlaylistRequest(name = "Second"))
        val unaffected = PlaylistManager.create(1, CreatePlaylistRequest(name = "Empty"))
        val track = addLibraryTrack()
        PlaylistManager.addTracks(first.id, listOf(track, addLibraryTrack()))
        PlaylistManager.addTracks(second.id, listOf(track))

        assertTrue(TrackManager.delete(track))
        assertFalse(TrackManager.delete(track))
        assertEquals(1, PlaylistManager.get(first.id).trackCount)
        assertEquals(0, PlaylistManager.get(second.id).trackCount)
        assertEquals(0, PlaylistManager.get(unaffected.id).trackCount)
        assertEquals(1, PlaylistManager.tracksOf(first.id).size)
        assertTrue(PlaylistManager.tracksOf(second.id).isEmpty())
    }

    @Test
    fun `migration backfills legacy playlists and can run repeatedly`() {
        val populated = PlaylistManager.create(1, CreatePlaylistRequest(name = "Populated"))
        val empty = PlaylistManager.create(1, CreatePlaylistRequest(name = "Empty"))
        PlaylistManager.addTracks(populated.id, listOf(addLibraryTrack(), addLibraryTrack()))
        transaction(database) {
            exec("ALTER TABLE playlists DROP COLUMN track_count")
        }

        AddPlaylistTrackCountColumn.apply()
        AddPlaylistTrackCountColumn.apply()
        assertEquals(2, PlaylistManager.get(populated.id).trackCount)
        assertEquals(0, PlaylistManager.get(empty.id).trackCount)
        assertEquals(0, PlaylistManager.create(1, CreatePlaylistRequest(name = "New")).trackCount)
    }

    @Test
    fun `creation defaults to a private empty playlist with the requested owner`() {
        val playlist = PlaylistManager.create(42, CreatePlaylistRequest())
        assertEquals("歌单1", playlist.name)
        assertEquals(42, playlist.ownerId)
        assertFalse(playlist.public)
        assertEquals(0, playlist.trackCount)
        assertEquals(playlist, PlaylistManager.get(playlist.id))

        val blankName = PlaylistManager.create(42, CreatePlaylistRequest(name = "  "))
        assertEquals("歌单2", blankName.name)
        assertEquals(blankName, PlaylistManager.get(blankName.id))
    }

    @Test
    fun `search returns public playlists with their persisted counts`() {
        PlaylistManager.create(1, CreatePlaylistRequest(name = "Hello World"))
        val public = PlaylistManager.create(
            42, CreatePlaylistRequest(name = "HeLLoWorld", public = true)
        )
        PlaylistManager.create(42, CreatePlaylistRequest(name = "Other", public = true))
        assertTrue(public.public)
        assertTrue(PlaylistManager.get(public.id).public)
        PlaylistManager.addTracks(public.id, listOf(addLibraryTrack()))

        val result = PlaylistManager.search("hello world").single()
        assertEquals(public.id, result.id)
        assertEquals(42, result.ownerId)
        assertTrue(result.public)
        assertEquals(1, result.trackCount)
    }
}
