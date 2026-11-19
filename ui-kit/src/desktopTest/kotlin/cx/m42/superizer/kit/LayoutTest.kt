package cx.m42.superizer.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LayoutTest {

    @Test
    fun pageUsesTheGutterOfItsWindowClassAndCapsItsWidth() = runComposeUiTest {
        var cls by androidx.compose.runtime.mutableStateOf(WindowClass.Compact)
        setContent {
            KitTheme {
                CompositionLocalProvider(LocalWindowClass provides cls) {
                    Box(Modifier.requiredWidth(1600.dp).testTag("frame")) {
                        Page(PageWidth.Narrow, scroll = false) { Box(Modifier.fillMaxWidth().height(10.dp).testTag("body")) }
                    }
                }
            }
        }
        // Measured from the frame: the test window may be narrower than it and centre it.
        val frame = onNodeWithTag("frame").getUnclippedBoundsInRoot().left
        val compact = onNodeWithTag("body").getUnclippedBoundsInRoot()
        assertEquals(16.dp, compact.left - frame, "the Compact gutter")
        assertEquals(1600.dp - 32.dp, compact.right - compact.left, "Compact: the window, less the gutters")

        cls = WindowClass.Expanded
        waitForIdle()
        val wide = onNodeWithTag("body").getUnclippedBoundsInRoot()
        assertEquals(PageWidth.Narrow.max, wide.right - wide.left, "Expanded: capped at the page's width")
        assertEquals((1600.dp - PageWidth.Narrow.max) / 2, wide.left - frame, "and centred")
    }

    @Test
    fun crumbsAreDrawnWhereThereIsNoBarOverThePage() = runComposeUiTest {
        var cls by androidx.compose.runtime.mutableStateOf(WindowClass.Compact)
        setContent {
            KitTheme {
                CompositionLocalProvider(LocalWindowClass provides cls) {
                    PageHeader("Пицца Север", crumbs = listOf(Crumb("Сервисы") {}, Crumb("Пицца Север", null)))
                }
            }
        }
        onNodeWithText("Сервисы").assertDoesNotExist()
        cls = WindowClass.Medium
        waitForIdle()
        onNodeWithText("Сервисы").assertExists()
        cls = WindowClass.Expanded
        waitForIdle()
        onNodeWithText("Сервисы").assertExists()
    }

    @Test
    fun aTableRowOpensWithItsRecord() = runComposeUiTest {
        var opened: String? = null
        setContent {
            KitTheme {
                DataTable(
                    rows = listOf("№ 1321", "№ 1322"),
                    columns = listOf(TableColumn("Заказ") { Text(it) }, TableColumn("Сумма", alignEnd = true) { Text("910 ₽") }),
                    onRow = { opened = it },
                )
            }
        }
        onNodeWithText("Заказ").assertExists()
        onNodeWithText("№ 1322").performClick()
        assertEquals("№ 1322", opened)
    }

    @Test
    fun chipsAndLineTabsReportTheChoice() = runComposeUiTest {
        var chip = -1
        var tab = 0
        setContent {
            KitTheme {
                androidx.compose.foundation.layout.Column {
                    Chip("Все", selected = true, onClick = { chip = 0 }, count = 9)
                    Chip("Ошибка", selected = false, onClick = { chip = 1 }, dot = Tone.Danger)
                    LineTabs(listOf("Обзор", "События", "Логи"), selected = tab, onSelect = { tab = it })
                }
            }
        }
        onNodeWithText("Ошибка").performClick()
        assertEquals(1, chip)
        onNodeWithText("Логи").performClick()
        assertEquals(2, tab)
    }

    @Test
    fun qtyStepperStaysWithinItsBounds() = runComposeUiTest {
        var value by mutableIntStateOf(1)
        var removed = false
        setContent {
            KitTheme {
                QtyStepper(value, onChange = { if (it < 1) removed = true else value = it }, min = 1, max = 2, removeLabel = "Убрать")
            }
        }
        onNodeWithContentDescription("+").performClick()
        assertEquals(2, value)
        onNodeWithContentDescription("+").assertIsNotEnabled()
        onNodeWithContentDescription("−").performClick()
        assertEquals(1, value)
        // At the minimum the minus turns into «remove the line».
        onNodeWithContentDescription("Убрать").performClick()
        assertTrue(removed)
        assertEquals(1, value)
    }

    @Test
    fun gridLaysFourCellsInTwoRowsOfEqualHeight() = runComposeUiTest {
        setContent {
            KitTheme {
                Box(Modifier.requiredWidth(416.dp).testTag("grid")) {
                    Grid(columns = 2, gap = 16.dp) {
                        Box(Modifier.height(40.dp).testTag("a"))
                        Box(Modifier.height(80.dp).testTag("b"))
                        Box(Modifier.height(20.dp).testTag("c"))
                        Box(Modifier.height(20.dp).testTag("d"))
                    }
                }
            }
        }
        val a = onNodeWithTag("a").getUnclippedBoundsInRoot()
        val b = onNodeWithTag("b").getUnclippedBoundsInRoot()
        val c = onNodeWithTag("c").getUnclippedBoundsInRoot()
        assertEquals(200.dp, a.right - a.left, "two equal columns")
        assertEquals(a.top, b.top, "a and b share a row")
        assertEquals(80.dp, a.bottom - a.top, "and its height")
        assertEquals(a.left, c.left, "c starts the second row")
        assertEquals(a.bottom + 16.dp, c.top)
    }
}
