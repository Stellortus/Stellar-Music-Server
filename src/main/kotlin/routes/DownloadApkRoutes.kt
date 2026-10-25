package top.stellortus.stellar_music_server.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import top.stellortus.stellar_music_common.dto.MessageResponse
import top.stellortus.stellar_music_server.config.AppPaths.mediaDir
import top.stellortus.stellar_music_server.util.PathSafety
import top.stellortus.stellar_music_server.util.extensions.info
import java.io.File

val apksDir = File(mediaDir, "apks")

fun Route.downloadApkRoutes() {
    get("/download/{version}") {
        val version = call.parameters["version"] ?: return@get call.respond(HttpStatusCode.BadRequest)

        val rawVersionName = if (version == "latest") {
            File(apksDir, "latest_version_name")
                .takeIf(File::exists)
                ?.readText()?.trim() ?: return@get call.respond(HttpStatusCode.BadRequest)
        } else {
            version
        }

        val versionName = if (rawVersionName.startsWith('v')) rawVersionName else "v$rawVersionName"

        val apkName = "Stellar_Music_${versionName}_release.apk"
        val apkFile = PathSafety.resolveWithin(apksDir, apkName)
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                MessageResponse("版本号非法")
            )

        call.info("APK 下载: version=$versionName 文件=${apkFile.absolutePath} 存在=${apkFile.isFile}")

        if (!apkFile.exists()) return@get call.respond(HttpStatusCode.NotFound)

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName, apkFile.name
            ).toString()
        )
        call.respondFile(apkFile)
    }

    route("/download_api") {
        get("/latest_version_name") {
            val versionName = File(apksDir, "latest_version_name")
                .takeIf(File::exists)
                ?.readText()?.trim() ?: call.respond(HttpStatusCode.BadRequest)
            call.respond(versionName)
        }
        get("/latest_version_code") {
            val versionCode = File(apksDir, "latest_version_code")
                .takeIf(File::exists)
                ?.readText()?.trim() ?: call.respond(HttpStatusCode.BadRequest)
            call.respond(versionCode)
        }
    }
}