package top.stellortus

import io.ktor.server.testing.*
import kotlin.test.Test

class ServerTest {

    @Test
    fun `test track list endpoint`() = testApplication {
        configure()
    }
}
