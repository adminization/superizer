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

/**
 * `LocalAuthentication`: Face ID or Touch ID, and the device passcode when neither is enrolled or
 * neither worked (D129). The same policy for both strengths — every biometric iOS offers is what
 * Android calls Class 3, and the passcode is the passcode.
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
            // The reply arrives on a queue of LocalAuthentication's own; resuming from there is fine.
            context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = title) { success, error ->
                val outcome = if (success) AuthOutcome.Success else outcomeOf(error)
                if (continuation.isActive) continuation.resume(outcome)
            }
            continuation.invokeOnCancellation { context.invalidate() }
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
