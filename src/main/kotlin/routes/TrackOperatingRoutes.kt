package top.stellortus.stellar_music_server.routes

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.log
import io.ktor.server.request.header
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.jvm.javaio.copyTo
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import top.stellortus.stellar_music_server.auth.MessageResponse
import top.stellortus.stellar_music_server.auth.requireUser
import top.stellortus.stellar_music_server.database.auth.AuthRepository
import top.stellortus.stellar_music_server.database.track.Track
import top.stellortus.stellar_music_server.database.track.TrackRepository
import top.stellortus.stellar_music_server.util.PathSafety
import java.io.File
import java.io.OutputStream
import kotlin.text.toIntOrNull


private const val MAX_UPLOAD_BYTES = 20L * 1024 * 1024
private const val UPLOAD_TOO_LARGE_MESSAGE = "文件过大，最大 20MiB"

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

fun Route.trackRoutes(trackRepo: TrackRepository, tracksDir: File, authRepo: AuthRepository) {
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
            call.respond(HttpStatusCode.NotFound)
        }
        post {
            val log = call.application.log

            val user = call.requireUser(authRepo) ?: return@post

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
                savedName != null -> call.respond(MessageResponse("上传成功: $savedName"))
                else -> call.respond(HttpStatusCode.BadRequest, MessageResponse("未收到文件"))
            }
        }
        delete {
            val log = call.application.log

            call.requireUser(authRepo) ?: return@delete

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