package top.stellortus.stellar_music_server.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.stellortus.stellar_music_common.dto.*
import top.stellortus.stellar_music_server.db.manager.AuthManager
import top.stellortus.stellar_music_server.db.manager.PlaylistManager
import top.stellortus.stellar_music_server.db.manager.TrackManager
import top.stellortus.stellar_music_server.util.PathSafety
import top.stellortus.stellar_music_server.util.extensions.*
import top.stellortus.stellar_music_server.util.readLogTail
import java.io.File
import java.io.FileNotFoundException
import kotlin.enums.enumEntries

private const val DEFAULT_PAGE_SIZE = 50
private const val MAX_PAGE_SIZE = 200
private const val ADMIN_PAGE_RESOURCE = "admin/index.html"


fun Route.adminRoutes() {

    get("/admin") { call.respondText(adminPageHtml, ContentType.Text.Html.withCharset(Charsets.UTF_8)) }

    route("/admin/api") {

        get("/logs") {
            requireAdmin()
            call.response.header(HttpHeaders.CacheControl, "no-store")
            val snapshot = try {
                withContext(Dispatchers.IO) { readLogTail(File("app.log")) }
            } catch (_: FileNotFoundException) {
                return@get call.respond(HttpStatusCode.NotFound, MessageResponse("日志文件不存在或无法读取"))
            }
            call.respond(snapshot)
        }

        get("/meta") {
            val me = requireAdmin()
            val assignable = UserLevel.assignableBelow(me.level)
            call.respond(
                AdminMetaResponse(
                    me = me,
                    minLevel = UserLevel.Admin.value,
                    allLevels = enumEntries<UserLevel>().map { LevelOption(it.value, it.name) },
                    assignableLevels = assignable.map { it.value },
                )
            )
        }

        get("/tracks") {
            requireAdmin()
            val keyword = call.request.queryParameters["query"]
            val offset = call.request.queryParameters["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_PAGE_SIZE)
                .coerceIn(1, MAX_PAGE_SIZE)

            val items = TrackManager.search(keyword, offset, limit)
            val total = TrackManager.count(keyword)
            call.info("后台歌曲列表: query=${keyword ?: "-"} offset=$offset limit=$limit 返回=${items.size}/$total")
            call.respond(TrackListResponse(items, total))
        }

        patch("/tracks/{id}") {
            requireAdmin()
            val id = call.id

            val request = call.tryReceive<UpdateTrackRequest>()

            if (request.title == null && request.artists == null &&
                request.coverPath == null && request.lyricsPath == null
            ) {
                return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("没有需要修改的字段"))
            }
            if (!request.title.isNullOrBlank()) {
                return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("歌名不能为空"))
            }
            if (!request.artists.isNullOrEmpty()) {
                return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("歌手不能为空"))
            }

            val updated = TrackManager.update(id, request.title, request.artists, request.coverPath, request.lyricsPath)
            if (!updated) {
                return@patch call.respond(HttpStatusCode.NotFound, MessageResponse("曲目不存在"))
            }

            call.info("后台修改曲目: id=$id")
            call.ok()
        }

        delete("/tracks/{id}") {
            requireAdmin()

            val id = call.id
            val song = TrackManager.get(id)
            TrackManager.delete(id)

            val fileDeleted = PathSafety.resolveWithin(tracksDir, song.fileName)
                ?.takeIf(File::exists)?.delete() ?: false

            call.info("后台删除曲目: id=$id file=${song.fileName} 磁盘文件已删除=$fileDeleted")
            call.ok()
        }

        get("/users") {
            requireAdmin()
            val keyword = call.request.queryParameters["query"]
            val users = AuthManager.listUsers(keyword)
            call.info("后台用户列表: query=${keyword ?: "-"} 返回=${users.size}")
            call.respond(UserListResponse(users, users.size))
        }

        patch("/users/{id}/level") {
            val actor = requireAdmin()

            val id = call.id
            val request = call.tryReceive<UpdateUserLevelRequest>()

            val target = AuthManager.findById(id)
                ?: return@patch call.respond(HttpStatusCode.NotFound, MessageResponse("用户不存在"))

            val newLevel = enumEntries<UserLevel>().firstOrNull { it.value == request.level }
                ?: return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("等级值非法"))

            if (actor.id == target.id) {
                call.warn("后台修改等级被拒: actor=${actor.username} 尝试修改自己的权限")
                return@patch call.respond(
                    HttpStatusCode.Forbidden,
                    MessageResponse("不能修改自己的权限")
                )
            }
            if (!UserLevel.canModify(actor.level, target.level)) {
                call.warn(
                    "后台修改等级被拒: actor=${actor.username}(${actor.level.name}) " +
                            "target=${target.username}(${target.level.name}) 目标不低于自己"
                )
                return@patch call.respond(
                    HttpStatusCode.Forbidden,
                    MessageResponse("不能修改同级或更高级别用户的权限")
                )
            }
            if (!UserLevel.canAssign(actor.level, newLevel)) {
                call.warn(
                    "后台修改等级被拒: actor=${actor.username}(${actor.level.name}) " +
                            "尝试设置=${newLevel.name} 不低于自己"
                )
                return@patch call.respond(
                    HttpStatusCode.Forbidden,
                    MessageResponse("不能设置为不低于自己的等级")
                )
            }

            AuthManager.setLevel(id, newLevel)
            call.info(
                "后台修改等级: actor=${actor.username}(${actor.level.name}) " +
                        "target=${target.username} ${target.level.name} -> ${newLevel.name}"
            )
            call.ok()
        }

        get("/playlists") {
            requireAdmin()

            val keyword = call.request.queryParameters["query"]?.trim().orEmpty()
            val all = PlaylistManager.listAll()
            val filtered = if (keyword.isEmpty()) {
                all
            } else {
                all.filter { it.name.contains(keyword, ignoreCase = true) }
            }

            call.info("后台歌单列表: query=${keyword.ifEmpty { "-" }} 返回=${filtered.size}/${all.size}")
            call.respond(PlaylistListResponse(filtered, filtered.size))
        }

        get("/playlists/{id}") {
            requireAdmin()
            val id = call.id
            val playlist = PlaylistManager.get(id)
            call.respond(playlist.toDetailResponse(PlaylistManager.tracksOf(id)))
        }

        patch("/playlists/{id}") {
            requireAdmin()

            val id = call.id
            val request = call.tryReceive<UpdatePlaylistRequest>()

            if (request.name == null && request.description == null && request.coverPath == null) {
                return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("没有需要修改的字段"))
            }
            if (!request.name.isNullOrBlank()) {
                return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("歌单名不能为空"))
            }

            PlaylistManager.update(id, request)

            val playlist = PlaylistManager.get(id)
            call.info("后台修改歌单: id=$id name=${playlist.name}")
            call.respond(playlist)
        }

        delete("/playlists/{id}") {
            requireAdmin()
            val id = call.id
            PlaylistManager.delete(id)
            call.info("后台删除歌单: id=$id")
            call.ok()
        }

        delete("/playlists/{id}/tracks/{trackId}") {
            requireAdmin()
            val id = call.id
            val trackId = call.trackId
            val removed = PlaylistManager.removeTrack(id, trackId)
            call.info("后台歌单移除曲目: id=$id trackId=$trackId 实际移除=$removed")
            call.ok()
        }
    }
}


private val adminPageHtml: String by lazy {
    val stream = ClassLoader.getSystemResourceAsStream(ADMIN_PAGE_RESOURCE)
        ?: throw IllegalStateException("后台页面资源缺失：$ADMIN_PAGE_RESOURCE")
    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
