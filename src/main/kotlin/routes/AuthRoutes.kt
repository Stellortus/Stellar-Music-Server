package top.stellortus.stellar_music_server.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import top.stellortus.stellar_music_common.dto.AuthResponse
import top.stellortus.stellar_music_common.dto.LoginRequest
import top.stellortus.stellar_music_common.dto.MessageResponse
import top.stellortus.stellar_music_common.dto.RegisterRequest
import top.stellortus.stellar_music_server.auth.TokenGenerator
import top.stellortus.stellar_music_server.util.extensions.*

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
            if (authRepo.findByUsername(username) != null) {
                return@post call.respond(
                    HttpStatusCode.Conflict,
                    MessageResponse("用户名已存在")
                )
            }

            val user = authRepo.createUser(username, passwordHasher.hash(password))
            val token = TokenGenerator.generate()
            authRepo.createSession(user.id, token)
            call.respond(HttpStatusCode.OK, AuthResponse(token, user))
        }

        post("/login") {
            val req = call.tryReceive<LoginRequest>()
            val username = req.username
            val password = req.password

            val credentials = username.takeIf { it.isNotEmpty() }?.let { authRepo.findCredentials(it) }
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
            authRepo.createSession(user.id, token)
            call.respond(HttpStatusCode.OK, AuthResponse(token, user))
        }

        get("/me") {
            val user = requireUser()
            call.respond(HttpStatusCode.OK, user)
        }

        post("/logout") {
            call.bearerToken()?.let { authRepo.deleteSession(it) }
            call.respond(HttpStatusCode.OK, MessageResponse("ok"))
        }
    }
}

