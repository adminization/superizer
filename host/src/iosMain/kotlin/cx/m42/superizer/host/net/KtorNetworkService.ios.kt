package cx.m42.superizer.host.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin

/** NSURLSession underneath: the system proxy, App Transport Security and a per-app VPN all apply. */
public actual fun defaultHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Darwin, block)
