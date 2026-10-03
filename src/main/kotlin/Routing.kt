package top.stellortus.stellar_music_server

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import top.stellortus.stellar_music_common.dto.MessageResponse
import top.stellortus.stellar_music_server.auth.Argon2PasswordHasher
import top.stellortus.stellar_music_server.config.AppPaths.mediaDir
import top.stellortus.stellar_music_server.config.AppPaths.trackDataBasePath
import top.stellortus.stellar_music_server.config.AppPaths.userDatabasePath
import top.stellortus.stellar_music_server.database.auth.AuthRepository
import top.stellortus.stellar_music_server.database.playlist.PlaylistRepository
import top.stellortus.stellar_music_server.database.track.TrackRepository
import top.stellortus.stellar_music_server.database.track.TrackSort
import top.stellortus.stellar_music_server.exceptions.IllegalParamsException
import top.stellortus.stellar_music_server.exceptions.PermissionDeniedException
import top.stellortus.stellar_music_server.exceptions.ResourceNotExistException
import top.stellortus.stellar_music_server.exceptions.UploadingException
import top.stellortus.stellar_music_server.routes.*
import top.stellortus.stellar_music_server.util.extensions.RouteDependencies
import top.stellortus.stellar_music_server.util.extensions.error
import top.stellortus.stellar_music_server.util.extensions.installRouteDependencies
import java.io.File
import kotlin.enums.enumEntries

fun Application.configureRouting() {
    val trackRepo = TrackRepository()
    trackRepo.init(trackDataBasePath)

    val authRepo = AuthRepository()
    authRepo.init(userDatabasePath)

    val playlistRepo = PlaylistRepository(trackRepo.database)
    playlistRepo.init()

    val passwordHasher = Argon2PasswordHasher()

    val tracksDir = File(mediaDir, "tracks")

    if (!tracksDir.isDirectory && !tracksDir.mkdirs()) {
        log.warn("上传目录不可用，上传将会失败: ${tracksDir.path}")
    }

    install(PartialContent)
    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        })
    }
    install(StatusPages) {
        exception<PermissionDeniedException> { call, exception ->
            call.respond(HttpStatusCode.Unauthorized, MessageResponse(exception.message ?: ""))
        }
        exception<IllegalStateException> { call, exception ->
            call.error("未处理的非法状态，返回 500", exception)
            call.respond(HttpStatusCode.InternalServerError, MessageResponse("服务器内部错误"))
        }
        exception<IllegalParamsException> { call, exception ->
            call.error(exception.message)
            call.respond(HttpStatusCode.BadRequest, MessageResponse(exception.message ?: ""))
        }
        exception<ResourceNotExistException> { call, exception ->
            call.error(exception.message)
            call.respond(HttpStatusCode.NotFound, MessageResponse(exception.message ?: ""))
        }
        exception<UploadingException> { call, exception ->
            call.error(exception.message)
            call.respond(HttpStatusCode.BadRequest, MessageResponse(exception.message ?: ""))
        }
    }


    intercept(ApplicationCallPipeline.Monitoring) {
        val startedAt = System.currentTimeMillis()
        try {
            proceed()
        } finally {
            val status = call.response.status()?.value?.toString() ?: "-"
            val elapsed = System.currentTimeMillis() - startedAt
            call.application.log.info(
                "${call.request.httpMethod.value} ${call.request.uri} -> $status (${elapsed}ms)"
            )
        }
    }
    routing {
        installRouteDependencies(
            RouteDependencies(
                tracksDir,
                trackRepo,
                playlistRepo,
                authRepo,
                passwordHasher
            )
        )
        downloadApkRoutes()
        authRoutes()
        trackRoutes()
        playlistRoutes()
        adminRoutes()
        get("/track_list") {
            val sortParam = call.request.queryParameters["sort"]
            val start = call.request.queryParameters["start"]?.toIntOrNull() ?: 0
            val sort = enumEntries<TrackSort>().firstOrNull { it.name == sortParam }
            val songList = trackRepo.top(10, sort, start = start)
            call.application.log.info("曲目列表: sort=$sortParam 返回=${songList.size} 首")
            call.respond(songList)
        }
    }
}
