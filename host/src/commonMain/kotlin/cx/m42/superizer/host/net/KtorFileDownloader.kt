package cx.m42.superizer.host.net

import cx.m42.superizer.runtime.DownloadTooLarge
import cx.m42.superizer.runtime.FileDownloader
import cx.m42.superizer.runtime.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray

/**
 * A file over HTTP as bytes (0.8.2), on the host's own client — the one [KtorNetworkService] uses, so a host that
 * substitutes the network in a test has no downloader either and reaches nothing.
 *
 * The body is read up to one byte past the limit and no further: a server that lies about its length, or says
 * nothing, still cannot make the app hold more than it asked for.
 */
public class KtorFileDownloader(private val client: HttpClient) : FileDownloader {

    override suspend fun download(url: String, maxBytes: Long, headers: Map<String, String>): ByteArray {
        require(maxBytes > 0) { "maxBytes is positive: $maxBytes" }
        try {
            return client.prepareGet(url) {
                headers.forEach { (name, value) -> header(name, value) }
                // Only where the plugin is there to read it: a host's own client may have none, and Ktor then
                // refuses the request outright rather than ignoring the setting.
                if (client.pluginOrNull(HttpTimeout) != null) timeout { requestTimeoutMillis = TIMEOUT_MS }
            }.execute { response ->
                val status = response.status.value
                if (status !in 200..299) throw NetworkException(status, "download failed: $status $url")
                response.contentLength()?.let { if (it > maxBytes) throw DownloadTooLarge(maxBytes) }
                val body = response.bodyAsChannel().readRemaining(maxBytes + 1).readByteArray()
                if (body.size > maxBytes) throw DownloadTooLarge(maxBytes)
                body
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException) {
            throw e
        } catch (e: DownloadTooLarge) {
            throw e
        } catch (cause: Throwable) {
            // Everything that never reached a status, as in KtorNetworkService: one type for the app to catch.
            throw NetworkException(null, "download failed: $url", cause)
        }
    }

    public companion object {
        /** The whole download, from the first byte to the last; the connect and between-packet limits stay the client's. */
        public const val TIMEOUT_MS: Long = 120_000
    }
}
