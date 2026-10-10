package cx.m42.superizer.push

import cx.m42.superizer.app.AppId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether this device will show a notification, as the system answers it (push-opt-in, D424). */
public enum class PushPermission {
    /** Notifications are shown. */
    Granted,

    /** Not yet, and the system's question can still be asked. */
    Off,

    /** Refused for good, or switched off in the system's settings: only those settings can turn it back on. */
    Blocked,

    /** No transport in this build or on this platform — desktop, the web, a build without Firebase. */
    Unavailable,
}

/** The device row on the push server, for the Service Menu. Never the secret, never the whole token. */
public data class PushDeviceStatus(
    /** `d_…` once the server has answered a registration; null before, or with no server configured. */
    val deviceId: String? = null,
    /** The token's first characters, which is enough to tell two apart. */
    val tokenHead: String? = null,
    /** The apps the server was last told about. */
    val apps: List<String> = emptyList(),
    /** When the server last accepted a registration or an update, epoch milliseconds. */
    val syncedAt: Long? = null,
    /** The last failure, as a short code; null after a success. */
    val error: String? = null,
)

/**
 * Push as the shell sees it (push-opt-in): which apps it is for, whether the system lets it show,
 * and the one action a person has — turning it on.
 *
 * Apps never see this. They see `runtime.push`.
 */
public interface PushPort {

    /**
     * Push apps the person has started using (D418): added from the catalog, opened, or restored
     * from a backup. Nothing about push — token, server, topics — happens while this is empty.
     */
    public val used: StateFlow<Set<AppId>>

    public val permission: StateFlow<PushPermission>

    public val device: StateFlow<PushDeviceStatus>

    /** The system's question while it can still be asked; the system's settings once it cannot. */
    public suspend fun enable()

    /** A host with no push at all — what a [cx.m42.superizer.Superizer] that predates this port answers. */
    public object None : PushPort {
        override val used: StateFlow<Set<AppId>> = MutableStateFlow(emptySet<AppId>()).asStateFlow()
        override val permission: StateFlow<PushPermission> =
            MutableStateFlow(PushPermission.Unavailable).asStateFlow()
        override val device: StateFlow<PushDeviceStatus> = MutableStateFlow(PushDeviceStatus()).asStateFlow()
        override suspend fun enable(): Unit = Unit
    }
}
