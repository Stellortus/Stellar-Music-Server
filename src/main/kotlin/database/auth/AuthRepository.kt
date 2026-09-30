package top.stellortus.stellar_music_server.database.auth

import top.stellortus.stellar_music_server.auth.TokenGenerator
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

class AuthRepository {

    lateinit var database: Database
        private set

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

    fun findByUsername(username: String): User? = transaction(database) {
        UsersTable.selectAll()
            .where { UsersTable.username eq username }
            .limit(1)
            .firstOrNull()
            ?.toUser()
    }

    fun findCredentials(username: String): Pair<User, String>? = transaction(database) {
        UsersTable.selectAll()
            .where { UsersTable.username eq username }
            .limit(1)
            .firstOrNull()
            ?.let { it.toUser() to it[UsersTable.passwordHash] }
    }

    fun createUser(username: String, passwordHash: String): User = transaction(database) {
        val newId = UsersTable.insert {
            it[UsersTable.username] = username
            it[UsersTable.passwordHash] = passwordHash
            it[createdAt] = System.currentTimeMillis()
        } get UsersTable.id

        User(id = newId, username = username, level = UserLevel.USER)
    }

    fun setLevel(userId: Int, level: UserLevel): Int = transaction(database) {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.level] = level.value
        }
    }

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

    fun deleteSession(token: String) {
        transaction(database) {
            SessionsTable.deleteWhere { SessionsTable.tokenHash eq TokenGenerator.hash(token) }
        }
    }

    fun purgeExpiredSessions(): Int {
        val cutoff = System.currentTimeMillis() - SESSION_IDLE_TIMEOUT_MS
        return transaction(database) {
            SessionsTable.deleteWhere { SessionsTable.lastUsedAt less cutoff }
        }
    }

    private fun ResultRow.toUser(): User = User(
        id = this[UsersTable.id],
        username = this[UsersTable.username],
        level = UserLevel.fromValue(this[UsersTable.level]),
    )

    companion object {
        /** 空闲超时：距上次使用超过 60 天的 session 视为失效并被清除 */
        const val SESSION_IDLE_TIMEOUT_MS = 60L * 24 * 60 * 60 * 1000
    }
}
