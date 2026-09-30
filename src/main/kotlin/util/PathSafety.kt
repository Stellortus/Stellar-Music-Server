package top.stellortus.stellar_music_server.util

import java.io.File

object PathSafety {

    fun resolveWithin(baseDir: File, name: String): File? {
        if (name.isBlank()) return null

        val base = runCatching { baseDir.canonicalFile }.getOrNull() ?: return null
        val target = runCatching { File(base, name).canonicalFile }.getOrNull() ?: return null

        val prefix = base.path + File.separator
        if (!target.path.startsWith(prefix)) return null
        if (target.parentFile != base) return null

        return target
    }
}
