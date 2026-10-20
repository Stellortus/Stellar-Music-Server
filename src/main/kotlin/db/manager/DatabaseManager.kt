package top.stellortus.stellar_music_server.db.manager

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import top.stellortus.stellar_music_server.config.AppPaths.databaseDir
import java.io.File

private val logger = LoggerFactory.getLogger(DatabaseManager::class.java)

abstract class DatabaseManager(val fileName: String, vararg val tables: Table) {

    protected lateinit var database: Database
        private set

    fun init() {
        val file = File(databaseDir, fileName).absoluteFile
        file.parentFile?.mkdirs() // sqlite 不会自动建目录
        database = Database.connect(
            url = "jdbc:sqlite:${file.path}",
            driver = "org.sqlite.JDBC",
            setupConnection = { connection ->
                connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            }
        )
        transaction(database) { SchemaUtils.create(*tables) }
        logger.info("数据库已就绪：{}", file.path)
    }

    /** 供一次性更新等外部逻辑在 manager 自己的库上执行事务 */
    internal fun <T> inTransaction(block: Transaction.() -> T): T = transaction(database, block)

    companion object {
        fun initDb() {
            TrackManager.init()
            PlaylistManager.init()
            AuthManager.init()
        }
    }
}
