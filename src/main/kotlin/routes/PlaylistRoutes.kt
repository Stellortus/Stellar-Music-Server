package top.stellortus.stellar_music_server.routes

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import top.stellortus.stellar_music_server.auth.MessageResponse
import top.stellortus.stellar_music_server.auth.requireUser
import top.stellortus.stellar_music_server.database.auth.AuthRepository
import top.stellortus.stellar_music_server.database.playlist.PlaylistRepository
import top.stellortus.stellar_music_server.playlist.AddTracksRequest
import top.stellortus.stellar_music_server.playlist.AddTracksResponse
import top.stellortus.stellar_music_server.playlist.CreatePlaylistRequest
import top.stellortus.stellar_music_server.playlist.UpdatePlaylistRequest
import top.stellortus.stellar_music_server.playlist.toDetailResponse
import top.stellortus.stellar_music_server.playlist.toResponse
import kotlin.text.toIntOrNull

private const val PLAYLIST_NOT_FOUND_MESSAGE = "歌单不存在"

fun Route.playlistRoutes(playlistRepo: PlaylistRepository, authRepo: AuthRepository) {
    route("/playlists") {
        post {
            val log = call.application.log

            val user = call.requireUser(authRepo) ?: return@post

            val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
            val request = if (contentLength == null || contentLength == 0L) {
                CreatePlaylistRequest()
            } else {
                try {
                    call.receive<CreatePlaylistRequest>()
                } catch (e: Exception) {
                    log.warn("歌单创建被拒: 请求体无法解析: ${e.message}")
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        MessageResponse("请求体无法解析")
                    )
                }
            }

            val playlist = playlistRepo.create(user.id, request.name)
            log.info("歌单创建: id=${playlist.id} name=${playlist.name} owner=${user.username}")
            call.respond(playlist.toResponse(trackCount = 0))
        }

        get {
            val log = call.application.log

            call.requireUser(authRepo) ?: return@get

            val playlists = playlistRepo.listAll()
            log.info("歌单列表: 返回=${playlists.size} 个")
            call.respond(playlists.map { it.toResponse() })
        }

        get("/{id}") {
            val log = call.application.log

            call.requireUser(authRepo) ?: return@get

            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                log.warn("歌单详情被拒: id 非法 value=${call.parameters["id"]}")
                return@get call.respond(HttpStatusCode.BadRequest, MessageResponse("id 非法"))
            }

            val playlist = playlistRepo.get(id)
            if (playlist == null) {
                log.warn("歌单详情失败: id=$id 不存在")
                return@get call.respond(HttpStatusCode.NotFound, MessageResponse(PLAYLIST_NOT_FOUND_MESSAGE))
            }

            val tracks = playlistRepo.tracksOf(id)
            log.info("歌单详情: id=$id name=${playlist.name} 曲目=${tracks.size} 首")
            call.respond(playlist.toDetailResponse(tracks))
        }

        patch("/{id}") {
            val log = call.application.log

            call.requireUser(authRepo) ?: return@patch

            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                log.warn("歌单更新被拒: id 非法 value=${call.parameters["id"]}")
                return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("id 非法"))
            }

            val request = try {
                call.receive<UpdatePlaylistRequest>()
            } catch (e: Exception) {
                log.warn("歌单更新被拒: 请求体无法解析 id=$id: ${e.message}")
                return@patch call.respond(
                    HttpStatusCode.BadRequest,
                    MessageResponse("请求体无法解析")
                )
            }

            val updated = playlistRepo.update(id, request.name, request.description, request.coverPath)
            if (!updated) {
                log.warn("歌单更新失败: id=$id 不存在")
                return@patch call.respond(
                    HttpStatusCode.NotFound,
                    MessageResponse(PLAYLIST_NOT_FOUND_MESSAGE)
                )
            }

            val playlist = playlistRepo.get(id)!!
            log.info(
                "歌单更新: id=$id name=${playlist.name} " +
                        "改name=${request.name != null} 改description=${request.description != null} " +
                        "改coverPath=${request.coverPath != null}"
            )
            call.respond(playlist.toResponse(playlistRepo.countTracks(id)))
        }

        delete("/{id}") {
            val log = call.application.log

            call.requireUser(authRepo) ?: return@delete

            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                log.warn("歌单删除被拒: id 非法 value=${call.parameters["id"]}")
                return@delete call.respond(HttpStatusCode.BadRequest, MessageResponse("id 非法"))
            }

            val deleted = playlistRepo.delete(id)
            log.info("歌单删除: id=$id 存在并已删除=$deleted")
            call.respond(MessageResponse("ok"))
        }

        post("/{id}/tracks") {
            val log = call.application.log

            call.requireUser(authRepo) ?: return@post

            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                log.warn("歌单加歌被拒: id 非法 value=${call.parameters["id"]}")
                return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("id 非法"))
            }

            val request = try {
                call.receive<AddTracksRequest>()
            } catch (e: Exception) {
                log.warn("歌单加歌被拒: 请求体无法解析 id=$id: ${e.message}")
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    MessageResponse("请求体无法解析")
                )
            }

            if (playlistRepo.get(id) == null) {
                log.warn("歌单加歌失败: id=$id 不存在")
                return@post call.respond(
                    HttpStatusCode.NotFound,
                    MessageResponse(PLAYLIST_NOT_FOUND_MESSAGE)
                )
            }

            val result = playlistRepo.addTracks(id, request.trackIds)
            log.info(
                "歌单加歌: id=$id 请求=${request.trackIds.size} 个 " +
                        "新增=${result.added} 忽略=${result.ignored}"
            )
            call.respond(AddTracksResponse("ok", result.added, result.ignored))
        }

        delete("/{id}/tracks/{trackId}") {
            val log = call.application.log

            call.requireUser(authRepo) ?: return@delete

            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                log.warn("歌单移除曲目被拒: id 非法 value=${call.parameters["id"]}")
                return@delete call.respond(HttpStatusCode.BadRequest, MessageResponse("id 非法"))
            }

            val trackId = call.parameters["trackId"]?.toIntOrNull()
            if (trackId == null) {
                log.warn("歌单移除曲目被拒: trackId 非法 value=${call.parameters["trackId"]}")
                return@delete call.respond(HttpStatusCode.BadRequest, MessageResponse("trackId 非法"))
            }

            val removed = playlistRepo.removeTrack(id, trackId)
            log.info("歌单移除曲目: id=$id trackId=$trackId 实际移除=$removed")
            call.respond(MessageResponse("ok"))
        }
    }
}
