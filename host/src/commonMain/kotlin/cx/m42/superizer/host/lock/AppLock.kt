package cx.m42.superizer.host.lock

import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.LockPolicy
import cx.m42.superizer.app.SuperizerApp
import cx.m42.superizer.host.storage.HostKeys
import cx.m42.superizer.host.storage.SafePrefs
import cx.m42.superizer.lock.AppLockPort
import cx.m42.superizer.lock.AuthAvailability
import cx.m42.superizer.lock.AuthOutcome
import cx.m42.superizer.lock.AuthStrength
import cx.m42.superizer.lock.DeviceAuthenticator
import cx.m42.superizer.lock.LockState
import cx.m42.superizer.lock.UserPresence
import cx.m42.superizer.runtime.Clock
import cx.m42.superizer.runtime.HostLifecycle
import cx.m42.superizer.runtime.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * When the lock closes (06 §5.3, D130–D131), and the one sheet at a time that opens it.
 *
 * ```
 * Background ─┬─ the screen went off                     → lock at once
 *             └─ another app came to the front           → remember when
 * Foreground ─── longer ago than the grace (1 min)       → lock
 * Process died ─ nothing remembered                      → locked
 * Own sheet up ─ Background/Foreground only remembered, judged when the sheet is gone
 * ```
 *
 * The last line is the trap. On API 24–29 the screen-lock PIN is the system's own activity, which
 * the process lifecycle sees as leaving the app; locking on that would put the sheet straight back
 * up after every PIN. So a transition during a sheet is not acted on, only recorded — and judged
 * when the sheet is gone, so that leaving *from* the sheet still counts.
 *
 * The unlock is in memory and only there: this class writes the grace and the person's switches
 * to the host's prefs, never the fact of having been unlocked.
 */
