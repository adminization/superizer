package cx.m42.superizer

import androidx.compose.runtime.Composable
import cx.m42.superizer.app.AppConfigSpec
import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.app.AppManifest
import cx.m42.superizer.app.AppMetadata
import cx.m42.superizer.app.AppSetupContext
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.app.localized
import cx.m42.superizer.registry.SessionSnapshot
import cx.m42.superizer.registry.SnapshotStore
import cx.m42.superizer.runtime.AppRuntime
import cx.m42.superizer.runtime.HostInfo
import cx.m42.superizer.runtime.InstanceRuntime
import cx.m42.superizer.runtime.Platform
import cx.m42.superizer.runtime.ServiceKey
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The smallest app that is still a real one: a config, a snapshot, and hooks that record what the
 * handler did to them. Every handler test is written against this rather than against a product
 * app, so a test failure means the handler changed.
 */
@Serializable
internal data class ProbeConfig(val mode: String = "default")

internal class ProbeInstance(
    private val runtime: InstanceRuntime,
    val config: ProbeConfig,
    private val vetoClose: Boolean,
    private val failOnLaunch: Boolean,
) : AppInstance() {

    val calls: MutableList<String> = mutableListOf()
    var entry: String = ""

    override suspend fun onLaunch() {
        calls += "onLaunch"
        if (failOnLaunch) error("onLaunch refused")
    }

    override fun saveState(): JsonObject? = buildJsonObject { put("entry", JsonPrimitive(entry)) }

    override fun restore(state: JsonObject) {
        calls += "restore"
        entry = (state["entry"] as? JsonPrimitive)?.content.orEmpty()
    }

    override fun onBackground() {
        calls += "onBackground"
    }

    override fun onForeground() {
        calls += "onForeground"
    }

    override suspend fun onCloseRequested(): Boolean = !vetoClose

    override fun onClose() {
        calls += "onClose"
    }

    override fun dispose() {
        calls += "dispose"
    }

    /** Lets a test do what D31 is about: throw from the instance's own scope, not from a call. */
    fun crash() {
        runtime.scope.launch { error("boom") }
    }

    @Composable
    override fun Content() {
    }
}

internal class ProbeApp(
    id: String = "probe",
    hidden: Boolean = false,
    minHostContract: Int = 1,
    requires: Set<ServiceKey<*>> = emptySet(),
    deepLinks: Set<String> = emptySet(),
    private val registersUndeclaredLink: String? = null,
    private val vetoClose: Boolean = false,
    private val failOnLaunch: Boolean = false,
    fallbackToDefault: Boolean = true,
) : SuperizerApp<ProbeConfig>() {

    override val manifest: AppManifest = AppManifest(
        id = AppId(id),
        version = "1.0.0",
        minHostContract = minHostContract,
        metadata = AppMetadata(title = localized("en" to "Probe"), hidden = hidden),
        requires = requires,
        deepLinks = deepLinks,
    )

    override val configSpec: AppConfigSpec<ProbeConfig> =
        AppConfigSpec(ProbeConfig.serializer(), ProbeConfig(), fallbackToDefault = fallbackToDefault)

    var lastInstance: ProbeInstance? = null

    override fun setup(ctx: AppSetupContext) {
        manifest.deepLinks.forEach { path ->
            ctx.deepLink(path) { params ->
                configSpec.encode(ProbeConfig(mode = params["mode"] ?: "link"))
            }
        }
        registersUndeclaredLink?.let { path -> ctx.deepLink(path) { cx.m42.superizer.app.AppConfig.Empty } }
    }

    override fun launch(runtime: InstanceRuntime, config: ProbeConfig): AppInstance =
        ProbeInstance(runtime, config, vetoClose, failOnLaunch).also { lastInstance = it }
}

internal fun testHost(contractVersion: Int = SuperizerContract.VERSION, services: Set<ServiceKey<*>> = emptySet()) =
    HostInfo(
        name = "Test",
        version = "0.0.0",
        build = "test",
        platform = Platform.Desktop,
        contractVersion = contractVersion,
        debug = true,
        services = services,
    )

internal class MemorySnapshots : SnapshotStore {
    var snapshot: SessionSnapshot? = null

    override suspend fun save(snapshot: SessionSnapshot) {
        this.snapshot = snapshot
    }

    override suspend fun load(): SessionSnapshot? = snapshot

    override suspend fun clear() {
        snapshot = null
    }
}
