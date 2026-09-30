package top.stellortus.stellar_music_server.routes

import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import top.stellortus.stellar_music_server.auth.MessageResponse
import top.stellortus.stellar_music_server.config.AppPaths.mediaDir
import top.stellortus.stellar_music_server.util.PathSafety
import java.io.File
import kotlin.text.startsWith

val apksDir = File(mediaDir, "apks")

fun Route.downloadApkRoutes() {
    get("/download/{version}") {
        val version = call.parameters["version"] ?: return@get call.respond(HttpStatusCode.BadRequest)

        val apkName =
            if (version == "latest") {
                File(apksDir, "latest_file_name")
                    .takeIf(File::exists)
                    ?.readText()?.trim() ?: return@get call.respond(HttpStatusCode.BadRequest)
            } else {
                val fullVersion = if (version.startsWith('v')) version else "v$version"
                "Stellar_Music_${fullVersion}_release.apk"
            }

        val apkFile = PathSafety.resolveWithin(apksDir, apkName)
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                MessageResponse("版本号非法")
            )

        call.application.log.info(
            "APK 下载: version=$version 文件=${apkFile.absolutePath} 存在=${apkFile.isFile}"
        )

        if (!apkFile.exists()) return@get call.respond(HttpStatusCode.NotFound)

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName, apkFile.name
            ).toString()
        )
        call.respondFile(apkFile)
    }
}