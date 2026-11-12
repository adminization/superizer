package cx.m42.superizer.host

import cx.m42.superizer.Superizer
import cx.m42.superizer.app.AppId
import cx.m42.superizer.host.net.KtorFileDownloader
import cx.m42.superizer.runtime.AppFolder
import cx.m42.superizer.runtime.AppRuntime
import cx.m42.superizer.runtime.DownloadTooLarge
import cx.m42.superizer.runtime.FileDownloader
import cx.m42.superizer.runtime.NetworkException
import cx.m42.superizer.runtime.QrScanner
import cx.m42.superizer.testing.FakeNetwork
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

/**
 * 0.8.2: what the host offers apps besides the frozen set — a folder of each app's own that the person sees, files
 * over HTTP as bytes, and the camera behind Activate.
 */
class HostFilesTest {

    @BeforeTest
    fun setUp() = isolatePrefs()

    private val root: File = Files.createTempDirectory("superizer-folders").toFile()

    private fun host(extra: SuperizerBuilder.() -> Unit = {}): Superizer =
        Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
            host(testHostInfo())
            network(FakeNetwork())
            register(StubApp("alpha"))
            register(StubApp("beta"))
            appFolders(root.path)
            extra()
        }

    private fun Superizer.runtimeOf(id: String): AppRuntime = requireNotNull(handler.appRuntime(AppId(id))) { "$id not enabled" }

    private fun Superizer.folderOf(id: String): AppFolder = assertNotNull(runtimeOf(id).service(AppFolder.Key))

    // ------------------------------------------------------------ AppFolder

    @Test
    fun eachAppHasItsOwnFolderNamedAfterItsId() = runBlocking {
        val superizer = host()
        assertTrue(AppFolder.Key in superizer.hostInfo.services)
        val alpha = superizer.folderOf("alpha")
        val beta = superizer.folderOf("beta")
        assertEquals(File(root, "alpha").path, alpha.location)

        alpha.write("book.tgz", byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), File(root, "alpha/book.tgz").readBytes())
        assertContentEquals(byteArrayOf(1, 2, 3), alpha.read("book.tgz"))
        assertNull(beta.read("book.tgz"))
        assertEquals(emptyList(), beta.list())
    }

    @Test
    fun nothingIsCreatedBeforeTheFirstWrite() = runBlocking {
        val alpha = host().folderOf("alpha")
        assertEquals(emptyList(), alpha.list())
        assertNull(alpha.read("missing.tgz"))
        assertFalse(alpha.delete("missing.tgz"))
        assertFalse(File(root, "alpha").exists())
    }

    @Test
    fun aWriteReplacesTheFileWholeAndLeavesNothingBeside() = runBlocking {
        val alpha = host().folderOf("alpha")
        alpha.write("a.tgz", ByteArray(10) { 7 })
        alpha.write("a.tgz", ByteArray(3) { 9 })
        assertContentEquals(ByteArray(3) { 9 }, alpha.read("a.tgz"))
        assertEquals(listOf("a.tgz"), File(root, "alpha").list()!!.toList())
    }

    @Test
    fun theListIsWhatIsInTheFolderWhoeverPutItThere() = runBlocking {
        val alpha = host().folderOf("alpha")
        alpha.write("b.tgz", ByteArray(5))
        // The person copies a file in, keeps a hidden one and a folder of their own.
        File(root, "alpha/a.tgz").writeBytes(ByteArray(2))
        File(root, "alpha/.hidden").writeBytes(ByteArray(1))
        File(root, "alpha/inner").mkdirs()
        val files = alpha.list()
        assertEquals(listOf("a.tgz", "b.tgz"), files.map { it.name })
        assertEquals(listOf(2L, 5L), files.map { it.size })
        assertTrue(files.all { it.modified > 0 })

        assertTrue(alpha.delete("a.tgz"))
        assertEquals(listOf("b.tgz"), alpha.list().map { it.name })
    }

    @Test
    fun aNameThatCouldReachOutsideIsTurnedAway() = runBlocking {
        val alpha = host().folderOf("alpha")
        for (name in listOf("", "../beta/x", "a/b", "a\\b", ".hidden", "c:d", "a\u0000b", "x".repeat(256))) {
            assertFailsWith<IllegalArgumentException>(name) { alpha.write(name, ByteArray(1)) }
            assertFailsWith<IllegalArgumentException>(name) { alpha.read(name) }
        }
        assertFalse(File(root, "beta").exists())
    }

    @Test
    fun aHostCanOfferNoFolders() {
        val superizer = host { appFolders(null) }
        assertFalse(AppFolder.Key in superizer.hostInfo.services)
        assertNull(superizer.runtimeOf("alpha").service(AppFolder.Key))
    }

    // ------------------------------------------------------------ QrScanner

    @Test
    fun theCameraBehindActivateIsOfferedToAppsWhereThereIsOne() = runBlocking {
        assertNull(host().runtimeOf("alpha").service(QrScanner.Key))
        val superizer = host { qrScanner { "https://example.org/book.tgz" } }
        assertTrue(QrScanner.Key in superizer.hostInfo.services)
        assertEquals("https://example.org/book.tgz", superizer.runtimeOf("alpha").service(QrScanner.Key)?.scan())
    }

    // ------------------------------------------------------------ FileDownloader

    @Test
    fun aHostWithItsOwnClientDownloadsAndOneWithASubstitutedNetworkDoesNot() = runBlocking {
        assertNull(host().runtimeOf("alpha").service(FileDownloader.Key))
        val engine = MockEngine { respond(ByteReadChannel(byteArrayOf(4, 5, 6)), HttpStatusCode.OK) }
        val superizer = Superizer.build(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) {
            host(testHostInfo())
            httpClient(HttpClient(engine))
            register(StubApp("alpha"))
            appFolders(null)
        }
        val downloader = assertNotNull(superizer.runtimeOf("alpha").service(FileDownloader.Key))
        assertContentEquals(byteArrayOf(4, 5, 6), downloader.download("https://example.org/f", maxBytes = 10))
    }

    @Test
    fun theBodyArrivesAsBytesWithTheHeadersTheAppSet() = runTest {
        var seen: String? = null
        val bytes = ByteArray(300) { (it * 7).toByte() }
        val engine = MockEngine { request ->
            seen = request.headers["Authorization"]
            respond(ByteReadChannel(bytes), HttpStatusCode.OK)
        }
        val body = KtorFileDownloader(HttpClient(engine)).download("https://example.org/f", 1000, mapOf("Authorization" to "Bearer t"))
        assertContentEquals(bytes, body)
        assertEquals("Bearer t", seen)
    }

    @Test
    fun aStatusOtherThan2xxIsANetworkExceptionWithThatStatus() = runTest {
        val failure = assertFailsWith<NetworkException> {
            KtorFileDownloader(HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) })).download("https://example.org/f", 10)
        }
        assertEquals(404, failure.status)
    }

    @Test
    fun noAnswerIsANetworkExceptionWithNoStatus() = runTest {
        val failure = assertFailsWith<NetworkException> {
            KtorFileDownloader(HttpClient(MockEngine { throw java.io.IOException("no route to host") })).download("https://example.org/f", 10)
        }
        assertNull(failure.status)
    }

    @Test
    fun aFileOverTheLimitIsTurnedAwayWhetherTheServerSaysItsLengthOrNot() = runTest {
        val said = MockEngine {
            respond(ByteReadChannel(ByteArray(20)), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "20"))
        }
        assertFailsWith<DownloadTooLarge> { KtorFileDownloader(HttpClient(said)).download("https://example.org/f", 10) }
        val silent = MockEngine { respond(ByteReadChannel(ByteArray(20)), HttpStatusCode.OK) }
        assertFailsWith<DownloadTooLarge> { KtorFileDownloader(HttpClient(silent)).download("https://example.org/f", 10) }
        val exact = MockEngine { respond(ByteReadChannel(ByteArray(10)), HttpStatusCode.OK) }
        assertEquals(10, KtorFileDownloader(HttpClient(exact)).download("https://example.org/f", 10).size)
    }

    @Test
    fun theDefaultClientsTimeoutIsLongerForAFile() = runTest {
        // The default client has HttpTimeout; the downloader raises the request limit for itself without failing.
        val engine = MockEngine { respond(ByteReadChannel(byteArrayOf(1)), HttpStatusCode.OK) }
        val client = HttpClient(engine) { install(io.ktor.client.plugins.HttpTimeout) { requestTimeoutMillis = 15_000 } }
        assertContentEquals(byteArrayOf(1), KtorFileDownloader(client).download("https://example.org/f", 10))
    }
}
