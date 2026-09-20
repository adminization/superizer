@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cx.m42.superizer.fixture

import cx.m42.superizer.Superizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * How a browser test sees a canvas.
 *
 * Compose draws into a canvas, so Playwright finds no text and no nodes — there is nothing in the
 * DOM to query, and a smoke test that only checked "the page loaded" would pass on a white screen.
 * The answer is a read-only bridge the page publishes as `window.__superizer`, and only when it is
 * opened with `?test=1`: on a normal load the object is never created.
 *
 * It answers three questions, which between them catch the three classes of wasm regression the
 * JVM cannot see: did the build start at all (the object exists), is anything being drawn (the
 * screenshot is not blank), and did a route reach the handler (`currentApp`).
 *
 * State is *pushed* into plain JS values rather than read back through callbacks, because a
 * suspending read from JS is not a thing, and a stale value would be worse than none.
 */
internal fun installTestBridge(superizer: Superizer) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val events = mutableListOf<String>()

    createBridge(superizer.registry.all().size)

    superizer.events
        .onEach { event ->
            events += event::class.simpleName.orEmpty()
            setEvents(events.joinToString(",", "[", "]") { "\"$it\"" })
        }
        .launchIn(scope)

    scope.launch {
        superizer.handler.current.collect { session ->
            setCurrentApp(session?.app?.id?.value ?: "")
        }
    }
}

private fun createBridge(appCount: Int): Unit = js(
    """{
        window.__superizerState = { currentApp: "", events: [], appCount: appCount };
        window.__superizer = {
            currentApp: function () { return window.__superizerState.currentApp; },
            events: function () { return window.__superizerState.events; },
            appCount: function () { return window.__superizerState.appCount; },
            destination: function () {
                return window.__superizerState.currentApp === "" ? "Home" : "App";
            }
        };
    }""",
)

private fun setCurrentApp(value: String): Unit = js("{ window.__superizerState.currentApp = value; }")

private fun setEvents(json: String): Unit = js("{ window.__superizerState.events = JSON.parse(json); }")
