package top.stellortus.stellar_music_server.config

object AppPaths {

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    val serverDataDir: String
        get() = if (isWindows) "src/main/resources" else "/opt/Stellar-Music-Server"

    val mediaDir: String
        get() = if (isWindows) "src/main/resources" else "/srv/Stellar-Music-Server"

    val databaseDir: String
        get() = "$serverDataDir/db"

}
