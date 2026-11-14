package cx.m42.superizer.host.push

import android.content.Intent
import android.provider.Settings
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import cx.m42.superizer.host.platform.AndroidHost
import cx.m42.superizer.push.PushPermission
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Firebase Cloud Messaging, which is step 9b of 13 §8 — the seam that every other part of push was
 * written against, finally filled.
 *
 * Nothing above this file changed to make it work: [PushRouter] still collects [incoming], the
 * Service Menu's simulator still calls [deliverMessage], the topic store still calls
 * [subscribeTopic]. That was the point of building the routing half against a no-op first, and it
 * is the reason this file is the whole of the Android implementation.
 *
 * It stays inert rather than crashing when Firebase is absent. `google-services.json` is a
 * per-deployment secret and cannot live in a library, so a host built from a clone with no
 * credentials has no [FirebaseApp] — [available] is then false, every call here returns without
 * touching the network, and the app runs exactly as it did before push existed.
 */
public actual object PushTransport {

    public actual val token: StateFlow<PushRegistration?> = NoopPushTransport.token
    public actual val incoming: SharedFlow<IncomingPush> = NoopPushTransport.incoming

    /**
     * Whether this build can receive anything at all.
     *
     * Computed rather than constant: it is a fact about the *app* the library was linked into, and
     * the library cannot know it at compile time. `FirebaseApp.getApps` is empty exactly when no
     * `google-services.json` was present at build time, which is the case this has to survive.
     */
    public actual val available: Boolean
        get() = AndroidHost.appContext?.let { FirebaseApp.getApps(it).isNotEmpty() } == true

    public actual fun deliverTap(data: Map<String, String>): Unit = NoopPushTransport.deliverTap(data)

    public actual fun deliverMessage(data: Map<String, String>): Unit = NoopPushTransport.deliverMessage(data)

    /**
     * Asks for POST_NOTIFICATIONS ([NotificationPermission.request]), and fetches the token once
     * notifications can be shown. Every `false` means "notifications cannot be shown", not "the
     * user said no".
     */
    public actual suspend fun requestPermission(): Boolean {
        val granted = NotificationPermission.request()
        if (granted) refreshToken()
        return granted
    }

    /**
     * Forwarded by the host activity from `onRequestPermissionsResult`, for push and local
     * reminders alike. Ignoring a request code that is not ours means the host can forward every
     * result blindly.
     */
    public fun onPermissionResult(requestCode: Int, grantResults: IntArray) {
        NotificationPermission.onResult(requestCode, grantResults)
    }

    /** Read without asking: [NotificationPermission.state], or Unavailable with no Firebase. */
    public actual suspend fun permission(): PushPermission {
        if (!available) return PushPermission.Unavailable
        return NotificationPermission.state()
    }

    /**
     * FCM was told not to start by itself (`firebase_messaging_auto_init_enabled = false` in the
     * app's manifest), so until this runs there is no token and Google has heard nothing from this
     * install. Firebase keeps the flag, so a later start wakes it again — after this host has
     * asked, because the person still uses a push app.
     */
    public actual suspend fun activate() {
        if (!available) return
        runCatching { FirebaseMessaging.getInstance().isAutoInitEnabled = true }
        refreshToken()
    }

    public actual fun openSettings(): Boolean {
        val context = AndroidHost.appContext ?: return false
        return runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }

    public actual suspend fun subscribeTopic(topic: String) {
        if (!available) return
        FirebaseMessaging.getInstance().subscribeToTopic(topic.asFcmTopic()).await()
    }

    public actual suspend fun unsubscribeTopic(topic: String) {
        if (!available) return
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic.asFcmTopic()).await()
    }

    /** Set by [SuperizerMessagingService] too, which is the only other thing that learns a token. */
    internal fun publishToken(value: String) {
        NoopPushTransport.token.value = PushRegistration(value, PLATFORM)
    }

    private fun refreshToken() {
        if (!available) return
        // Fire-and-forget: a failure here means no push, which `token` staying null already says.
        // Throwing would take down whatever happened to touch this object first.
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { publishToken(it) }
        }
    }

    private const val PLATFORM = "android"

}

/**
 * Google Play Services speaks `Task`, the rest of this library speaks `suspend`. Written out rather
 * than pulled in as `kotlinx-coroutines-play-services`, because that artifact exists to do exactly
 * this and nothing else, and two call sites do not justify a dependency.
 */
private suspend fun com.google.android.gms.tasks.Task<Void>.await() {
    if (isComplete) return
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener {
            // Resumed on completion whether it succeeded or not: a topic that failed to subscribe
            // is a message that will not arrive, which the caller finds out about by not getting
            // one. Throwing here would make a lost network the app's problem to catch.
            if (continuation.isActive) continuation.resume(Unit)
        }
    }
}
