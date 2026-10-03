package top.stellortus.stellar_music_server.util.extensions

import io.ktor.server.application.ApplicationCall
import top.stellortus.stellar_music_server.exceptions.EmptyParamException
import top.stellortus.stellar_music_server.exceptions.InvalidParamException
import kotlin.text.toIntOrNull

val ApplicationCall.id: Int
    get() = this.intParam("id")

val ApplicationCall.trackId: Int
    get() = this.intParam("trackId")

private fun ApplicationCall.intParam(name: String): Int =
    parameters[name]?.let {
        it.toIntOrNull() ?: throw InvalidParamException(name, it)
    } ?: throw EmptyParamException(name)