package top.stellortus.stellar_music_server.update

import top.stellortus.stellar_music_server.db.manager.DatabaseManager

fun main() {
    DatabaseManager.initDb()
    Updater.run()
}

object Updater {

    private val updates: List<Update> = listOf(
        AddPlaylistPublicColumn,
    )

    fun run() {
        for (update in updates) {
            update.apply()
        }
    }
}
