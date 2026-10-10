package cx.m42.superizer.host.push

import cx.m42.superizer.runtime.NetworkRequest
import cx.m42.superizer.runtime.NetworkService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One body for both calls (push-opt-in, D422). A field left null is left out of the JSON: on the
 * server an absent key keeps what it has, so a PUT that only moves `apps` cannot clear the token.
 */
@Serializable
public data class DeviceBody(
    /** SHA-256 (hex) of the `ds_…` secret. POST only: the secret itself never leaves the phone. */
    val secretHash: String? = null,
    val platform: String? = null,
    val pushToken: String? = null,
    /** The push apps this device uses (D429): the server sends nothing for any other. */
    val apps: List<String>? = null,
    val appVersion: String? = null,
    val locale: String? = null,
)

public sealed interface DeviceCall<out T> {
    public data class Ok<T>(val value: T) : DeviceCall<T>

    /** 401: the server does not know this device or this secret any more. Register again. */
    public data object Unauthorized : DeviceCall<Nothing>

    /** Anything else — no network, a 5xx, a body that did not parse. Try again later. */
    public data class Failed(val reason: String) : DeviceCall<Nothing>
}

/** The device half of the Unitool server's API (`/api/unitool/v1`). */
public interface DeviceApi {
    /** POST /devices → the new `d_…`. */
    public suspend fun register(body: DeviceBody): DeviceCall<String>

    /** PUT /devices/me, with `Bearer d_….ds_…`. */
    public suspend fun update(credential: String, body: DeviceBody): DeviceCall<Unit>
}

/** [DeviceApi] over the host's own [NetworkService], so a test's network stands in for it too. */
public class NetworkDeviceApi(
    private val network: NetworkService,
    baseUrl: String,
    private val json: Json = BODY_JSON,
) : DeviceApi {

    private val devices = baseUrl.trimEnd('/') + "/api/unitool/v1/devices"

    override suspend fun register(body: DeviceBody): DeviceCall<String> =
        call(NetworkRequest(url = devices, method = "POST", headers = emptyMap(), body = encode(body), contentType = JSON)) { text ->
            json.parseToJsonElement(text).jsonObject["deviceId"]?.jsonPrimitive?.content
        }

    override suspend fun update(credential: String, body: DeviceBody): DeviceCall<Unit> =
        call(
            NetworkRequest(
                url = "$devices/me",
                method = "PUT",
                headers = mapOf("Authorization" to "Bearer $credential"),
                body = encode(body),
                contentType = JSON,
            ),
        ) { Unit }

    private fun encode(body: DeviceBody): String = json.encodeToString(DeviceBody.serializer(), body)

    private suspend fun <T> call(request: NetworkRequest, read: (String) -> T?): DeviceCall<T> {
        val response = try {
            network.request(request)
        } catch (cause: Exception) {
            return DeviceCall.Failed("network")
        }
        return when {
            response.status == 401 -> DeviceCall.Unauthorized
            response.status !in 200..299 -> DeviceCall.Failed("http-${response.status}")
            else -> runCatching { read(response.body) }.getOrNull()?.let { DeviceCall.Ok(it) }
                ?: DeviceCall.Failed("unreadable")
        }
    }

    private companion object {
        const val JSON = "application/json"
        val BODY_JSON = Json { explicitNulls = false; encodeDefaults = false }
    }
}
