package cx.m42.superizer.host.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleDestroyedException
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.withStarted
import cx.m42.superizer.host.platform.AndroidHost
import cx.m42.superizer.lock.AuthAvailability
import cx.m42.superizer.lock.AuthOutcome
import cx.m42.superizer.lock.AuthStrength
import cx.m42.superizer.lock.DeviceAuthenticator
import cx.m42.superizer.runtime.Logger
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * `androidx.biometric`'s sheet: a fingerprint, a face, or the screen lock's own PIN, pattern or
 * password when neither is there or neither worked (D129). Unitool has no PIN of its own; the tries,
 * the delays and the lockout are the system's.
 *
 * `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` for [AuthStrength.Any], because face unlock on most Android
 * phones is Class 2, and with STRONG it would silently not be offered — and "Face ID" is what was
 * asked for. The class of a biometric matters to a Keystore key, not to a curtain (06 §5.2).
 */
internal class AndroidDeviceAuthenticator(
    private val debug: Boolean,
    private val logger: Logger,
) : DeviceAuthenticator {

    private var reported = false

    override fun availability(strength: AuthStrength): AuthAvailability {
        val context = AndroidHost.appContext ?: return AuthAvailability.Unsupported
        val activity = AndroidHost.currentActivity()
        if (activity != null && activity !is FragmentActivity) {
            misconfigured(activity)
            return AuthAvailability.Unsupported
        }
        // Every biometric sits on top of a screen lock — Android does not let one be enrolled
        // without it — so the screen lock alone answers the question for both strengths.
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            ?: return AuthAvailability.Unsupported
        return if (keyguard.isDeviceSecure) AuthAvailability.Available else AuthAvailability.NoScreenLock
    }

    override suspend fun authenticate(title: String, subtitle: String?, strength: AuthStrength): AuthOutcome =
        withContext(Dispatchers.Main.immediate) {
            val activity = AndroidHost.currentActivity() as? FragmentActivity
                ?: return@withContext AuthOutcome.Error("no activity to show the sheet over")
            // The fragment the sheet lives in cannot be added once the activity has saved its state,
            // and the screen going off is exactly when the curtain comes down. Wait for it to be seen.
            try {
                activity.lifecycle.withStarted { }
            } catch (_: LifecycleDestroyedException) {
                return@withContext AuthOutcome.Cancelled
            }
            show(activity, title, subtitle, strength)
        }

    private suspend fun show(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        strength: AuthStrength,
    ): AuthOutcome = suspendCancellableCoroutine { continuation ->
        // An activity destroyed under the sheet takes the callback with it, and a caller left
        // waiting forever would keep the host's "sheet is up" flag raised — which mutes the lock.
        var watcher: LifecycleEventObserver? = null

        // Always on the main thread: the sheet's callbacks and lifecycle events both arrive there.
        fun finish(outcome: AuthOutcome) {
            watcher?.let { activity.lifecycle.removeObserver(it) }
            watcher = null
            if (continuation.isActive) continuation.resume(outcome)
        }

        val prompt = BiometricPrompt(
            activity,
            MainThread,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    finish(AuthOutcome.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    finish(outcomeOf(errorCode, errString.toString()))
                }

                // onAuthenticationFailed is one finger that did not match; the sheet stays up and
                // says so itself, and the person tries again.
            },
        )
        watcher = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) finish(AuthOutcome.Cancelled)
        }.also { activity.lifecycle.addObserver(it) }
        continuation.invokeOnCancellation {
            MainThread.execute {
                runCatching { prompt.cancelAuthentication() }
                finish(AuthOutcome.Cancelled)
            }
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (!subtitle.isNullOrBlank()) setSubtitle(subtitle) }
            .setAllowedAuthenticators(authenticators(strength))
            // A face needs no extra "Confirm" tap: the bank apps this imitates do not ask for one.
            .setConfirmationRequired(false)
            .build()
        runCatching { prompt.authenticate(info) }
            .onFailure { finish(AuthOutcome.Error(it.message ?: "the sheet could not be shown")) }
    }

    /**
     * STRONG together with DEVICE_CREDENTIAL exists from API 30. Before that the Keystore's
     * time-bound keys accept any unlock of the device anyway, so the weaker sheet is what stage 2
     * would have shown there too (06 §8.5).
     */
    private fun authenticators(strength: AuthStrength): Int =
        if (strength == AuthStrength.Strong && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }

    private fun outcomeOf(code: Int, message: String): AuthOutcome = when (code) {
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_CANCELED,
        -> AuthOutcome.Cancelled

        BiometricPrompt.ERROR_LOCKOUT,
        BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
        -> AuthOutcome.LockedOut(message)

        else -> AuthOutcome.Error(message)
    }

    /**
     * §5.8, last row: the sheet needs a `FragmentActivity`, and a host whose activity is not one has
     * no lock at all. In a debug build that is a crash on the first frame, so it is found on the
     * first run; in a release it is an error in the log and the "not protected" banner.
     */
    private fun misconfigured(activity: Activity) {
        val message = "${activity::class.qualifiedName} is not a FragmentActivity: the lock cannot show its sheet"
        if (debug) error(message)
        if (!reported) logger.error(message)
        reported = true
    }

    private object MainThread : Executor {
        private val handler = Handler(Looper.getMainLooper())

        override fun execute(command: Runnable) {
            if (Looper.myLooper() == Looper.getMainLooper()) command.run() else handler.post(command)
        }
    }
}
