package top.stellortus.stellar_music_server.util

import java.io.File

object PathSafety {

    /**
     * 把用户提供的文件名 [name] 解析到 [baseDir] 之下并规范化，越界返回 null。
     *
     * 拒绝 `../` 穿越、绝对路径，以及带目录层级的多段路径（这类目标即便落在基准目录内，
     * 也会因为父目录不存在而在写入时抛异常，不如直接判非法）。
     */
    fun resolveWithin(baseDir: File, name: String): File? {
        if (name.isBlank()) return null

        // canonicalFile 遇到 Windows 盘符路径（如 C:\x）或非法字符会抛 IOException，一律按非法处理
        val base = runCatching { baseDir.canonicalFile }.getOrNull() ?: return null
        val target = runCatching { File(base, name).canonicalFile }.getOrNull() ?: return null

        val prefix = base.path + File.separator
        if (!target.path.startsWith(prefix)) return null
        if (target.parentFile != base) return null

        return target
    }
}
