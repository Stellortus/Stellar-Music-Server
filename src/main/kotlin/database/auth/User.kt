package top.stellortus.stellar_music_server.database.auth

data class User(
    val id: Int = 0,
    val username: String,
    val level: UserLevel = UserLevel.USER,
)
