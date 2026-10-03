package top.stellortus

import top.stellortus.stellar_music_common.dto.UserLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserLevelTest {

    @Test
    fun `test level values`() {
        assertEquals(UserLevel.User, UserLevel.fromValue(0))
        assertEquals(UserLevel.VIP, UserLevel.fromValue(1))
        assertEquals(UserLevel.Admin, UserLevel.fromValue(5))
        assertEquals(UserLevel.Owner, UserLevel.fromValue(9))
    }

    @Test
    fun `test unknown level falls back to user`() {
        assertEquals(UserLevel.User, UserLevel.fromValue(999))
        assertEquals(UserLevel.User, UserLevel.fromValue(-1))
    }

    @Test
    fun `test level is cumulative`() {
        assertTrue(UserLevel.Owner.value >= UserLevel.Admin.value)
        assertTrue(UserLevel.Admin.value >= UserLevel.VIP.value)
        assertTrue(UserLevel.VIP.value >= UserLevel.User.value)
    }

}
