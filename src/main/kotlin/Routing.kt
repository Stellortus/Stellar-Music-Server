package top.stellortus.stellar_music_server

import top.stellortus.stellar_music_server.auth.Argon2PasswordHasher
import top.stellortus.stellar_music_server.auth.MessageResponse
import top.stellortus.stellar_music_server.auth.authenticate
import top.stellortus.stellar_music_server.auth.authRoutes
import top.stellortus.stellar_music_server.database.auth.AuthRepository
import top.stellortus.stellar_music_server.database.track.Track
import top.stellortus.stellar_music_server.database.track.TrackRepository
import top.stellortus.stellar_music_server.database.track.TrackSort
import top.stellortus.stellar_music_server.util.PathSafety
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.json.Json
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import top.stellortus.stellar_music_server.config.AppPaths.mediaDir
import top.stellortus.stellar_music_server.config.AppPaths.trackDataBasePath
import top.stellortus.stellar_music_server.config.AppPaths.userDatabasePath
import java.io.File
import java.io.OutputStream
import kotlin.enums.enumEntries

private const val MAX_UPLOAD_BYTES = 20L * 1024 * 1024
private const val UPLOAD_TOO_LARGE_MESSAGE = "文件过大，最大 20MiB"
private const val UNAUTHORIZED_MESSAGE = "未登录或 token 无效"

/** 写入超过上限时中止上传，由调用方负责删除残留文件 */
private class UploadTooLargeException : Exception()

private class LimitedOutputStream(
    private val delegate: OutputStream,
    private val limit: Long,
) : OutputStream() {

    private var written = 0L

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (written + len > limit) throw UploadTooLargeException()
        delegate.write(b, off, len)
        written += len
    }

    override fun flush() = delegate.flush()

    override fun close() = delegate.close()
}

