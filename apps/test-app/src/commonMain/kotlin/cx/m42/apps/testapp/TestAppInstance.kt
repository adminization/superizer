package cx.m42.apps.testapp

import androidx.compose.runtime.Composable
import cx.m42.superizer.app.AppChrome
import cx.m42.superizer.app.AppInstance
import cx.m42.superizer.event.SuperizerEvent
import cx.m42.superizer.runtime.InstanceRuntime
import cx.m42.superizer.runtime.PushMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Everything the bench app is holding while it is open.
 *
 * A plain state holder rather than a view model: the app has one screen, the host owns navigation,
 * and a framework for state that lives exactly as long as this object would be a framework earning
 * nothing.
 */
internal class TestAppInstance(
    val runtime: InstanceRuntime,
    val config: TestConfig,
) : AppInstance() {

    /** Whatever the user typed into the State card — the thing that must survive a process death. */
    val note = MutableStateFlow("")

    /** The event feed, from this instance's own birth. Rendering it is how §22 is answered. */
    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> get() = _events.asStateFlow()

    private val _launches = MutableStateFlow(0)
    val launches: StateFlow<Int> get() = _launches.asStateFlow()

    private val _foregrounds = MutableStateFlow(0)
    val foregrounds: StateFlow<Int> get() = _foregrounds.asStateFlow()

    private val _backgrounds = MutableStateFlow(0)
    val backgrounds: StateFlow<Int> get() = _backgrounds.asStateFlow()

    private val _lastPush = MutableStateFlow<PushMessage?>(null)
    val lastPush: StateFlow<PushMessage?> get() = _lastPush.asStateFlow()

    private val _ping = MutableStateFlow<String?>(null)
    val ping: StateFlow<String?> get() = _ping.asStateFlow()

    private val _navigationError = MutableStateFlow<String?>(null)
    val navigationError: StateFlow<String?> get() = _navigationError.asStateFlow()

    /** The Veto card's switch: the one thing here that changes what the *host* does (03). */
    val blockClose = MutableStateFlow(false)

    private val _chrome = MutableStateFlow(AppChrome())
    override val chrome: StateFlow<AppChrome> get() = _chrome.asStateFlow()

    override suspend fun onLaunch() {
        // Counting launches in storage rather than in a field is the point of the Storage card:
        // it is the one number here that outlives the session.
        val count = (runtime.storage.get(KEY_LAUNCHES)?.toIntOrNull() ?: 0) + 1
        runtime.storage.set(KEY_LAUNCHES, count.toString())
        _launches.value = count

        _chrome.value = AppChrome(subtitle = config.mode)

        runtime.analytics.screen("test-app")

        // The app-level runtime is what push arrives on (D15), but a screen may watch it too.
        runtime.scope.launch { runtime.push.messages.collect { _lastPush.value = it } }
        runtime.scope.launch {
            runtime.events.collect { event ->
                if (event.appId == null || event.appId == runtime.appId) {
                    _events.value = (_events.value + event.describe()).takeLast(MAX_EVENTS)
                }
            }
        }
    }

    override fun saveState(): JsonObject? = buildJsonObject {
        put("note", JsonPrimitive(note.value))
        put("blockClose", JsonPrimitive(blockClose.value))
    }

    override fun restore(state: JsonObject) {
        note.value = state["note"]?.jsonPrimitive?.content.orEmpty()
        blockClose.value = state["blockClose"]?.jsonPrimitive?.content == "true"
    }

    override fun onForeground() {
        _foregrounds.value += 1
    }

    override fun onBackground() {
        _backgrounds.value += 1
    }

    override suspend fun onCloseRequested(): Boolean = !blockClose.value

    @Composable
    override fun Content() {
        TestAppScreen(this)
    }

    // ------------------------------------------------------------------ card actions

    fun ping() {
        runtime.scope.launch {
            val started = runtime.clock.now()
            _ping.value = runCatching {
                val response = runtime.network.get(PING_URL)
                "${response.status} in ${runtime.clock.now() - started} ms"
            }.getOrElse { "failed: ${it.message}" }
        }
    }

    fun sendEvent() {
        // The name is the app's own; the host prefixes the id before it goes anywhere (D33), which
        // is visible in the Service Menu's log as `test-app.test`.
        runtime.analytics.event("test", mapOf("at" to runtime.clock.now().toString()))
    }

    fun clearStorage() {
        runtime.scope.launch {
            runtime.storage.remove(KEY_LAUNCHES)
            _launches.value = 0
        }
    }

    fun openCalculatorScientific() {
        runtime.scope.launch {
            runtime.navigation.openApp(
                cx.m42.superizer.app.AppId("calculator"),
                cx.m42.superizer.app.AppConfig(buildJsonObject { put("mode", JsonPrimitive("scientific")) }),
            ).onFailure { _navigationError.value = it.message }
        }
    }

    fun openUnknownApp() {
        runtime.scope.launch {
            // D22: a typed failure, shown as a line on the card. A boolean here would leave the
            // card unable to say *why*, which is the whole reason the signature changed.
            runtime.navigation.openApp(cx.m42.superizer.app.AppId("nope"))
                .onFailure { _navigationError.value = it.message }
        }
    }

    fun close() {
        runtime.scope.launch { runtime.navigation.close() }
    }

    fun subscribe(topic: String) {
        runtime.scope.launch { runtime.push.subscribe(topic) }
    }

    fun unsubscribe(topic: String) {
        runtime.scope.launch { runtime.push.unsubscribe(topic) }
    }

    fun requestPushPermission() {
        runtime.scope.launch { runtime.push.requestPermission() }
    }

    companion object {
        const val KEY_LAUNCHES = "launches"
        const val PING_URL = "https://api.frankfurter.app/latest?from=USD&to=EUR"
        const val MAX_EVENTS = 40
    }
}

/** Short enough for a card, complete enough to read the sequence off. */
private fun SuperizerEvent.describe(): String {
    val name = this::class.simpleName
    return when (this) {
        is SuperizerEvent.ConfigRejected -> "$name($reason)"
        is SuperizerEvent.LaunchFailed -> "$name($reason)"
        is SuperizerEvent.Activated -> "$name(source=$source)"
        is SuperizerEvent.PushDropped -> "$name($reason)"
        else -> name.orEmpty()
    }
}
