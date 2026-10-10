package cx.m42.superizer.host.push

/** Through a hex string, the one thing that crosses the wasm/JS boundary without a typed-array bridge. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun randomHex(size: Int): String =
    js("Array.from(crypto.getRandomValues(new Uint8Array(size)), b => b.toString(16).padStart(2, '0')).join('')")

internal actual fun secureRandomBytes(size: Int): ByteArray {
    val hex = randomHex(size)
    return ByteArray(size) { i -> hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
}
