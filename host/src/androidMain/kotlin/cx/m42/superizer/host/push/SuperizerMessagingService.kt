package cx.m42.superizer.host.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Where a message stops being Firebase's and becomes the host's.
 *
 * Constructed by the system, from the name in this library's `AndroidManifest.xml`, in whatever
 * process FCM felt like waking. It therefore has no route to a composition and no host to call —
 * which is why it hands everything to [PushTransport], an object, and why the seam was an object in
 * the first place.
 *
 * It deliberately does nothing else. It does not decide what a message means, does not draw a
 * notification and does not know which app a message is for: [PushRouter] does all three, on every
 * target, and a second copy of those rules living here is how the Android build would quietly start
 * behaving differently from the tests.
 *
 * **One thing it cannot do.** `incoming` has no replay, so a data message that arrives while no
 * host is collecting is dropped. That is the cold-start case — the process woken only to receive
 * this — and the answer to it is a `notification` payload alongside the data, which Firebase itself
 * displays without any of this code running. A data-only message is for an app that is already open.
 */
public class SuperizerMessagingService : FirebaseMessagingService() {

    /**
     * Fires on install, on a token rotation, and after the user clears app data. Never assume the
     * previous one still works: a server holding a stale token gets no error, only silence.
     */
    override fun onNewToken(token: String) {
        PushTransport.publishToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Only the data half. A `notification` payload is Firebase's to display and never reaches
        // the router, which is the documented split rather than an omission.
        if (message.data.isEmpty()) return
        PushTransport.deliverMessage(message.data)
    }
}
