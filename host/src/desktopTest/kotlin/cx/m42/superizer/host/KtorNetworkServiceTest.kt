package cx.m42.superizer.host

import cx.m42.superizer.host.net.KtorNetworkService
import cx.m42.superizer.runtime.NetworkException
import cx.m42.superizer.runtime.NetworkRequest
import cx.m42.superizer.runtime.decode
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable

/**
 * The host's side of `NetworkService`, against Ktor's own mock engine.
 *
 * The decisions worth holding still (D32): a non-2xx status is an *answer* rather than an
 * exception, no credentials are added by the host, and everything that never reached a status
 * becomes one exception type so an app has one thing to catch.
 */
class KtorNetworkServiceTest {

    @Serializable
    private data class Payload(val base: String, val date: String)

    private fun service(engine: MockEngine) =
        KtorNetworkService(HttpClient(engine), online = MutableStateFlow(true))

    @Test
    fun aSuccessfulResponseArrivesAsStatusAndBody() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"base":"USD","date":"2026-09-19"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val response = service(engine).get("https://api.example/latest")

        assertEquals(200, response.status)
        assertTrue(response.isSuccess)
        assertEquals("USD", response.decode<Payload>().base)
    }

    @Test
    fun aNotFoundIsAnAnswerAndNotAnException() {
        // `expectSuccess = false`: what a 404 means is the app's business. A host that threw here
        // would be deciding for it.
        runTest {
            val response = service(MockEngine { respondError(HttpStatusCode.NotFound) })
                .get("https://api.example/missing")
            assertEquals(404, response.status)
            assertFalse(response.isSuccess)
        }
    }

    @Test
    fun aTransportFailureIsOneTypeAnAppCanCatch() = runTest {
        val engine = MockEngine { throw java.io.IOException("no route to host") }
        val failure = assertFailsWith<NetworkException> {
            service(engine).get("https://api.example/latest")
        }
        assertEquals(null, failure.status)
    }

    @Test
    fun theHostSendsExactlyTheHeadersItWasGiven() = runTest {
        // No credentials injected (D32): an app that needs a token reads `auth.session` and sets
        // the header itself, so the host never sends one to a host that should not get it.
        var seen: Set<String> = emptySet()
        val engine = MockEngine { request ->
            seen = request.headers.names().toSet()
            respond("{}")
        }
        service(engine).request(
            NetworkRequest("GET", "https://api.example/x", headers = mapOf("X-Token" to "abc")),
        )

        assertTrue("X-Token" in seen)
        assertFalse(seen.any { it.equals("Authorization", ignoreCase = true) })
    }

    @Test
    fun aPostCarriesItsBodyAndContentType() = runTest {
        var contentType: String? = null
        val engine = MockEngine { request ->
            contentType = request.body.contentType?.toString()
            respond("{}")
        }
        service(engine).post("https://api.example/x", body = """{"a":1}""")

        assertTrue(contentType.orEmpty().startsWith("application/json"))
    }
}
