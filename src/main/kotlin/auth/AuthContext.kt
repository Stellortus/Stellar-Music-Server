package top.stellortus.stellar_music_server.auth

import top.stellortus.stellar_music_server.database.auth.AuthRepository
import top.stellortus.stellar_music_server.database.auth.User
import io.ktor.server.application.*
import io.ktor.util.AttributeKey

object AuthContext {
    val UserKey = AttributeKey<User>("authUser")
}

fun ApplicationCall.bearerToken(): String? {
    val header = request.headers["Authorization"] ?: return null
    if (!header.startsWith("Bearer ", ignoreCase = true)) return null
    // "Bearer " 共 7 个字符
    val token = header.substring(7).trim()
    return token.ifEmpty { null }
}

fun ApplicationCall.authenticate(repo: AuthRepository): User? {
    val token = bearerToken() ?: return null
    val user = repo.findByToken(token) ?: return null
    attributes.put(AuthContext.UserKey, user)
    return user
}
