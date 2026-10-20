package top.stellortus.stellar_music_server.update

import top.stellortus.stellar_music_server.db.manager.PlaylistManager

object AddPlaylistTrackCountColumn : Update {

    override fun apply() {
        PlaylistManager.inTransaction {
            val exists = exec(
                "SELECT COUNT(*) FROM pragma_table_info('playlists') WHERE name = 'track_count'"
            ) { resultSet -> resultSet.next() && resultSet.getInt(1) > 0 } ?: false

            if (!exists) {
                exec("ALTER TABLE playlists ADD COLUMN track_count INTEGER NOT NULL DEFAULT 0")
            }

            exec(
                """
                UPDATE playlists SET track_count = (
                    SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = playlists.id
                )
                """.trimIndent()
            )
        }
    }
}
