package top.stellortus.stellar_music_server.routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import top.stellortus.stellar_music_server.db.TrackSort
import top.stellortus.stellar_music_server.db.manager.TrackManager
import top.stellortus.stellar_music_server.util.extensions.info
import top.stellortus.stellar_music_server.util.extensions.requireUser
import kotlin.enums.enumEntries

fun Routing.trackListRoutes() {
    route("/track_list") {
        get {
            requireUser()
            val sortParam = call.request.queryParameters["sort"]
            val start = call.request.queryParameters["start"]?.toIntOrNull() ?: 0
            val sort = enumEntries<TrackSort>().firstOrNull { it.name == sortParam }
            val songList = TrackManager.top(10, sort, start = start)
            call.application.log.info("曲目列表: sort=$sortParam 返回=${songList.size} 首")
            call.respond(songList)
        }
        get("/search") {
            requireUser()
            val searchKey = call.request.queryParameters["key"].orEmpty()
            val playlists = TrackManager.search(searchKey)
            call.info("歌曲搜索: key=$searchKey 返回=${playlists.size} 个")
            call.respond(playlists)
        }
    }
}