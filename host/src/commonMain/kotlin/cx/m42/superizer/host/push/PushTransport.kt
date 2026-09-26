package cx.m42.superizer.host.push

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** A device registration as the server would want it. */
public data class PushRegistration(val token: String, val platform: String)

/** One message off the wire. [tapped] is the difference between the two roads in 13 §1. */
public data class IncomingPush(val data: Map<String, String>, val tapped: Boolean)

/**
 * The platform seam for push (D28).
 *
 * An `expect object` with flows rather than a class with callbacks, because the entry points are
 * static — a `FirebaseMessagingService` is constructed by the system and has no route to a
 * composition — which is exactly the shape `agentiz/push` arrived at for the same reason.
 *
 * Every actual is a no-op today. That is the whole design: the router, the topic store, the
 * simulator in the Service Menu and the bench app's Push card all work against this, so adding FCM
 * later is one `actual` file and a `google-services.json` — and if it is not, step 9b was done
 * wrong.
 */
public expect object PushTransport {
    public val token: StateFlow<PushRegistration?>
    public val incoming: SharedFlow<IncomingPush>
    public val available: Boolean

    /** From the tap intent or the launch options; may arrive before anything is composed. */
    public fun deliverTap(data: Map<String, String>)

    /** Feeds a message in as if it had arrived — what the Service Menu's simulator uses (13 §7). */
    public fun deliverMessage(data: Map<String, String>)

    public suspend fun requestPermission(): Boolean

    public suspend fun subscribeTopic(topic: String)

    public suspend fun unsubscribeTopic(topic: String)
}

/**
 * FCM accepts `[a-zA-Z0-9-_.~%]` and nothing else in a topic name, and rejects the whole call
 * otherwise. The host's qualified names are `<appId>.<topic>`, and an app id is not constrained to
 * that set — so the mapping happens once, here, for both platforms that speak FCM.
 */
internal fun String.asFcmTopic(): String = map { if (it in FCM_TOPIC_CHARS) it else '_' }.joinToString("")

private val FCM_TOPIC_CHARS: Set<Char> = (('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('-', '_', '.', '~', '%')).toSet()

/**
 * The shared no-op body, so the three actuals are a line each and cannot drift apart. When Android
 * grows a real transport it stops delegating to this; the others keep it.
 */
public object NoopPushTransport {
    public val token: MutableStateFlow<PushRegistration?> = MutableStateFlow(null)
    public val incoming: MutableSharedFlow<IncomingPush> =
        MutableSharedFlow(replay = 0, extraBufferCapacity = 16)

    public fun deliverTap(data: Map<String, String>) {
        incoming.tryEmit(IncomingPush(data, tapped = true))
    }

    public fun deliverMessage(data: Map<String, String>) {
        incoming.tryEmit(IncomingPush(data, tapped = false))
    }
}
