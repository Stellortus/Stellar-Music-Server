package top.stellortus

import top.stellortus.stellar_music_server.database.auth.UserLevel
import kotlin.test.*

class UserLevelTest {

    @Test
    fun `test level values`() {
        assertEquals(UserLevel.USER, UserLevel.fromValue(0))
        assertEquals(UserLevel.VIP, UserLevel.fromValue(1))
        assertEquals(UserLevel.ADMIN, UserLevel.fromValue(5))
        assertEquals(UserLevel.OWNER, UserLevel.fromValue(9))
    }

    @Test
    fun `test unknown level falls back to user`() {
        assertEquals(UserLevel.USER, UserLevel.fromValue(999))
        assertEquals(UserLevel.USER, UserLevel.fromValue(-1))
    }

    @Test
    fun `test level is cumulative`() {
        assertTrue(UserLevel.OWNER.value >= UserLevel.ADMIN.value)
        assertTrue(UserLevel.ADMIN.value >= UserLevel.VIP.value)
        assertTrue(UserLevel.VIP.value >= UserLevel.USER.value)
    }

}
