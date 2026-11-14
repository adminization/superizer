package cx.m42.superizer.host.push

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import cx.m42.superizer.host.platform.AndroidHost
import cx.m42.superizer.host.storage.SafePrefs
import cx.m42.superizer.push.PushPermission
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The system's notification question, with nothing of Firebase in it (0.9.0): push asks it and then
 * fetches a token, local reminders ask it and fetch nothing. Moved out of [PushTransport] unchanged.
 */
internal object NotificationPermission {

    /**
     * Three different questions hide behind one boolean, and they are answered in this order:
     *
     *  * **Already allowed** — true, without a dialog. `areNotificationsEnabled()` covers both the
     *    runtime permission and the user having switched the app's notifications off in system
     *    settings, which is a state no permission check would see.
     *  * **Below API 33** — the permission does not exist, so a `false` here means the user turned
     *    notifications off by hand. Prompting is impossible and would be wrong anyway.
     *  * **API 33+, not yet granted** — the system dialog, which needs an Activity. The host
     *    activity registers itself through [AndroidHost.init] and forwards the result to
     *    [PushTransport.onPermissionResult]; without that forwarding this suspends until cancelled,
     *    which is why a missing Activity returns false instead of hanging.
     */
    suspend fun request(): Boolean {
        val context = AndroidHost.appContext ?: return false
        if (enabled(context)) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val activity = AndroidHost.currentActivity() ?: return false
        // One dialog at a time. A second caller would overwrite `pending`, and the first would then
        // suspend for the life of the process waiting for a result that is handed to somebody else.
        if (pending != null) return false

        // Remembered so [state] can tell "never asked" from "refused for good": the system
        // answers both with no rationale, and only one of them still has a dialog behind it.
        SafePrefs.put(ASKED_KEY, "1")
        return suspendCancellableCoroutine { continuation ->
            pending = continuation
            continuation.invokeOnCancellation { pending = null }
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
        }
    }

    fun onResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != REQUEST_CODE) return
        val continuation = pending ?: return
        pending = null
        if (continuation.isActive) {
            continuation.resume(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
        }
    }

    /**
     *  * notifications enabled — [PushPermission.Granted];
     *  * below API 33, or the permission granted but notifications switched off in Settings — only
     *    Settings can help, [PushPermission.Blocked];
     *  * API 33+ without the permission — the dialog is still there unless we asked before and the
     *    system no longer offers a rationale, which is how Android says "denied twice, never again".
     */
    fun state(): PushPermission {
        val context = AndroidHost.appContext ?: return PushPermission.Unavailable
        if (enabled(context)) return PushPermission.Granted
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return PushPermission.Blocked
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return PushPermission.Blocked
        val activity = AndroidHost.currentActivity()
        val rationale = activity?.shouldShowRequestPermissionRationale(permission) == true
        return if (!rationale && SafePrefs.get(ASKED_KEY) != null) PushPermission.Blocked else PushPermission.Off
    }

    fun enabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return manager?.areNotificationsEnabled() == true
    }

    private const val REQUEST_CODE = 0x5075 // 'P','u'
    private const val ASKED_KEY = "host.push.asked"

    private var pending: CancellableContinuation<Boolean>? = null
}
