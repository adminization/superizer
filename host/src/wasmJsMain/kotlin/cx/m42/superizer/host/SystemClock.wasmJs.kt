@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cx.m42.superizer.host

/**
 * `Date.now()` through JS interop rather than `kotlin.js.Date`, which the wasm target does not
 * have. Milliseconds since the epoch, as a Double, because that is what the platform returns.
 */
public actual object SystemClock : cx.m42.superizer.runtime.Clock {
    actual override fun now(): Long = dateNow().toLong()
}

private fun dateNow(): Double = js("Date.now()")
