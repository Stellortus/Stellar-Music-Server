package top.stellortus.stellar_music_server.auth

import io.ktor.http.HttpHeaders
import top.stellortus.stellar_music_server.database.auth.AuthRepository
import top.stellortus.stellar_music_server.database.auth.User
import top.stellortus.stellar_music_server.database.auth.UserLevel
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey

const val UNAUTHORIZED_MESSAGE = "未登录或 token 无效"

const val FORBIDDEN_MESSAGE = "权限不足"

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

suspend fun ApplicationCall.requireUser(
    repo: AuthRepository,
    minLevel: UserLevel = UserLevel.USER,
): User? {
    val user = bearerToken()?.let { token -> repo.findByToken(token) }
        ?.apply { attributes.put(AuthContext.UserKey, this) }
    if (user != null) {
        if (user.level.value >= minLevel.value) return user

        application.log.warn(
            "${request.httpMethod.value} ${request.uri} 被拒: 权限不足 " +
                    "user=${user.username} level=${user.level.value} 需要=${minLevel.value}"
        )
        respond(HttpStatusCode.Forbidden, MessageResponse(FORBIDDEN_MESSAGE))
        return null
    }

    val header = request.headers[HttpHeaders.Authorization]
    application.log.warn(
        "${request.httpMethod.value} ${request.uri} 被拒: 未认证 " +
                "Authorization=${if (header == null) "缺失" else "存在但不匹配"}"
    )
    respond(HttpStatusCode.Unauthorized, MessageResponse(UNAUTHORIZED_MESSAGE))
    return null
}
