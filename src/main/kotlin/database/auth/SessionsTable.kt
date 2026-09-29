package top.stellortus.stellar_music_server.database.auth

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

/**
 * 会话表：一个用户可有多条 session（多设备登录），每条一行。
 *
 * 只存 token 的 SHA-256 摘要，原始 token 仅返回给客户端；
 * 即便数据库泄漏也无法还原出可用 token。
 * 空闲超过 [AuthRepository.SESSION_IDLE_TIMEOUT_MS] 的 session 会被清除。
 */
object SessionsTable : Table("sessions") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)

    val tokenHash = varchar("token_hash", 128).uniqueIndex()

    val createdAt = long("created_at")
    val lastUsedAt = long("last_used_at")

    override val primaryKey = PrimaryKey(id)
}
