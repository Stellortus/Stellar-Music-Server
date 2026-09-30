package top.stellortus.stellar_music_server.database.auth

import org.jetbrains.exposed.sql.Table

object UsersTable : Table("users") {
    val id = integer("id").autoIncrement()

    val username = varchar("username", 128, "BINARY").uniqueIndex()

    val passwordHash = text("password_hash")

    val createdAt = long("created_at")

    val level = integer("level").default(UserLevel.USER.value)

    override val primaryKey = PrimaryKey(id)
}
