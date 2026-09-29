package top.stellortus.stellar_music_server.auth

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Argon2id 密码哈希实现。
 *
 * 生成自包含的 PHC 格式哈希字符串（含盐与参数）：
 * `$argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>`。
 *
 * 参数（可根据服务器性能微调，目标单次哈希约 200~500ms）：
 * memory=19456 KiB、iterations=2、parallelism=1、盐 16 字节、哈希 32 字节。
 * 生产环境请勿降低到不安全水平。
 */
class Argon2PasswordHasher {

    private val secureRandom = SecureRandom()

    /** 用户不存在时用它走一次等量校验，抹平登录接口的响应时间差异 */
    val dummyHash: String = hash("stellar-music-timing-equalizer")

    fun hash(password: String): String {
        val salt = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }
        val digest = argon2(salt, MEMORY_KIB, ITERATIONS, PARALLELISM, password)

        val encoder = Base64.getEncoder().withoutPadding()
        return $$"$argon2id$v=19$m=$$MEMORY_KIB,t=$$ITERATIONS,p=$$PARALLELISM" +
                "$${encoder.encodeToString(salt)}$${encoder.encodeToString(digest)}"
    }

    fun verify(password: String, hash: String): Boolean {
        val parts = hash.split('$')
        if (parts.size != 6 || parts[1] != "argon2id") return false

        val params = parts[3].split(',').mapNotNull { param ->
            val kv = param.split('=')
            if (kv.size != 2) null else kv[0] to kv[1]
        }
        if (params.size != 3) return false

        val memory = params.firstOrNull { it.first == "m" }?.second?.toIntOrNull() ?: return false
        val iterations = params.firstOrNull { it.first == "t" }?.second?.toIntOrNull() ?: return false
        val parallelism = params.firstOrNull { it.first == "p" }?.second?.toIntOrNull() ?: return false

        val salt = decodeBase64(parts[4]) ?: return false
        val expected = decodeBase64(parts[5]) ?: return false

        val actual = argon2(salt, memory, iterations, parallelism, password)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun argon2(
        salt: ByteArray,
        memoryKib: Int,
        iterations: Int,
        parallelism: Int,
        password: String,
    ): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withParallelism(parallelism)
            .withMemoryAsKB(memoryKib)
            .withIterations(iterations)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .build()

        val generator = Argon2BytesGenerator()
        generator.init(params)
        return ByteArray(HASH_LENGTH).also { generator.generateBytes(password.toCharArray(), it) }
    }

    private fun decodeBase64(value: String): ByteArray? =
        runCatching { Base64.getDecoder().decode(value) }.getOrNull()

    private companion object {
        const val MEMORY_KIB = 19_456
        const val ITERATIONS = 2
        const val PARALLELISM = 1
        const val SALT_LENGTH = 16
        const val HASH_LENGTH = 32
    }
}