fun Application.configureRouting() {

    val trackRepo = TrackRepository()
    trackRepo.init(trackDataBasePath)

    val authRepo = AuthRepository()
    authRepo.init(userDatabasePath)

    val passwordHasher = Argon2PasswordHasher()

    val regexPattern = "^[^\\p{P}][\\s\\S]*$"

    val tracksDir = File(mediaDir, "tracks")
    val apksDir = File(mediaDir, "apks")

    // 上传目录可能尚不存在，先建出来，否则写入时会抛 FileNotFoundException
    if (!tracksDir.isDirectory && !tracksDir.mkdirs()) {
        log.warn("上传目录不可用，上传将会失败: ${tracksDir.path}")
    }

    log.info("媒体目录 mediaDir=$mediaDir")
    log.info("音频目录 tracks=${tracksDir.absolutePath} 存在=${tracksDir.isDirectory}")
    log.info("APK 目录 apks=${apksDir.absolutePath} 存在=${apksDir.isDirectory}")
    log.info("曲库数据库=${File(trackDataBasePath).absolutePath}")
    log.info("用户数据库=${File(userDatabasePath).absolutePath}")
    log.info("工作目录=${File(".").absolutePath}")

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
        get("/") {
            call.respondText("Hello World!", ContentType.Text.Plain)
        }
        authRoutes(authRepo, passwordHasher)
        get("/regex") {
            call.respondText(regexPattern)
        }
        get("/demo") {
            call.respondText("HELLO WORLD!")
        }
        get("/file") {
            call.respondFile(File(mediaDir, "file.txt"))
        }
        get("/track") {
            call.respondFile(File(mediaDir, "muic.mp3"))
        }
        get("/track_list") {
            val sortParam = call.request.queryParameters["sort"] ?: "NEWEST"
            val sort = enumEntries<TrackSort>().firstOrNull { it.name == sortParam }
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    MessageResponse("非法排序字段: $sortParam")
                )
            val songList = trackRepo.top(10, sort)
            call.application.log.info("曲目列表: sort=$sortParam 返回=${songList.size} 首")
            call.respond(songList)
        }

        route("/track/{id}") {
            get {
                val log = call.application.log
                val id = call.parameters["id"]?.toIntOrNull() ?: 0

                val song = trackRepo.get(id)
                if (song == null) {
                    log.warn("请求音频失败: id=$id 曲库中无此记录")
                    return@get call.respond(HttpStatusCode.NotFound)
                }

                log.info("请求音频: id=$id fileName=${song.fileName}")

                val songFile = PathSafety.resolveWithin(tracksDir, song.fileName)
                if (songFile == null) {
                    log.warn("请求音频失败: id=$id 文件名非法 fileName=${song.fileName}")
                    return@get call.respond(HttpStatusCode.NotFound)
                }

                if (songFile.isFile) {
                    log.info("返回音频: ${songFile.absolutePath} 大小=${songFile.length()}")
                    return@get call.respondFile(songFile)
                }

                log.warn("音频文件缺失: id=$id 期望路径=${songFile.absolutePath}")

                val fallback = File(mediaDir, "music.mp3")
                if (!fallback.isFile) {
                    log.warn("示例音频也不存在: ${fallback.absolutePath}")
                    return@get call.respond(HttpStatusCode.NotFound)
                }

                log.warn("回落示例音频: ${fallback.absolutePath}")
                call.respondFile(fallback)
            }
            post {
                val log = call.application.log

                val user = call.authenticate(authRepo)
                if (user == null) {
                    val authHeader = call.request.header(HttpHeaders.Authorization)
                    log.warn("上传被拒: 未认证 Authorization=${if (authHeader == null) "缺失" else "存在但不匹配"}")

                    return@post call.respond(
                        HttpStatusCode.Unauthorized,
                        MessageResponse(UNAUTHORIZED_MESSAGE)
                    )
                }

                val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
                log.info(
                    "上传开始: user=${user.username} " +
                        "contentType=${call.request.header(HttpHeaders.ContentType)} " +
                        "contentLength=$contentLength 上限=$MAX_UPLOAD_BYTES"
                )

                if (contentLength != null && contentLength > MAX_UPLOAD_BYTES) {
                    log.warn("上传被拒: Content-Length=$contentLength 超过上限 $MAX_UPLOAD_BYTES")

                    return@post call.respond(
                        HttpStatusCode.PayloadTooLarge,
                        MessageResponse(UPLOAD_TOO_LARGE_MESSAGE)
                    )
                }

                val multipart = try {
                    call.receiveMultipart()
                } catch (e: Exception) {
                    log.error("multipart 解析失败", e)
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        MessageResponse("上传内容无法解析")
                    )
                }

                var savedName: String? = null
                var failure: Pair<HttpStatusCode, MessageResponse>? = null
                var partCount = 0

                multipart.forEachPart { part ->
                    try {
                        partCount++
                        log.info("收到 part #$partCount: type=${part::class.simpleName} name=${part.name}")

                        if (failure != null || savedName != null) {
                            log.info("已处理完成，忽略剩余 part")
                            return@forEachPart
                        }

                        when (part) {
                            is PartData.FileItem -> {
                                val originalName = part.originalFileName
                                log.info("文件 part: name=${part.name} fileName=$originalName")

                                if (originalName == null) {
                                    log.warn("文件 part 缺少 filename，跳过")
                                    return@forEachPart
                                }

                                val file = PathSafety.resolveWithin(tracksDir, originalName)
                                if (file == null) {
                                    log.warn("文件名非法: $originalName")
                                    failure = HttpStatusCode.BadRequest to MessageResponse("文件名非法")
                                    return@forEachPart
                                }

                                log.info("落盘路径=${file.absolutePath}")

                                if (trackRepo.existsByFileName(file.name)) {
                                    log.warn("同名文件已存在: ${file.name}")
                                    failure = HttpStatusCode.Conflict to
                                        MessageResponse("同名文件已存在")
                                    return@forEachPart
                                }

                                try {
                                    file.outputStream().use { output ->
                                        part.provider().copyTo(LimitedOutputStream(output, MAX_UPLOAD_BYTES))
                                    }
                                } catch (_: UploadTooLargeException) {
                                    file.delete()
                                    log.warn("上传被拒: 流式写入超过上限 $MAX_UPLOAD_BYTES")
                                    failure = HttpStatusCode.PayloadTooLarge to
                                        MessageResponse(UPLOAD_TOO_LARGE_MESSAGE)
                                    return@forEachPart
                                } catch (e: Exception) {
                                    file.delete()
                                    log.error("音频写入失败: ${file.path}", e)
                                    failure = HttpStatusCode.InternalServerError to
                                        MessageResponse("文件写入失败")
                                    return@forEachPart
                                }

                                log.info("音频已落盘: ${file.absolutePath} 大小=${file.length()}")

                                val audioFile = try {
                                    AudioFileIO.read(file)
                                } catch (e: Exception) {
                                    file.delete()
                                    log.warn("音频文件解析失败: ${file.name}: ${e.message}")
                                    failure = HttpStatusCode.BadRequest to
                                        MessageResponse("无法解析音频文件")
                                    return@forEachPart
                                }

                                try {
                                    val saved = trackRepo.upload(
                                        Track(
                                            title = file.name,
                                            artists = audioFile.tag?.getAll(FieldKey.ARTIST)
                                                ?.takeIf { it.isNotEmpty() } ?: listOf("未知歌手"),
                                            fileName = file.name,
                                            uploader = user.username,
                                            coverPath = "",
                                            lyricsPath = ""
                                        )
                                    )
                                    log.info("音频已入库: id=${saved.id} file=${saved.fileName} uploader=${saved.uploader}")
                                } catch (e: Exception) {
                                    file.delete()
                                    log.error("音频入库失败: ${file.name}", e)
                                    failure = HttpStatusCode.InternalServerError to
                                        MessageResponse("文件入库失败")
                                    return@forEachPart
                                }

                                savedName = file.name
                            }

                            is PartData.FormItem -> {
                                log.info("表单字段: ${part.name} = ${part.value}")
                            }

                            else -> { /* Ignore */
                            }
                        }
                    } finally {
                        part.release()
                    }
                }

                val error = failure
                log.info("上传结束: partCount=$partCount savedName=$savedName 失败=${error?.second?.message}")

                when {
                    error != null -> call.respond(error.first, error.second)
                    savedName != null -> call.respondText("上传成功: $savedName")
                    else -> call.respond(HttpStatusCode.BadRequest, MessageResponse("未收到文件"))
                }
            }
            delete {
                val log = call.application.log

                val user = call.authenticate(authRepo)
                if (user == null) {
                    val authHeader = call.request.header(HttpHeaders.Authorization)
                    log.warn("删除被拒: 未认证 Authorization=${if (authHeader == null) "缺失" else "存在但不匹配"}")

                    return@delete call.respond(
                        HttpStatusCode.Unauthorized,
                        MessageResponse(UNAUTHORIZED_MESSAGE)
                    )
                }

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    log.warn("删除被拒: id 非法 value=${call.parameters["id"]}")
                    return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        MessageResponse("id 非法")
                    )
                }

                val song = trackRepo.get(id)
                if (song == null) {
                    log.warn("删除失败: id=$id 曲库中无此记录")
                    return@delete call.respond(HttpStatusCode.NotFound)
                }

                trackRepo.delete(id)
                val fileDeleted = PathSafety.resolveWithin(tracksDir, song.fileName)
                    ?.takeIf(File::exists)?.delete() ?: false

                log.info("删除完成: id=$id file=${song.fileName} 磁盘文件已删除=$fileDeleted")
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
