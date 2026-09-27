package database.song

import kotlinx.serialization.Serializable


@Serializable
data class Track(
    val id: Int = 0,                                 // 0 表示还没入库
    val title: String,                               // 歌曲名
    val artists: List<String>,                       // 歌手（多个）
    val fileName: String,                            // 音频文件名
    val uploader: String,                            // 上传者
    val coverPath: String,                           // 封面图片文件位置
    val lyricsPath: String,                          // 歌词文件位置
    val createdAt: Long = System.currentTimeMillis() // 上传时间，用于排序
)