public class AppLockController(
    private val apps: StateFlow<List<SuperizerApp<*>>>,
    private val authenticator: DeviceAuthenticator,
    private val store: LockStore,
    private val clock: Clock,
    private val lifecycle: StateFlow<HostLifecycle>,
    /** Fires when the screen turns off, whether or not the app is in front. */
    private val screenOff: Flow<Unit>,
    /** Whether the screen is on right now — asked at the moment the app leaves the front. */
    private val screenOn: () -> Boolean,
    private val strength: AuthStrength = AuthStrength.Any,
    private val deviceSettings: () -> Boolean = { false },
    private val logger: Logger? = null,
) : AppLockPort {

    private val stored = store.load()

    private val _state = MutableStateFlow(
        LockState(
            availability = authenticator.availability(strength),
            graceMillis = stored.graceMillis,
            choices = stored.choices.mapNotNull { (id, on) -> AppId.parseOrNull(id)?.let { it to on } }.toMap(),
        ),
    )
    override val state: StateFlow<LockState> get() = _state.asStateFlow()

    /** When the app last left the front with the lock open, or null while it is in front. */
    private var leftAt: Long? = null

    /** What this host asks apps to confirm through (D133). Registered only where there is something to ask with. */
    public val presence: UserPresence = object : UserPresence {
        override suspend fun confirm(reason: String): Boolean {
            // D140: nothing to ask with is not a refusal. The banner over the app already says so.
            if (refreshAvailability() != AuthAvailability.Available) return true
            return prompt { authenticator.authenticate(reason, null, strength) } == AuthOutcome.Success
        }
    }

    public fun attach(scope: CoroutineScope) {
        // Now, and not only from the collector below: on Android the host's scope is the main
        // dispatcher, the collector runs a frame later, and a snapshot restored in that frame would
        // be drawn before anything knew it was meant to be covered.
        recompute()
        scope.launch { apps.collect { recompute() } }
        scope.launch {
            var last = lifecycle.value
            lifecycle.collect { now ->
                if (now == last) return@collect
                last = now
                when (now) {
                    HostLifecycle.Background -> onBackground()
                    HostLifecycle.Foreground -> onForeground()
                }
            }
        }
        // Even under the sheet: a screen that went dark is a phone that may be in someone else's hand.
        scope.launch { screenOff.collect { close("screen off") } }
    }

    override suspend fun unlock(title: String, subtitle: String?): AuthOutcome {
        if (refreshAvailability() != AuthAvailability.Available) return AuthOutcome.Error("nothing to ask with")
        // A sheet over a window nobody can see cannot be answered, and on Android putting one up
        // after the activity saved its state throws. The curtain can be drawn in the background —
        // the screen went off — and asks the moment the app is back.
        lifecycle.first { it == HostLifecycle.Foreground }
        val outcome = prompt { authenticator.authenticate(title, subtitle, strength) }
        if (outcome == AuthOutcome.Success) _state.update { it.copy(open = true) }
        return outcome
    }

    override fun lockNow() {
        close("lock now")
    }

    override fun setGrace(millis: Long) {
        val value = millis.coerceAtLeast(0)
        _state.update { it.copy(graceMillis = value) }
        persist()
    }

    override fun setEnabled(id: AppId, enabled: Boolean) {
        val policy = apps.value.firstOrNull { it.id == id }?.manifest?.protection?.lock ?: return
        if (policy != LockPolicy.OptionalOff && policy != LockPolicy.OptionalOn) return
        _state.update { it.copy(choices = it.choices + (id to enabled)) }
        persist()
        recompute()
    }

    override fun openDeviceSettings(): Boolean = deviceSettings()

    // ------------------------------------------------------------------ internals

    /**
     * One sheet at a time, with the lifecycle muted while it is up. Whatever it recorded meanwhile
     * is judged here: a person who answered has just proven themselves, and one who did not may
     * have walked away from the sheet itself.
     */
    private suspend fun prompt(ask: suspend () -> AuthOutcome): AuthOutcome {
        if (_state.value.prompting) return AuthOutcome.Cancelled
        _state.update { it.copy(prompting = true) }
        val outcome = try {
            ask()
        } finally {
            _state.update { it.copy(prompting = false) }
        }
        if (outcome == AuthOutcome.Success) {
            leftAt = null
        } else if (lifecycle.value == HostLifecycle.Foreground) {
            // Back already, so the Foreground that would have judged the absence came and went
            // while the sheet was up. Judge it now.
            val left = leftAt
            leftAt = null
            if (left != null && clock.now() - left >= _state.value.graceMillis) close("away during the sheet")
        }
        // Still away: the Foreground that brings them back judges it, as for any other absence.
        return outcome
    }

    private fun onBackground() {
        if (!_state.value.prompting) {
            if (!screenOn()) return close("screen off")
            if (_state.value.graceMillis == 0L) return close("left the app")
        }
        leftAt = clock.now()
    }

    private fun onForeground() {
        // The screen lock is switched on and off in the system's settings, which is to say from
        // the background: this is the moment to find out whether the banner still holds (§5.8).
        refreshAvailability()
        if (_state.value.prompting) return
        val left = leftAt ?: return
        leftAt = null
        if (clock.now() - left >= _state.value.graceMillis) close("away longer than the grace")
    }

    private fun close(why: String) {
        leftAt = null
        if (!_state.value.open) return
        _state.update { it.copy(open = false) }
        logger?.info("lock closed: $why")
    }

    private fun refreshAvailability(): AuthAvailability {
        val now = authenticator.availability(strength)
        _state.update { it.copy(availability = now) }
        return now
    }

    /** Policy and choice together (D139). A choice outlives a default that changed, not a move to Required. */
    private fun recompute() {
        _state.update { state ->
            state.copy(
                guarded = apps.value.filter { app ->
                    when (app.manifest.protection.lock) {
                        LockPolicy.Off -> false
                        LockPolicy.Required -> true
                        LockPolicy.OptionalOn -> state.choices[app.id] ?: true
                        LockPolicy.OptionalOff -> state.choices[app.id] ?: false
                    }
                }.map { it.id }.toSet(),
            )
        }
    }

    private fun persist() {
        val state = _state.value
        store.save(LockSettings(state.graceMillis, state.choices.mapKeys { it.key.value }))
    }
}

/**
 * What the person decided about the lock (§5.7), stored under `host.lock`. Only what they touched:
 * a switch nobody moved is absent, and the app's own default applies to it.
 */
@Serializable
public data class LockSettings(
    val graceMillis: Long = AppLockPort.DEFAULT_GRACE,
    /** By app id, as written: an id a later build no longer registers is kept, not dropped. */
    val choices: Map<String, Boolean> = emptyMap(),
)

public class LockStore(private val json: Json = Json { ignoreUnknownKeys = true }) {

    public fun load(): LockSettings {
        val raw = SafePrefs.get(HostKeys.LOCK) ?: return LockSettings()
        return runCatching { json.decodeFromString(LockSettings.serializer(), raw) }.getOrDefault(LockSettings())
    }

    public fun save(settings: LockSettings) {
        SafePrefs.put(HostKeys.LOCK, json.encodeToString(LockSettings.serializer(), settings))
    }
}
