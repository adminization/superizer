package cx.m42.superizer.testing

import cx.m42.superizer.lock.AuthAvailability
import cx.m42.superizer.lock.AuthOutcome
import cx.m42.superizer.lock.AuthStrength
import cx.m42.superizer.lock.DeviceAuthenticator
import cx.m42.superizer.lock.UserPresence
import kotlinx.coroutines.CompletableDeferred

/**
 * The device's sheet, answered by the test (06 §11).
 *
 * [availability] is what the device can ask with, and a test changes it the way a person changes
 * their screen lock. Each sheet answers [answer] — or, after [hold], waits until the test completes
 * the deferred, which is how "the lifecycle is ignored while the sheet is up" gets a moment to be
 * tested in.
 */
public class FakeDeviceAuthenticator(
    public var availability: AuthAvailability = AuthAvailability.Available,
    public var answer: AuthOutcome = AuthOutcome.Success,
) : DeviceAuthenticator {

    /** Every sheet that was put up, by title, in order. */
    public val prompts: MutableList<String> = mutableListOf()

    /** Every strength that was asked for, in the same order as [prompts]. */
    public val strengths: MutableList<AuthStrength> = mutableListOf()

    private var held: CompletableDeferred<AuthOutcome>? = null

    /** The next sheet stays up until the returned deferred is completed. */
    public fun hold(): CompletableDeferred<AuthOutcome> = CompletableDeferred<AuthOutcome>().also { held = it }

    override fun availability(strength: AuthStrength): AuthAvailability = availability

    override suspend fun authenticate(title: String, subtitle: String?, strength: AuthStrength): AuthOutcome {
        prompts += title
        strengths += strength
        val waiting = held
        if (waiting != null) {
            held = null
            return waiting.await()
        }
        return answer
    }
}

/** An app's view of the confirmation (D133): answers [answer] and remembers what it was asked for. */
public class FakeUserPresence(public var answer: Boolean = true) : UserPresence {
    public val reasons: MutableList<String> = mutableListOf()

    override suspend fun confirm(reason: String): Boolean {
        reasons += reason
        return answer
    }
}
