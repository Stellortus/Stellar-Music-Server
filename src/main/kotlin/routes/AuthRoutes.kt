package top.stellortus.stellar_music_server.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import top.stellortus.stellar_music_common.dto.AuthResponse
import top.stellortus.stellar_music_common.dto.LoginRequest
import top.stellortus.stellar_music_common.dto.MessageResponse
import top.stellortus.stellar_music_common.dto.RegisterRequest
import top.stellortus.stellar_music_server.auth.TokenGenerator
import top.stellortus.stellar_music_server.db.manager.AuthManager
import top.stellortus.stellar_music_server.util.extensions.bearerToken
import top.stellortus.stellar_music_server.util.extensions.passwordHasher
import top.stellortus.stellar_music_server.util.extensions.requireUser
import top.stellortus.stellar_music_server.util.extensions.tryReceive

fun Route.authRoutes() {

    route("/auth") {

        post("/register") {
            val req = call.tryReceive<RegisterRequest>()
            val username = req.username
            val password = req.password

            if (username.isEmpty() || password.isEmpty()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    MessageResponse("用户名和密码不能为空")
                )
            }
            if (AuthManager.findByUsername(username) != null) {
                return@post call.respond(
                    HttpStatusCode.Conflict,
                    MessageResponse("用户名已存在")
                )
            }

            val user = AuthManager.createUser(username, passwordHasher.hash(password))
            val token = TokenGenerator.generate()
            AuthManager.createSession(user.id, token)
            call.respond(HttpStatusCode.OK, AuthResponse(token, user))
        }

        post("/login") {
            val req = call.tryReceive<LoginRequest>()
            val username = req.username
            val password = req.password

            val credentials = username.takeIf { it.isNotEmpty() }?.let { AuthManager.findCredentials(it) }
            val user = credentials?.first
            val hash = credentials?.second

            // Fake hash if user not exists
            val passwordMatches = passwordHasher.verify(password, hash ?: passwordHasher.dummyHash)

            if (user == null || hash == null || !passwordMatches) {
                return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    MessageResponse("用户名或密码错误")
                )
            }

            val token = TokenGenerator.generate()
            AuthManager.createSession(user.id, token)
            call.respond(HttpStatusCode.OK, AuthResponse(token, user))
        }

        get("/me") {
            val user = requireUser()
            call.respond(HttpStatusCode.OK, user)
        }

        post("/logout") {
            call.bearerToken()?.let { AuthManager.deleteSession(it) }
            call.respond(HttpStatusCode.OK, MessageResponse("ok"))
        }
    }
}

