package top.stellortus.stellar_music_server.util.extensions

import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log


//fun ApplicationCall.debug(msg: String) = this.application.log.debug(msg)
fun ApplicationCall.info(msg: String) = this.application.log.info(msg)
fun ApplicationCall.warn(msg: String) = this.application.log.warn(msg)
fun ApplicationCall.error(msg: String?) = this.application.log.error(msg)
fun ApplicationCall.error(format: String, arg: Any) = this.application.log.error(format, arg)