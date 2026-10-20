package top.stellortus.stellar_music_server.update

import top.stellortus.stellar_music_server.db.manager.PlaylistManager

object AddPlaylistPublicColumn : Update {

    override fun apply() {
        PlaylistManager.inTransaction {
            val exists = exec(
                "SELECT COUNT(*) FROM pragma_table_info('playlists') WHERE name = 'public'"
            ) { resultSet -> resultSet.next() && resultSet.getInt(1) > 0 } ?: false

            if (!exists) {
                exec("ALTER TABLE playlists ADD COLUMN public BOOLEAN NOT NULL DEFAULT 0")
            }
        }
    }
}
