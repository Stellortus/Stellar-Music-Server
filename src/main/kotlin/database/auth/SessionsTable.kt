package top.stellortus.stellar_music_server.database.auth

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object SessionsTable : Table("sessions") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)

    val tokenHash = varchar("token_hash", 128).uniqueIndex()

    val createdAt = long("created_at")
    val lastUsedAt = long("last_used_at")

    override val primaryKey = PrimaryKey(id)
}
