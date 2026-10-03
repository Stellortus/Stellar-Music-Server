package top.stellortus.stellar_music_server.auth

import io.ktor.util.*
import top.stellortus.stellar_music_common.dto.User

object AuthContext {
    val UserKey = AttributeKey<User>("authUser")
}
