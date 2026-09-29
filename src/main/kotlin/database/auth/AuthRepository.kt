package top.stellortus.stellar_music_server.database.auth

import top.stellortus.stellar_music_server.auth.TokenGenerator
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

/**
 * 认证数据存取（users / sessions 表）。
 *
 * 使用独立的 [database] 连接，与 TrackRepository 使用的歌曲数据库互不干扰；
 * 所有查询显式通过 [database] 执行，不依赖 Exposed 的全局默认数据库。
 */
class AuthRepository {

    lateinit var database: Database
        private set

    /** 初始化数据库连接并建表（目录不存在时自动创建） */
    fun init(dbPath: String) {
        File(dbPath).parentFile?.mkdirs()
        database = Database.connect(
            url = "jdbc:sqlite:$dbPath",
            driver = "org.sqlite.JDBC",
        )
        transaction(database) {
            SchemaUtils.create(UsersTable, SessionsTable)
        }
    }

    /** 按用户名（严格匹配，区分大小写与空格）查用户，不存在返回 null */
    fun findByUsername(username: String): User? = transaction(database) {
        UsersTable.selectAll()
            .where { UsersTable.username eq username }
            .limit(1)
            .firstOrNull()
            ?.toUser()
    }

    /** 按用户名查用户及其密码哈希，用于登录校验，不存在返回 null */
    fun findCredentials(username: String): Pair<User, String>? = transaction(database) {
        UsersTable.selectAll()
            .where { UsersTable.username eq username }
            .limit(1)
            .firstOrNull()
            ?.let { it.toUser() to it[UsersTable.passwordHash] }
    }

    /** 创建用户，返回新用户 */
    fun createUser(username: String, passwordHash: String): User = transaction(database) {
        val newId = UsersTable.insert {
            it[UsersTable.username] = username
            it[UsersTable.passwordHash] = passwordHash
            it[createdAt] = System.currentTimeMillis()
        } get UsersTable.id

        User(id = newId, username = username)
    }

    /** 为用户创建一条 session，入库的是 token 摘要而非 token 本身 */
    fun createSession(userId: Int, token: String) {
        val now = System.currentTimeMillis()
        transaction(database) {
            SessionsTable.insert {
                it[SessionsTable.userId] = userId
                it[SessionsTable.tokenHash] = TokenGenerator.hash(token)
                it[SessionsTable.createdAt] = now
                it[SessionsTable.lastUsedAt] = now
            }
        }
    }

    /**
     * 按 token 查 session 对应的用户，并刷新该 session 的 [SessionsTable.lastUsedAt]。
     *
     * 距上次使用超过 [SESSION_IDLE_TIMEOUT_MS] 的 session 视为已过期：
     * 就地删除并返回 null，调用方按未认证处理。
     */
    fun findByToken(token: String): User? = transaction(database) {
        val now = System.currentTimeMillis()

        val row = SessionsTable.selectAll()
            .where { SessionsTable.tokenHash eq TokenGenerator.hash(token) }
            .limit(1)
            .firstOrNull() ?: return@transaction null

        val sessionId = row[SessionsTable.id]

        if (now - row[SessionsTable.lastUsedAt] > SESSION_IDLE_TIMEOUT_MS) {
            SessionsTable.deleteWhere { SessionsTable.id eq sessionId }
            return@transaction null
        }

        SessionsTable.update({ SessionsTable.id eq sessionId }) {
            it[SessionsTable.lastUsedAt] = now
        }

        UsersTable.selectAll()
            .where { UsersTable.id eq row[SessionsTable.userId] }
            .limit(1)
            .firstOrNull()
            ?.toUser()
    }

    /** 删除指定 token 的 session（幂等） */
    fun deleteSession(token: String) {
        transaction(database) {
            SessionsTable.deleteWhere { SessionsTable.tokenHash eq TokenGenerator.hash(token) }
        }
    }

    /**
     * 清除所有空闲超时的 session，返回删除条数。
     *
     * [findByToken] 只在 token 被再次使用时才发现过期，长期无人使用的 session
     * 需要靠本方法回收；目前尚未接入定时任务，需手动或另行调度调用。
     */
    fun purgeExpiredSessions(): Int {
        val cutoff = System.currentTimeMillis() - SESSION_IDLE_TIMEOUT_MS
        return transaction(database) {
            SessionsTable.deleteWhere { SessionsTable.lastUsedAt less cutoff }
        }
    }

    private fun ResultRow.toUser(): User = User(
        id = this[UsersTable.id],
        username = this[UsersTable.username],
    )

    companion object {
        /** 空闲超时：距上次使用超过 60 天的 session 视为失效并被清除 */
        const val SESSION_IDLE_TIMEOUT_MS = 60L * 24 * 60 * 60 * 1000
    }
}
