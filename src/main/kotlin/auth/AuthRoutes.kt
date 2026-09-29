package top.stellortus.stellar_music_server.auth

import top.stellortus.stellar_music_server.database.auth.AuthRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(repo: AuthRepository, passwordHasher: Argon2PasswordHasher) {

    route("/auth") {

        // 注册：成功即自动登录
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
            if (repo.findByUsername(username) != null) {
                return@post call.respond(
                    HttpStatusCode.Conflict,
                    MessageResponse("用户名已存在")
                )
            }

            val user = repo.createUser(username, passwordHasher.hash(password))
            val token = TokenGenerator.generate()
            repo.createSession(user.id, token)
            call.respond(HttpStatusCode.OK, AuthResponse(token, user.toResponse()))
        }

        // 登录
        post("/login") {
            val req = call.parseOrNull<LoginRequest>()
            val username = req?.username
            val password = req?.password ?: ""

            val credentials = username?.takeIf { it.isNotEmpty() }?.let { repo.findCredentials(it) }
            val user = credentials?.first
            val hash = credentials?.second

            // 用户不存在时也拿假哈希算一次，让两条失败路径耗时一致，避免用响应时间枚举用户名
            val passwordMatches = passwordHasher.verify(password, hash ?: passwordHasher.dummyHash)

            if (user == null || hash == null || !passwordMatches) {
                return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    MessageResponse("用户名或密码错误")
                )
            }

            val token = TokenGenerator.generate()
            repo.createSession(user.id, token)
            call.respond(HttpStatusCode.OK, AuthResponse(token, user.toResponse()))
        }

        // 当前用户信息（需要认证）
        get("/me") {
            val user = call.authenticate(repo)
                ?: return@get call.respond(
                    HttpStatusCode.Unauthorized,
                    MessageResponse("未登录或 token 无效")
                )
            call.respond(HttpStatusCode.OK, user.toResponse())
        }

        // 登出（幂等：token 不存在也返回 200）
        post("/logout") {
            call.bearerToken()?.let { repo.deleteSession(it) }
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
