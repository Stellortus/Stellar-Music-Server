package top.stellortus.stellar_music_server.util.extensions

import io.ktor.server.application.*
import io.ktor.server.response.*
import top.stellortus.stellar_music_common.dto.MessageResponse

suspend fun ApplicationCall.ok() = respond(MessageResponse("ok"))