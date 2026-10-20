package top.stellortus

import top.stellortus.stellar_music_server.util.readLogTail
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogReaderTest {
    @Test
    fun `reads empty and UTF-8 logs as plain text`() {
        val file = Files.createTempFile("admin-log-", ".log").toFile()
        try {
            assertEquals("", readLogTail(file).content)
            val content = "日志中文\n<script>alert('test')</script>\n"
            file.writeText(content)
            val snapshot = readLogTail(file)
            assertEquals(content, snapshot.content)
            assertFalse(snapshot.truncated)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `limits reads to the tail without breaking initial UTF-8 characters`() {
        val file = Files.createTempFile("admin-log-", ".log").toFile()
        try {
            file.writeText("较旧日志\n最新日志\n")
            // Start inside the three-byte UTF-8 encoding of 最.
            val snapshot = readLogTail(file, maxBytes = 11)
            assertEquals("新日志\n", snapshot.content)
            assertTrue(snapshot.truncated)
        } finally {
            file.delete()
        }
    }
}
