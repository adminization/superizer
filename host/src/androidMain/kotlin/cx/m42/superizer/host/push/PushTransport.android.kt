package cx.m42.superizer.host.push

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import cx.m42.superizer.host.platform.AndroidHost
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
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
     * Asks for POST_NOTIFICATIONS, and reports whether notifications can actually be shown.
     *
     * Three different questions hide behind one boolean, and they are answered in this order:
     *
     *  * **Already allowed** — true, without a dialog. `areNotificationsEnabled()` covers both the
     *    runtime permission and the user having switched the app's notifications off in system
     *    settings, which is a state no permission check would see.
     *  * **Below API 33** — the permission does not exist, so a `false` here means the user turned
     *    notifications off by hand. Prompting is impossible and would be wrong anyway.
     *  * **API 33+, not yet granted** — the system dialog, which needs an Activity. The host
     *    activity registers itself through [AndroidHost.init] and forwards the result to
     *    [onPermissionResult]; without that forwarding this suspends until cancelled, which is why
     *    a missing Activity returns false instead of hanging.
     *
     * Every `false` above means "notifications cannot be shown", not "the user said no". The two
     * are worth distinguishing in a UI, and this signature cannot — a caller that needs to tell
     * them apart should read the system setting itself rather than infer it from here.
     */
    public actual suspend fun requestPermission(): Boolean {
        val context = AndroidHost.appContext ?: return false
        if (notificationsEnabled(context)) {
            refreshToken()
            return true
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val activity = AndroidHost.currentActivity() ?: return false
        // One dialog at a time. A second caller would overwrite `pending`, and the first would then
        // suspend for the life of the process waiting for a result that is handed to somebody else
        // — a hang with no error, in a function whose whole contract is to return a boolean.
        if (pending != null) return false

        val granted = suspendCancellableCoroutine { continuation ->
            pending = continuation
            continuation.invokeOnCancellation { pending = null }
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
        }
        if (granted) refreshToken()
        return granted
    }

    /**
     * Forwarded by the host activity from `onRequestPermissionsResult`.
     *
     * A plain static hand-off rather than `registerForActivityResult`, because this object is
     * reached from a `FirebaseMessagingService` and from the router, neither of which has an
     * activity or a composition — the same reason the seam is an object and not a class. Ignoring
     * a request code that is not ours means the host can forward every result blindly.
     */
    public fun onPermissionResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != REQUEST_CODE) return
        val continuation = pending ?: return
        pending = null
        if (continuation.isActive) {
            continuation.resume(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        }
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

    private fun notificationsEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return manager?.areNotificationsEnabled() == true
    }

    private const val PLATFORM = "android"
    private const val REQUEST_CODE = 0x5075 // 'P','u'

    private var pending: CancellableContinuation<Boolean>? = null

    /**
     * Last in the object body, not first, because an object initialises in declaration order: an
     * `init` at the top runs before the properties below it exist. Nothing here reads them today,
     * which is exactly why the ordering would have gone unnoticed until something did.
     *
     * The token usually exists long before anything asks for it — Firebase's own init provider runs
     * before `Application.onCreate` — so asking here means the first read of [token] is already
     * populated rather than null until a message happens to arrive.
     */
    init {
        refreshToken()
    }
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
