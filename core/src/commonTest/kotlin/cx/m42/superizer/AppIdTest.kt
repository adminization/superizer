package cx.m42.superizer

import cx.m42.superizer.app.AppId
import cx.m42.superizer.app.localized
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AppIdTest {

    @Test
    fun kebabCaseIsTheOnlySpelling() {
        assertEquals("currency-converter", AppId("currency-converter").value)
    }

    @Test
    fun anIdThatAPayloadCouldDisagreeAboutIsRefused() {
        // Every source of an id is text from outside the process; one that could arrive as both
        // `Calculator` and `calculator` is an id two payloads disagree about.
        assertFailsWith<IllegalArgumentException> { AppId("Calculator") }
        assertFailsWith<IllegalArgumentException> { AppId("test app") }
        assertFailsWith<IllegalArgumentException> { AppId("-leading") }
        assertFailsWith<IllegalArgumentException> { AppId("") }
    }

    @Test
    fun parsingUntrustedTextAnswersNullRatherThanThrowing() {
        assertNull(AppId.parseOrNull("Calculator"))
        assertNull(AppId.parseOrNull(null))
        assertEquals(AppId("calculator"), AppId.parseOrNull("calculator"))
    }

    @Test
    fun aLocalizedTitleResolvesOnThePrimarySubtag() {
        val title = localized("en" to "Calculator", "ru" to "Калькулятор")
        assertEquals("Калькулятор", title.resolve("ru-RU"))
        assertEquals("Calculator", title.resolve("en"))
        // Neither table matches, so the first entry answers — which is the language it was written in.
        assertEquals("Calculator", title.resolve("fr"))
    }
}
