package cx.m42.superizer.host.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js

/**
 * The browser's own fetch. Which means CORS applies: a host an app talks to has to send the
 * headers, and that is a property of the *endpoint*, not something this layer can paper over —
 * see the rates source chosen in 07 for why that decided which API the converter uses.
 */
public actual fun defaultHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Js, block)
