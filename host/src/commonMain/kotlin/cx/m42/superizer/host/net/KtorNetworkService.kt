package cx.m42.superizer.host.net

import cx.m42.superizer.host.platform.Connectivity
import cx.m42.superizer.runtime.NetworkException
import cx.m42.superizer.runtime.NetworkRequest
import cx.m42.superizer.runtime.NetworkResponse
import cx.m42.superizer.runtime.NetworkService
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.flow.StateFlow

/** The platform's HTTP engine — OkHttp, CIO, the browser's fetch. */
public expect fun defaultHttpClient(block: HttpClientConfig<*>.() -> Unit = {}): HttpClient

/**
 * Strings in, strings out, over Ktor.
 *
 * Three decisions, all of them D32:
 *
 *  - **No credentials are injected.** An app that needs a token reads `auth.session` and sets the
 *    header itself. A host that added one "helpfully" would be sending it to every host an app
 *    talks to, including the ones it should not.
 *  - **Fifteen seconds, no retries.** Whether a call is safe to repeat is knowledge the app has.
 *  - **`expectSuccess = false`.** A 404 is an answer, not an exception; the app decides.
 */
public class KtorNetworkService(
    private val client: HttpClient,
    override val online: StateFlow<Boolean> = Connectivity.online,
) : NetworkService {

    override suspend fun request(request: NetworkRequest): NetworkResponse {
        val response: HttpResponse = try {
            client.request(request.url) {
                method = HttpMethod.parse(request.method.uppercase())
                request.headers.forEach { (name, value) -> header(name, value) }
                request.contentType?.let { contentType(ContentType.parse(it)) }
                request.body?.let { setBody(it) }
            }
        } catch (cause: Throwable) {
            // Everything that never reached a status: DNS, TLS, the timeout above, a browser's CORS
            // refusal. One type, so an app has one thing to catch.
            throw NetworkException(null, "request failed: ${request.method} ${request.url}", cause)
        }
        return NetworkResponse(
            status = response.status.value,
            body = response.bodyAsText(),
            headers = response.headers.entries().associate { it.key to it.value },
        )
    }

    public companion object {
        public const val TIMEOUT_MS: Long = 15_000

        /** The one client a host makes, configured the same way on every platform. */
        public fun defaultClient(): HttpClient = defaultHttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MS
                connectTimeoutMillis = TIMEOUT_MS
                socketTimeoutMillis = TIMEOUT_MS
            }
        }
    }
}
