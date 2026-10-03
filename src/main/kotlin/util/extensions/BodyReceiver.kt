package top.stellortus.stellar_music_server.util.extensions

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.request.receive


suspend inline fun <reified T> ApplicationCall.tryReceive(): T =
    try {
        receive<T>()
    } catch (e: Exception) {
        application.log.warn("请求体解析失败: ${e.message}")
        throw e
    }
