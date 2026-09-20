package cx.m42.superizer.host.push

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * No Firebase yet (13 §8). Taps still arrive — the host activity forwards a launch intent's extras
 * — so the routing half is exercised on a device without a push server existing at all.
 */
public actual object PushTransport {
    public actual val token: StateFlow<PushRegistration?> = NoopPushTransport.token
    public actual val incoming: SharedFlow<IncomingPush> = NoopPushTransport.incoming
    public actual val available: Boolean = false

    public actual fun deliverTap(data: Map<String, String>): Unit = NoopPushTransport.deliverTap(data)

    public actual fun deliverMessage(data: Map<String, String>): Unit = NoopPushTransport.deliverMessage(data)

    public actual suspend fun requestPermission(): Boolean = false

    public actual suspend fun subscribeTopic(topic: String): Unit = Unit

    public actual suspend fun unsubscribeTopic(topic: String): Unit = Unit
}
