package cx.m42.superizer.host

import androidx.compose.runtime.Composable
import cx.m42.superizer.SuperizerContract
import cx.m42.superizer.app.AppConfigSpec
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.app.AppManifest
import cx.m42.superizer.app.AppMetadata
import cx.m42.superizer.app.AppSetupContext
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.app.localized
import cx.m42.superizer.host.storage.PrefsStorage
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.runtime.InstanceRuntime
import cx.m42.superizer.runtime.Platform
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.Serializable

/** The smallest registrable app, for tests that are about the host and not about any app. */
@Serializable
internal data class StubConfig(val mode: String = "default")

internal class StubApp(
    id: String = "stub",
    hidden: Boolean = false,
    deepLinks: Set<String> = emptySet(),
    topics: Set<String> = emptySet(),
) : SuperizerApp<StubConfig>() {

    override val manifest: AppManifest = AppManifest(
        id = AppId(id),
        version = "1.0.0",
        metadata = AppMetadata(title = localized("en" to id), hidden = hidden),
        deepLinks = deepLinks,
        pushTopics = topics,
    )

    override val configSpec: AppConfigSpec<StubConfig> =
        AppConfigSpec(StubConfig.serializer(), StubConfig())

    override fun setup(ctx: AppSetupContext) {
        manifest.deepLinks.forEach { path ->
            ctx.deepLink(path) { params -> configSpec.encode(StubConfig(params["mode"] ?: "link")) }
        }
    }

    override fun launch(runtime: InstanceRuntime, config: StubConfig): AppInstance =
        object : AppInstance() {
            @Composable
            override fun Content() = Unit
        }
}

internal fun testHostInfo(debug: Boolean = true) = HostInfo(
    name = "Test",
    version = "0.0.0",
    build = "test",
    platform = Platform.Desktop,
    contractVersion = SuperizerContract.VERSION,
    debug = debug,
)

/**
 * Each test gets its own preferences directory.
 *
 * `PrefsStorage` is a process-wide object by design — every platform's store is — so a suite that
 * did not do this would have each test reading whatever the one before it wrote, and the failures
 * would depend on execution order.
 */
internal fun isolatePrefs() {
    PrefsStorage.useDirectory(
        File(System.getProperty("java.io.tmpdir"), "superizer-host-test-${counter.incrementAndGet()}"),
    )
}

private val counter = AtomicInteger(0)
