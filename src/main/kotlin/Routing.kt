package top.stellortus.stellar_music_server

import top.stellortus.stellar_music_server.auth.Argon2PasswordHasher
import top.stellortus.stellar_music_server.routes.authRoutes
import top.stellortus.stellar_music_server.database.auth.AuthRepository
import top.stellortus.stellar_music_server.database.playlist.PlaylistRepository
import top.stellortus.stellar_music_server.database.track.TrackRepository
import top.stellortus.stellar_music_server.database.track.TrackSort
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import top.stellortus.stellar_music_server.config.AppPaths.mediaDir
import top.stellortus.stellar_music_server.config.AppPaths.trackDataBasePath
import top.stellortus.stellar_music_server.config.AppPaths.userDatabasePath
import top.stellortus.stellar_music_server.routes.downloadApkRoutes
import top.stellortus.stellar_music_server.routes.playlistRoutes
import top.stellortus.stellar_music_server.routes.trackRoutes
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
        downloadApkRoutes()
        authRoutes(authRepo, passwordHasher)
        trackRoutes(trackRepo, tracksDir, authRepo)
        playlistRoutes(playlistRepo, authRepo)
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
