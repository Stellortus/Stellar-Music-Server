package top.stellortus.stellar_music_server.util

import kotlinx.serialization.Serializable
import java.io.File
import java.io.RandomAccessFile

@Serializable
data class LogSnapshot(val content: String, val truncated: Boolean)

/** Read a bounded tail so large log files do not fill server memory. */
internal fun readLogTail(file: File, maxBytes: Int = 256 * 1024): LogSnapshot {
    require(maxBytes > 0)
    return RandomAccessFile(file, "r").use { log ->
        val length = log.length()
        val start = (length - maxBytes).coerceAtLeast(0)
        log.seek(start)
        val bytes = ByteArray((length - start).toInt())
        var size = 0
        while (size < bytes.size) {
            val read = log.read(bytes, size, bytes.size - size)
            if (read < 0) break // The service may truncate the file while reading.
            size += read
        }
        var offset = 0
        if (start > 0) {
            // Drop an incomplete UTF-8 character at the start of the window.
            while (offset < size && (bytes[offset].toInt() and 0xC0) == 0x80) offset++
        }
        LogSnapshot(String(bytes, offset, size - offset, Charsets.UTF_8), start > 0)
    }
}
