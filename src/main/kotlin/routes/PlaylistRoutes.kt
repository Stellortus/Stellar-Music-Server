package top.stellortus.stellar_music_server.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import top.stellortus.stellar_music_common.dto.*
import top.stellortus.stellar_music_server.util.extensions.*

private const val PLAYLIST_NOT_FOUND_MESSAGE = "歌单不存在"

fun Route.playlistRoutes() {
    route("/playlists") {
        post {
            val user = requireUser()
            val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
            val request = if (contentLength == null || contentLength == 0L) {
                CreatePlaylistRequest()
            } else {
                try {
                    call.receive<CreatePlaylistRequest>()
                } catch (e: Exception) {
                    call.warn("歌单创建被拒: 请求体无法解析: ${e.message}")
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        MessageResponse("请求体无法解析")
                    )
                }
            }

            val playlist = playlistRepo.create(user.id, request.name)
            call.info("歌单创建: id=${playlist.id} name=${playlist.name} owner=${user.username}")
            call.respond(playlist.toResponse(trackCount = 0))
        }

        get {
            requireUser()
            val playlists = playlistRepo.listAll()
            call.info("歌单列表: 返回=${playlists.size} 个")
            call.respond(playlists.map { it.toResponse() })
        }

        route("/{id}") {

            get {
                requireUser()

                val id = call.id
                val playlist = playlistRepo.get(id)
                val tracks = playlistRepo.tracksOf(id)
                call.info("歌单详情: id=$id name=${playlist.name} 曲目=${tracks.size} 首")
                call.respond(playlist.toDetailResponse(tracks))
            }

            delete {
                requireUser()
                val id = call.id
                playlistRepo.delete(id)
                call.info("删除歌单: id=$id")
                call.ok()
            }

            put {
                requireUser()
                val id = call.id
                val request = try {
                    call.receive<UpdatePlaylistRequest>()
                } catch (e: Exception) {
                    call.warn("歌单更新被拒: 请求体无法解析 id=$id: ${e.message}")
                    return@put call.respond(
                        HttpStatusCode.BadRequest,
                        MessageResponse("请求体无法解析")
                    )
                }

                val updated = playlistRepo.update(id, request.name, request.description, request.coverPath)
                if (!updated) {
                    call.warn("歌单更新失败: id=$id 不存在")
                    return@put call.respond(
                        HttpStatusCode.NotFound,
                        MessageResponse(PLAYLIST_NOT_FOUND_MESSAGE)
                    )
                }

                val playlist = playlistRepo.get(id)
                call.info(
                    "歌单更新: id=$id name=${playlist.name} " +
                            "改name=${request.name != null} 改description=${request.description != null} " +
                            "改coverPath=${request.coverPath != null}"
                )
                call.ok()
            }
            route("/tracks") {
                post {
                    requireUser()
                    val id = call.id
                    val request = call.tryReceive<AddTracksRequest>()
                    playlistRepo.addTracks(id, request.trackIds)
                    call.info(
                        "歌单加歌: id=$id 请求=${request.trackIds.size} 个 "
                    )
                    call.ok()
                }
                delete("/{trackId}") {
                    requireUser()
                    val id = call.id
                    val trackId = call.trackId
                    playlistRepo.removeTrack(id, trackId)
                    call.info(
                        "歌单移除: id=$id trackId=$trackId"
                    )
                    call.ok()
                }
            }
        }
    }
}
