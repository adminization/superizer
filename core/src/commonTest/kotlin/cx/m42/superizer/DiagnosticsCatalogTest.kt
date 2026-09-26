package cx.m42.superizer

import cx.m42.superizer.diagnostics.DiagnosticsAction
import cx.m42.superizer.diagnostics.DiagnosticsCode
import cx.m42.superizer.diagnostics.SelfTestCode
import cx.m42.superizer.diagnostics.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The catalogue of 06 §6.1 as the library states it. The words are the host's; what is checked here
 * is what the words cannot fix — a code with no way out, two codes with one id.
 */
class DiagnosticsCatalogTest {

    @Test
    fun everyCodeIsUniqueAndFindsItself() {
        assertEquals(DiagnosticsCode.entries.size, DiagnosticsCode.entries.map { it.id }.toSet().size)
        DiagnosticsCode.entries.forEach { assertEquals(it, DiagnosticsCode.of(it.id)) }
        assertEquals(SelfTestCode.entries.size, SelfTestCode.entries.map { it.id }.toSet().size)
    }

    /** I21, the half the library owns: at Unsafe or worse there is always something to do, if only to report it. */
    @Test
    fun unsafeOrWorseAlwaysOffersAnAction() {
        DiagnosticsCode.entries.filter { it.status >= Status.Unsafe }.forEach { code ->
            assertTrue(code.actions.isNotEmpty(), "${code.id} is ${code.status} and offers nothing")
        }
        assertTrue(DiagnosticsCode.SelfTestFailed.actions.contains(DiagnosticsAction.ShareReport))
    }

    @Test
    fun statusesAreOrderedFromFineToBroken() {
        assertEquals(listOf(Status.Ok, Status.Info, Status.Attention, Status.Unsafe, Status.Missing, Status.Broken), Status.entries.sorted())
    }
}
