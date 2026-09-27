package top.stellortus.stellar_music_server

import database.song.Track
import database.song.TrackRepository
import database.song.TrackSort
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
import java.io.File

fun Application.configureRouting() {

    val rootDir =
        if (System.getProperty("os.name").lowercase().contains("win")) "src/main/resources"
        else "/srv/Stellar-Music-Server"

    val repo = TrackRepository()
    repo.init("$rootDir/db/music.db")

    val regexPattern = "^[^\\p{P}][\\s\\S]*\$"

    install(PartialContent)
    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        })
    }
    routing {
        get("/latest") {
            val dir = File(rootDir, "apks")
            val latestApkName = File(dir, "latest_file_name")
                .takeIf(File::exists)
                ?.readText() ?: return@get call.respond(HttpStatusCode.BadRequest)

            val latestApk = File(dir, latestApkName)
                .takeIf(File::exists) ?: return@get call.respond(HttpStatusCode.NotFound)

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName, latestApkName
                ).toString()
            )
            call.respondFile(latestApk)
        }
        get("/") {
            call.respondText("Hello World!", ContentType.Text.Plain)
        }
        get("/regex") {
            call.respondText(regexPattern)
        }
        get("/demo") {
            call.respondText("HELLO WORLD!")
        }
        get("/file") {
            call.respondFile(File("$rootDir/file.txt"))
        }
        get("/track") {
            call.respondFile(File("$rootDir/muic.mp3"))
        }
        get("/track_list") {
            val sort = TrackSort.valueOf(call.request.queryParameters["sort"] ?: "NEWEST")
            val songList = repo.top(10, sort)
            call.respond(songList)
        }

        route("/track/{id}") {
            get {
                val id = call.parameters["id"]?.toIntOrNull() ?: 0
                val song = repo.get(id) ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respondFile(
                    File("$rootDir/tracks/${song.fileName}")
                        .takeIf { it.exists() } ?: File("$rootDir/music.mp3"))
            }
            post {
                val multipart = call.receiveMultipart()
                var savedName: String? = null

                val uploader = call.request.headers["Uploader"] ?: "Stellortus"

                multipart.forEachPart { part ->
                    try {
                        when (part) {
                            is PartData.FileItem -> {
                                val originalName = part.originalFileName ?: return@forEachPart

                                if (!originalName.matches(Regex(regexPattern))) {
                                    call.respond(
                                        HttpStatusCode.BadRequest,
                                        "文件名非法"
                                    )
                                    return@forEachPart
                                }

                                val file = File("$rootDir/tracks", originalName)
                                file.outputStream().use { output ->
                                    part.provider().copyTo(output)
                                }
                                val audioFile = AudioFileIO.read(file)
                                savedName = originalName
                                repo.upload(
                                    Track(
                                        title = savedName,
                                        artists = audioFile.tag.getAll(FieldKey.ARTIST) ?: listOf("未知歌手"),
                                        fileName = savedName,
                                        uploader = uploader,
                                        coverPath = "",
                                        lyricsPath = ""
                                    )
                                )
                            }

                            is PartData.FormItem -> {
                                println("表单字段: ${part.name} = ${part.value}")
                            }

                            else -> { /* Ignore */
                            }
                        }
                    } finally {
                        part.release()
                    }
                }

                if (savedName != null) {
                    call.respondText("上传成功: $savedName")
                } else {
                    call.respond(HttpStatusCode.BadRequest, "未收到文件")
                }
            }
            delete {
                val id = call.parameters["id"]?.toIntOrNull()
                id?.let {
                    val song = repo.get(id) ?: return@delete call.respond(HttpStatusCode.NotFound)
                    repo.delete(it)
                    File("$rootDir/tracks/${song.fileName}").takeIf { it.exists() }?.delete()
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}