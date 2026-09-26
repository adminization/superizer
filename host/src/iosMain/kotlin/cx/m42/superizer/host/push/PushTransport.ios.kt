package cx.m42.superizer.host.push

import kotlin.coroutines.resume
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIApplication
import platform.UIKit.registerForRemoteNotifications
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * What only the app's Swift side can do: talk to Firebase Messaging.
 *
 * The Firebase iOS SDK is a Swift package linked into the *app* target (13 §8, agentiz's pattern),
 * and a Kotlin library cannot link against it — so the app hands this in at launch, once
 * `FirebaseApp.configure()` has run. Without `GoogleService-Info.plist` it never does, and push is
 * inert exactly as it is on an Android build with no `google-services.json`.
 */
public interface IosMessaging {
    public fun subscribe(topic: String)
    public fun unsubscribe(topic: String)
}

/**
 * FCM on iOS, the same route Android takes: the registration is an FCM token, and Google talks to
 * APNs on the server's behalf, so nothing here or on a server has to know which APNs environment a
 * build is in.
 *
 * The entry points are the app delegate's: [deliverToken] from `MessagingDelegate`, [deliverTap]
 * from the notification response, [deliverMessage] from a data message. Everything after them is
 * the same router, topic store and simulator every other platform runs.
 */
public actual object PushTransport {

    public actual val token: StateFlow<PushRegistration?> = NoopPushTransport.token
    public actual val incoming: SharedFlow<IncomingPush> = NoopPushTransport.incoming

    /** Set by the app at launch; null in a build with no Firebase configuration. */
    public var messaging: IosMessaging? = null

    public actual val available: Boolean get() = messaging != null

    public actual fun deliverTap(data: Map<String, String>): Unit = NoopPushTransport.deliverTap(data)

    public actual fun deliverMessage(data: Map<String, String>): Unit = NoopPushTransport.deliverMessage(data)

    /** The FCM token, first on registration and again whenever Firebase rotates it. */
    public fun deliverToken(token: String) {
        NoopPushTransport.token.value = PushRegistration(token, PLATFORM)
    }

    /**
     * The system's one-time question, then APNs registration — which is what gives Firebase the
     * device token it mints the FCM token from. Asked again after an answer, iOS answers from the
     * stored decision without a dialog, which is the same contract Android's version keeps.
     */
    public actual suspend fun requestPermission(): Boolean {
        val granted = suspendCancellableCoroutine { continuation ->
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound,
            ) { granted, _ ->
                if (continuation.isActive) continuation.resume(granted)
            }
        }
        if (granted && available) {
            // UIKit is main-thread only, and the answer above arrives on a queue of its own.
            dispatch_async(dispatch_get_main_queue()) {
                UIApplication.sharedApplication.registerForRemoteNotifications()
            }
        }
        return granted
    }

    public actual suspend fun subscribeTopic(topic: String) {
        messaging?.subscribe(topic.asFcmTopic())
    }

    public actual suspend fun unsubscribeTopic(topic: String) {
        messaging?.unsubscribe(topic.asFcmTopic())
    }

    private const val PLATFORM = "ios"
}
