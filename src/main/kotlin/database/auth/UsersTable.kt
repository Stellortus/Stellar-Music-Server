package top.stellortus.stellar_music_server.database.auth

import org.jetbrains.exposed.sql.Table

object UsersTable : Table("users") {
    val id = integer("id").autoIncrement()

    // COLLATE BINARY：严格区分大小写与空格
    val username = varchar("username", 128, "BINARY").uniqueIndex()

    val passwordHash = text("password_hash")

    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
