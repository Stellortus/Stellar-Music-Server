package top.stellortus.stellar_music_server.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.*
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import top.stellortus.stellar_music_common.dto.MessageResponse
import top.stellortus.stellar_music_common.dto.Track
import top.stellortus.stellar_music_server.exceptions.UploadingException
import top.stellortus.stellar_music_server.util.PathSafety
import top.stellortus.stellar_music_server.util.extensions.*
import java.io.OutputStream


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

fun Route.trackRoutes() {
    route("/track/{id}") {
        get {
            val id = call.id

            val song = trackRepo.get(id)

            call.info("请求音频: id=$id fileName=${song.fileName}")

            val songFile = PathSafety.resolveWithin(tracksDir, song.fileName)
            if (songFile == null) {
                call.warn("请求音频失败: id=$id 文件名非法 fileName=${song.fileName}")
                return@get call.respond(HttpStatusCode.NotFound)
            }

            if (songFile.isFile) {
                call.info("返回音频: ${songFile.absolutePath} 大小=${songFile.length()}")
                return@get call.respondFile(songFile)
            }

            call.warn("音频文件缺失: id=$id 期望路径=${songFile.absolutePath}")
            call.respond(HttpStatusCode.NotFound)
        }
        post {
            val user = requireUser()
            val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
            call.info(
                "上传开始: user=${user.username} " +
                        "contentType=${call.request.header(HttpHeaders.ContentType)} " +
                        "contentLength=$contentLength 上限=$MAX_UPLOAD_BYTES"
            )

            if (contentLength != null && contentLength > MAX_UPLOAD_BYTES) {
                call.warn("上传被拒: Content-Length=$contentLength 超过上限 $MAX_UPLOAD_BYTES")

                return@post call.respond(
                    HttpStatusCode.PayloadTooLarge,
                    MessageResponse(UPLOAD_TOO_LARGE_MESSAGE)
                )
            }

            val multipart = try {
                call.receiveMultipart()
            } catch (e: Exception) {
                call.error("multipart 解析失败", e)
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
                    call.info("收到 part #$partCount: type=${part::class.simpleName} name=${part.name}")

                    if (failure != null || savedName != null) {
                        call.info("已处理完成，忽略剩余 part")
                        return@forEachPart
                    }

                    when (part) {
                        is PartData.FileItem -> {
                            val originalName = part.originalFileName ?: throw UploadingException("文件 part 缺少 filename，跳过")
                            call.info("文件 part: name=${part.name} fileName=$originalName")

                            val file = PathSafety.resolveWithin(tracksDir, originalName)
                                ?: throw UploadingException("文件名非法: $originalName")

                            call.info("落盘路径=${file.absolutePath}")

                            trackRepo.existsByFileName(file.name)
                                .ifTrue { throw UploadingException("同名文件已存在: ${file.name}") }

                            try {
                                file.outputStream().use { output ->
                                    part.provider().copyTo(LimitedOutputStream(output, MAX_UPLOAD_BYTES))
                                }
                            } catch (_: UploadTooLargeException) {
                                file.delete()
                                call.warn("上传被拒: 流式写入超过上限 $MAX_UPLOAD_BYTES")
                                failure = HttpStatusCode.PayloadTooLarge to
                                        MessageResponse(UPLOAD_TOO_LARGE_MESSAGE)
                                return@forEachPart
                            } catch (e: Exception) {
                                file.delete()
                                call.error("音频写入失败: ${file.path}", e)
                                failure = HttpStatusCode.InternalServerError to
                                        MessageResponse("文件写入失败")
                                return@forEachPart
                            }

                            call.info("音频已落盘: ${file.absolutePath} 大小=${file.length()}")

                            val audioFile = try {
                                AudioFileIO.read(file)
                            } catch (e: Exception) {
                                file.delete()
                                call.warn("音频文件解析失败: ${file.name}: ${e.message}")
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
                                call.info("音频已入库: id=${saved.id} file=${saved.fileName} uploader=${saved.uploader}")
                            } catch (e: Exception) {
                                file.delete()
                                call.error("音频入库失败: ${file.name}", e)
                                failure = HttpStatusCode.InternalServerError to
                                        MessageResponse("文件入库失败")
                                return@forEachPart
                            }

                            savedName = file.name
                        }

                        is PartData.FormItem -> {
                            call.info("表单字段: ${part.name} = ${part.value}")
                        }

                        else -> { /* Ignore */
                        }
                    }
                } finally {
                    part.release()
                }
            }

            val error = failure
            call.info("上传结束: partCount=$partCount savedName=$savedName 失败=${error?.second?.message}")

            when {
                error != null -> call.respond(error.first, error.second)
                savedName != null -> call.respond(MessageResponse("上传成功: $savedName"))
                else -> call.respond(HttpStatusCode.BadRequest, MessageResponse("未收到文件"))
            }
        }
    }
}