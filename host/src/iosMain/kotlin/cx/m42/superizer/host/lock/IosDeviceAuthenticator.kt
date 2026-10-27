package cx.m42.superizer.host.lock

import cx.m42.superizer.lock.AuthAvailability
import cx.m42.superizer.lock.AuthOutcome
import cx.m42.superizer.lock.AuthStrength
import cx.m42.superizer.lock.DeviceAuthenticator
import cx.m42.superizer.runtime.Logger
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorBiometryLockout
import platform.LocalAuthentication.LAErrorPasscodeNotSet
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAErrorUserFallback
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics

/**
 * `LocalAuthentication`: Face ID or Touch ID only, with no "Enter Passcode" on the sheet, whenever
 * one is enrolled; the device passcode only on a phone with neither. A passcode is the thing a
 * thief watches over a shoulder, and it opened 2FA and Passwords (Unitool .ai-notes/vault-auth §1).
 * After too many failed faces the system locks biometry until the phone is unlocked with the
 * passcode — at the lock screen, not here. The same policy for both strengths: every biometric iOS
 * offers is what Android calls Class 3.
 *
 * Still a curtain over the screen (D128): someone with the passcode can enrol their own face in
 * Settings. Keys bound to the current biometry set are vault-auth's job.
 *
 * The tries, the delays and the lockout are the system's, as on Android. What an app may add is
 * only the line under the sheet: [authenticate]'s title.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosDeviceAuthenticator(
    private val debug: Boolean,
    private val logger: Logger,
) : DeviceAuthenticator {

    private var reported = false

    override fun availability(strength: AuthStrength): AuthAvailability {
        // A Face ID device kills an app that asks without saying why in its Info.plist — not a
        // refusal, the process is gone. Found on the first run of a debug build, never in the field.
        if (NSBundle.mainBundle.objectForInfoDictionaryKey(FACE_ID_REASON) == null) {
            val message = "Info.plist has no $FACE_ID_REASON: the lock cannot use Face ID"
            if (debug) error(message)
            if (!reported) logger.error(message)
            reported = true
            return AuthAvailability.Unsupported
        }
        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val can = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error.ptr)
            when {
                can -> AuthAvailability.Available
                error.value?.code == LAErrorPasscodeNotSet -> AuthAvailability.NoScreenLock
                else -> AuthAvailability.Unsupported
            }
        }
    }

    override suspend fun authenticate(title: String, subtitle: String?, strength: AuthStrength): AuthOutcome =
        suspendCancellableCoroutine { continuation ->
            val context = LAContext()
            // Empty hides the sheet's fallback button, which would otherwise offer the passcode.
            context.localizedFallbackTitle = ""
            // The reply arrives on a queue of LocalAuthentication's own; resuming from there is fine.
            context.evaluatePolicy(policy(), localizedReason = title) { success, error ->
                val outcome = if (success) AuthOutcome.Success else outcomeOf(error)
                if (continuation.isActive) continuation.resume(outcome)
            }
            continuation.invokeOnCancellation { context.invalidate() }
        }

    /**
     * Biometrics when the phone has them enrolled — locked out included, so five failed faces end
     * in "locked out" rather than in the passcode. The passcode only on a phone with no Face ID or
     * Touch ID set up, where it is all there is.
     */
    private fun policy(): Long = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val biometry = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error.ptr) ||
            error.value?.code == LAErrorBiometryLockout
        if (biometry) LAPolicyDeviceOwnerAuthenticationWithBiometrics else LAPolicyDeviceOwnerAuthentication
    }

    private fun outcomeOf(error: NSError?): AuthOutcome {
        val message = error?.localizedDescription ?: "authentication failed"
        return when (error?.code) {
            LAErrorUserCancel, LAErrorSystemCancel, LAErrorAppCancel, LAErrorUserFallback -> AuthOutcome.Cancelled
            LAErrorBiometryLockout -> AuthOutcome.LockedOut(message)
            else -> AuthOutcome.Error(message)
        }
    }

    private companion object {
        const val FACE_ID_REASON = "NSFaceIDUsageDescription"
    }
}
