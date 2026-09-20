package cx.m42.superizer.host

import cx.m42.superizer.activation.ActivationResult
import cx.m42.superizer.app.AppId
import cx.m42.superizer.host.activation.QrPayloadParser
import cx.m42.superizer.registry.AppRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive

/**
 * The parser meets text somebody else produced: a code printed a year ago, a URL pasted by hand, a
 * payload from a build that does not exist yet.
 *
 * Every case below answers with a typed rejection, and none of them throws. That is the whole
 * specification — an exception here is not something the Activate screen can show anybody.
 */
class QrPayloadParserTest {

    private val registry = AppRegistry(testHostInfo()).apply {
        register(StubApp("calculator"))
    }
    private val parser = QrPayloadParser(registry, scheme = "unitool")

    private fun reason(text: String): ActivationResult.Reason? =
        (parser.parse(text) as? ActivationResult.Rejected)?.reason

    @Test
    fun aWellFormedPayloadNamesAnAppAndItsConfig() {
        val result = parser.parse(
            """{"schemaVersion":1,"type":"app_activation","appId":"calculator","config":{"mode":"scientific"}}""",
        )
        assertTrue(result is ActivationResult.Success)
        assertEquals(AppId("calculator"), result.activation.appId)
        assertEquals(JsonPrimitive("scientific"), result.activation.config.json["mode"])
    }

    @Test
    fun aPayloadWithNoConfigOpensTheAppOnItsDefaults() {
        val result = parser.parse("""{"schemaVersion":1,"type":"app_activation","appId":"calculator"}""")
        assertTrue(result is ActivationResult.Success)
        assertTrue(result.activation.config.json.isEmpty())
    }

    @Test
    fun theCompactUrlFormIsTheSameParser() {
        // A long QR code degrades to this, and a deep link is the same thing arriving another way.
        val result = parser.parse("unitool://activate?a=calculator&c=eyJtb2RlIjoic2NpZW50aWZpYyJ9")
        assertTrue(result is ActivationResult.Success)
        assertEquals(JsonPrimitive("scientific"), result.activation.config.json["mode"])
    }

    @Test
    fun theUrlFormAlsoAcceptsPlainJsonInTheQuery() {
        val result = parser.parse("""unitool://activate?a=calculator&c=%7B%22mode%22%3A%22scientific%22%7D""")
        assertTrue(result is ActivationResult.Success)
        assertEquals(JsonPrimitive("scientific"), result.activation.config.json["mode"])
    }

    @Test
    fun everyShapeOfRubbishHasItsOwnAnswer() {
        // The table from 12 §4.7, line by line. Each entry is a thing that has actually been typed
        // into a field like this one.
        assertEquals(ActivationResult.Reason.Malformed, reason(""))
        assertEquals(ActivationResult.Reason.Malformed, reason("   "))
        assertEquals(ActivationResult.Reason.Malformed, reason("hello"))
        assertEquals(ActivationResult.Reason.Malformed, reason("[1,2,3]"))
        assertEquals(ActivationResult.Reason.Malformed, reason("""{"type":"app_activation"}"""))
        assertEquals(ActivationResult.Reason.Malformed, reason("""{"type":"something_else","appId":"calculator"}"""))
        // An id with a capital or a space could never match the one the registry holds.
        assertEquals(ActivationResult.Reason.Malformed, reason("""{"type":"app_activation","appId":"Calculator"}"""))
        assertEquals(ActivationResult.Reason.Malformed, reason("""{"type":"app_activation","appId":"a b"}"""))
        // A number where a string belongs: `.content` on it would throw.
        assertEquals(ActivationResult.Reason.Malformed, reason("""{"type":"app_activation","appId":7}"""))
        assertEquals(ActivationResult.Reason.UnknownApp, reason("""{"type":"app_activation","appId":"weather"}"""))
        assertEquals(ActivationResult.Reason.UnsupportedSchema, reason("""{"schemaVersion":2,"type":"app_activation","appId":"calculator"}"""))
        assertEquals(ActivationResult.Reason.UnsupportedSchema, reason("""{"schemaVersion":-1,"type":"app_activation","appId":"calculator"}"""))
        assertEquals(ActivationResult.Reason.Malformed, reason("unitool://nonsense"))
        assertEquals(ActivationResult.Reason.Malformed, reason("unitool://activate"))
        assertEquals(ActivationResult.Reason.UnknownApp, reason("unitool://activate?a=weather"))
    }

    @Test
    fun deeplyNestedAndVeryLongInputAreRejectedRatherThanHanging() {
        assertEquals(ActivationResult.Reason.Malformed, reason("[".repeat(2000) + "]".repeat(2000)))
        assertEquals(ActivationResult.Reason.Malformed, reason("x".repeat(64 * 1024)))
    }

    @Test
    fun unicodeAndRightToLeftTextAreJustText() {
        assertEquals(ActivationResult.Reason.Malformed, reason("Привет مرحبا 🙂"))
    }

    @Test
    fun aBrokenConfigInsideAValidActivationIsNotTheParsersProblem() {
        // 06: it must reach the handler, which reports `ConfigRejected` and applies the app's own
        // `fallbackToDefault`. Rejecting it here would hide a decision the app gets to make.
        val result = parser.parse("""{"type":"app_activation","appId":"calculator","config":{"mode":{"deep":1}}}""")
        assertTrue(result is ActivationResult.Success)
    }
}
