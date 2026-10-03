package top.stellortus

import top.stellortus.stellar_music_common.dto.UserLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 后台等级层级规则（[UserLevel.canModify] / [UserLevel.canAssign]）。
 *
 * 这些规则当前由 AdminSettings.enforceLevelHierarchy 关闭，但实现已就绪，开启后即依赖这些行为。
 */
class AdminLevelPolicyTest {

    @Test
    fun `admin cannot modify admin or owner`() {
        assertFalse(UserLevel.canModify(UserLevel.Admin, UserLevel.Admin))
        assertFalse(UserLevel.canModify(UserLevel.Admin, UserLevel.Owner))
    }

    @Test
    fun `admin can modify lower levels`() {
        assertTrue(UserLevel.canModify(UserLevel.Admin, UserLevel.VIP))
        assertTrue(UserLevel.canModify(UserLevel.Admin, UserLevel.User))
    }

    @Test
    fun `admin can assign at most vip`() {
        assertTrue(UserLevel.canAssign(UserLevel.Admin, UserLevel.VIP))
        assertTrue(UserLevel.canAssign(UserLevel.Admin, UserLevel.User))
        assertFalse(UserLevel.canAssign(UserLevel.Admin, UserLevel.Admin))
        assertFalse(UserLevel.canAssign(UserLevel.Admin, UserLevel.Owner))
    }

    @Test
    fun `owner can assign below owner`() {
        assertTrue(UserLevel.canAssign(UserLevel.Owner, UserLevel.Admin))
        assertTrue(UserLevel.canAssign(UserLevel.Owner, UserLevel.VIP))
        assertFalse(UserLevel.canAssign(UserLevel.Owner, UserLevel.Owner))
        assertTrue(UserLevel.canModify(UserLevel.Owner, UserLevel.Admin))
    }

    @Test
    fun `vip can only assign user`() {
        assertTrue(UserLevel.canAssign(UserLevel.VIP, UserLevel.User))
        assertFalse(UserLevel.canAssign(UserLevel.VIP, UserLevel.VIP))
    }

    @Test
    fun `user cannot modify anyone`() {
        assertFalse(UserLevel.canModify(UserLevel.User, UserLevel.User))
        assertFalse(UserLevel.canAssign(UserLevel.User, UserLevel.User))
        assertTrue(UserLevel.assignableBelow(UserLevel.User).isEmpty())
    }

    @Test
    fun `assignable levels are strictly below actor in descending order`() {
        assertEquals(listOf(UserLevel.VIP, UserLevel.User), UserLevel.assignableBelow(UserLevel.Admin))
        assertEquals(
            listOf(UserLevel.Admin, UserLevel.VIP, UserLevel.User),
            UserLevel.assignableBelow(UserLevel.Owner),
        )
        assertEquals(listOf(UserLevel.User), UserLevel.assignableBelow(UserLevel.VIP))
    }
}
