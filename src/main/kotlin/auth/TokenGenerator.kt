package top.stellortus.stellar_music_server.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object TokenGenerator {

    private val secureRandom = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        // 使用 URL-safe 编码并去掉填充，便于放入 Authorization 头
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** 入库前对 token 做摘要。token 有 256 bit 熵，无需加盐；也不能用慢哈希，否则每个请求都要多花数百毫秒 */
    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }
}
