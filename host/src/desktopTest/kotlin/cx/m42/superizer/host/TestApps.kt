package cx.m42.superizer.host

import androidx.compose.runtime.Composable
import cx.m42.superizer.SuperizerContract
import cx.m42.superizer.app.AppConfigSpec
import cx.m42.superizer.backup.BackupPolicy
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.app.AppManifest
import cx.m42.superizer.app.AppProtection
import cx.m42.superizer.app.LockPolicy
import cx.m42.superizer.app.AppMetadata
import cx.m42.superizer.app.AppSetupContext
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.app.localized
import cx.m42.superizer.host.storage.PrefsStorage
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.runtime.InstanceRuntime
import cx.m42.superizer.runtime.Platform
import java.nio.file.Files
import kotlinx.serialization.Serializable

/** The smallest registrable app, for tests that are about the host and not about any app. */
@Serializable
internal data class StubConfig(val mode: String = "default")

internal class StubApp(
    id: String = "stub",
    hidden: Boolean = false,
    deepLinks: Set<String> = emptySet(),
    topics: Set<String> = emptySet(),
    lock: LockPolicy = LockPolicy.Off,
    secureWindow: Boolean = false,
    backup: BackupPolicy = BackupPolicy.All,
    minContract: Int? = null,
) : SuperizerApp<StubConfig>() {

    override val manifest: AppManifest = AppManifest(
        id = AppId(id),
        version = "1.0.0",
        minHostContract = minContract ?: when {
            backup != BackupPolicy.All -> 3
            lock != LockPolicy.Off || secureWindow -> 2
            else -> 1
        },
        metadata = AppMetadata(title = localized("en" to id), hidden = hidden),
        deepLinks = deepLinks,
        pushTopics = topics,
        protection = AppProtection(lock, secureWindow),
        backup = backup,
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
 * Each test gets its own preferences directory, and a *new* one on every run.
 *
 * `PrefsStorage` is a process-wide object by design — every platform's store is — so without this
 * each test reads whatever the one before it wrote. A numbered name is not enough: the directories
 * outlive the JVM, so run two would inherit run one's unlocked apps and its haptics switch, and the
 * failures would depend on what happened yesterday.
 */
internal fun isolatePrefs() {
    PrefsStorage.useDirectory(Files.createTempDirectory("superizer-host-test").toFile())
}
