package top.stellortus.stellar_music_server.routes

import top.stellortus.stellar_music_server.database.auth.AuthRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import top.stellortus.stellar_music_server.auth.Argon2PasswordHasher
import top.stellortus.stellar_music_server.auth.AuthResponse
import top.stellortus.stellar_music_server.auth.LoginRequest
import top.stellortus.stellar_music_server.auth.MessageResponse
import top.stellortus.stellar_music_server.auth.RegisterRequest
import top.stellortus.stellar_music_server.auth.TokenGenerator
import top.stellortus.stellar_music_server.auth.bearerToken
import top.stellortus.stellar_music_server.auth.requireUser
import top.stellortus.stellar_music_server.auth.toResponse

fun Route.authRoutes(authRepo: AuthRepository, passwordHasher: Argon2PasswordHasher) {

    route("/auth") {

        post("/register") {
            val req = call.parseOrNull<RegisterRequest>()
            val username = req?.username
            val password = req?.password

            if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
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
            call.respond(HttpStatusCode.OK, AuthResponse(token, user.toResponse()))
        }

        post("/login") {
            val req = call.parseOrNull<LoginRequest>()
            val username = req?.username
            val password = req?.password ?: ""

            val credentials = username?.takeIf { it.isNotEmpty() }?.let { authRepo.findCredentials(it) }
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
            call.respond(HttpStatusCode.OK, AuthResponse(token, user.toResponse()))
        }

        get("/me") {
            val user = call.requireUser(authRepo) ?: return@get
            call.respond(HttpStatusCode.OK, user.toResponse())
        }

        post("/logout") {
            call.bearerToken()?.let { authRepo.deleteSession(it) }
            call.respond(HttpStatusCode.OK, MessageResponse("ok"))
        }
    }
}

private suspend inline fun <reified T> ApplicationCall.parseOrNull(): T? =
    try {
        receive<T>()
    } catch (e: Exception) {
        application.log.warn("请求体解析失败: ${e.message}")
        null
    }
