package top.stellortus

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.*

class ServerTest {

    @Test
    fun `test track list endpoint`() = testApplication {
        configure()
        assertEquals(HttpStatusCode.OK, client.get("/track_list").status)
    }

}
