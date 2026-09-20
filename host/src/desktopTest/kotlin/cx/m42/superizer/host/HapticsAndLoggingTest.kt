package cx.m42.superizer.host

import cx.m42.superizer.app.AppId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

/** The two services that exist to keep a decision out of every call site (D33, D38). */
class HapticsAndLoggingTest {

    @Test
    fun theSwitchIsCheckedOnceHereRatherThanAtEveryCallSite() {
        // D38: an app calls `haptics.tick()` and never learns the host has a setting.
        val enabled = MutableStateFlow(false)
        val haptics = PlatformHaptics(enabled)
        haptics.tick()
        enabled.value = true
        haptics.tick()
        // Desktop has no motor, so the observable effect is that neither call threw. What is being
        // pinned is that the app did not have to ask.
    }

    @Test
    fun theRingBufferKeepsTheLastLinesAndDropsTheRest() {
        val buffer = LogBuffer(capacity = 3)
        repeat(5) { buffer.add("line $it") }
        assertEquals(listOf("line 2", "line 3", "line 4"), buffer.lines.value)
    }

    @Test
    fun everyLevelReachesTheBufferEvenWhenTheConsoleOnlySeesSome() {
        // D33: the line you want is always the one that was not important enough to print.
        val buffer = LogBuffer()
        val logger = ConsoleLogger("app:chat", buffer, verbose = false)
        logger.debug("d")
        logger.info("i")
        logger.warn("w")
        logger.error("e")
        assertEquals(4, buffer.lines.value.size)
        assertTrue(buffer.lines.value.all { "app:chat" in it })
    }

    @Test
    fun analyticsNamesArePrefixedWithTheAppTheyCameFrom() {
        // Two apps both reporting `error` are two apps nobody can tell apart.
        val buffer = LogBuffer()
        val logger = ConsoleLogger("app:chat", buffer, verbose = true)
        LoggingAnalytics(AppId("chat"), logger).event("error")
        assertTrue(buffer.lines.value.single().contains("chat.error"))
    }
}
