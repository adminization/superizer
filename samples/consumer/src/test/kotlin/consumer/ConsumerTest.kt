package consumer

import cx.m42.apps.testapp.TestApp
import cx.m42.superizer.testing.AppContractTest
import cx.m42.superizer.host.storage.PrefsStorage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The TCK, run by a consumer.
 *
 * `superizer:testing` being usable from outside is not a nicety: an app written by somebody else
 * has to be able to inherit the same guarantees, or "write an app for any host" is only true for
 * apps written inside this repository.
 */
class ConsumerContractTest : AppContractTest(TestApp())

class ConsumerHostTest {

    @Test
    fun aHostBuiltFromThePublishedPackageRegistersAndEnablesItsApps() {
        PrefsStorage.useDirectory(File(System.getProperty("java.io.tmpdir"), "superizer-consumer-test"))
        val superizer = buildConsumerHost(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

        assertEquals(1, superizer.registry.all().size)
        assertTrue(superizer.handler.isEnabled(cx.m42.superizer.app.AppId("test-app")))
        // Hidden, so it is not on All Apps until something unlocks it — the same rule as anywhere.
        assertTrue(superizer.registry.visible(emptySet()).isEmpty())
    }
}
