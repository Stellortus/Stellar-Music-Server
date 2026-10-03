package top.stellortus.stellar_music_server.util.extensions

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import top.stellortus.stellar_music_common.dto.User
import top.stellortus.stellar_music_common.dto.UserLevel
import top.stellortus.stellar_music_server.auth.AuthContext
import top.stellortus.stellar_music_server.database.auth.AuthRepository
import top.stellortus.stellar_music_server.exceptions.AuthorizationException
import top.stellortus.stellar_music_server.exceptions.PermissionDeniedException
import top.stellortus.stellar_music_server.exceptions.UnauthorizedException

fun ApplicationCall.bearerToken(): String? {
    val header = request.headers[HttpHeaders.Authorization] ?: return null
    if (!header.startsWith("Bearer ", ignoreCase = true)) return null
    // "Bearer " 7 Characters
    val token = header.substring(7).trim()
    return token.ifEmpty { null }
}

private fun ApplicationCall.require(
    repo: AuthRepository,
    minLevel: UserLevel,
) = bearerToken()?.let { token -> repo.findByToken(token) }
        ?.apply { attributes.put(AuthContext.UserKey, this) }
        ?.takeIf { it.level >= minLevel }


context(route: Route)
fun RoutingContext.requirePermission(level: UserLevel, exception: AuthorizationException): User {
    return call.require(route.authRepo, level) ?: throw exception
}

context(route: Route)
fun RoutingContext.requireUser(): User = requirePermission(UserLevel.User, PermissionDeniedException())

context(route: Route)
fun RoutingContext.requireAdmin(): User = requirePermission(UserLevel.Admin, UnauthorizedException())