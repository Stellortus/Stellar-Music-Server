package top.stellortus.stellar_music_server.database.auth

import kotlin.enums.enumEntries

enum class UserLevel(val value: Int) {
    USER(0),
    VIP(1),
    ADMIN(5),
    OWNER(9);

    companion object {
        fun fromValue(value: Int): UserLevel =
            enumEntries<UserLevel>().firstOrNull { it.value == value } ?: USER
    }
}
