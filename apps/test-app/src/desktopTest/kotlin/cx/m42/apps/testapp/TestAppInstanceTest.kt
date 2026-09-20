package cx.m42.apps.testapp

import cx.m42.superizer.app.AppConfig
import cx.m42.superizer.app.AppId
import cx.m42.superizer.testing.runAppTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The bench app is a test instrument, so these are tests of the instrument: if its Storage card
 * miscounts or its Navigation card swallows an error, nothing it reports afterwards can be trusted.
 */
class TestAppInstanceTest {

    private fun config(json: String) = AppConfig(Json.parseToJsonElement(json) as JsonObject)

    @Test
    fun theConfigCardShowsWhatArrivedAndNothingAboutWhereItCameFrom() = runAppTest(
        TestApp(),
        config = config("""{"mode":"promo"}"""),
    ) {
        assertShown("test-app:section-config")
        // §12–13: there is no field for the source, because there is nothing to read.
        assertEquals("promo", (instance as TestAppInstance).config.mode)
    }

    @Test
    fun theLaunchCounterIsTheOneNumberThatOutlivesTheSession() = runAppTest(
        TestApp(),
        prepare = { it.storage.entries[TestAppInstance.KEY_LAUNCHES] = "4" },
    ) {
        assertEquals(5, (instance as TestAppInstance).launches.value)
        assertEquals("5", runtime.storage.entries[TestAppInstance.KEY_LAUNCHES])
    }

    @Test
    fun theNoteSurvivesASnapshotRoundTrip() {
        var saved: JsonObject? = null
        runAppTest(TestApp()) {
            (instance as TestAppInstance).note.value = "remember me"
            saved = instance.saveState()
        }
        runAppTest(TestApp(), restore = saved) {
            assertEquals("remember me", (instance as TestAppInstance).note.value)
        }
    }

    @Test
    fun blockingCloseIsWhatMakesTheHostAsk() = runAppTest(TestApp()) {
        val bench = instance as TestAppInstance
        bench.blockClose.value = true
        kotlinx.coroutines.test.runTest {
            assertEquals(false, bench.onCloseRequested())
        }
    }

    @Test
    fun openingAnAppThatDoesNotExistIsATypedFailureAndNotSilence() = runAppTest(TestApp()) {
        val bench = instance as TestAppInstance
        runtime.navigation.unknown += AppId("nope")
        bench.openUnknownApp()
        idle()
        // D22: the card can say *why*, which a boolean would not have allowed.
        assertTrue(bench.navigationError.value.orEmpty().contains("nope"))
    }

    @Test
    fun theAnalyticsCardSendsTheAppsOwnNameAndLetsTheHostPrefixIt() = runAppTest(TestApp()) {
        (instance as TestAppInstance).sendEvent()
        assertEquals(listOf("test"), runtime.analytics.events.map { it.first })
    }

    @Test
    fun aPushForThisAppLandsOnTheCard() = runAppTest(TestApp()) {
        runtime.push.deliver(
            topic = "demo",
            data = buildJsonObject { put("n", JsonPrimitive(1)) },
            link = "superizer://app/test-app/echo?x=1",
        )
        idle()
        assertEquals("demo", (instance as TestAppInstance).lastPush.value?.topic)
    }
}
