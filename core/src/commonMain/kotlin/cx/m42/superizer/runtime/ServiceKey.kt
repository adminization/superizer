package cx.m42.superizer.runtime

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A typed name for an optional service (D19).
 *
 * The extension seam of the runtime: a host that gains a camera, biometrics or a share sheet
 * registers it under a key instead of growing a field on [AppRuntime] — which would be an
 * incompatible change to the contract and break every implementation and every fake.
 *
 * Declared next to the service interface it names:
 * ```kotlin
 * public interface CameraService { … ; public companion object { public val Key: ServiceKey<CameraService> = ServiceKey("camera") } }
 * ```
 *
 * Identity is the [name], not the instance: a key deserialized out of a manifest has to match the
 * one the host registered under.
 */
public class ServiceKey<T : Any>(public val name: String) {
    override fun toString(): String = name

    override fun equals(other: Any?): Boolean = other is ServiceKey<*> && other.name == name

    override fun hashCode(): Int = name.hashCode()
}

/**
 * A key travels as its name (D47). Reading one back gives a key that compares equal to the host's
 * but carries no type — which is all a manifest check needs, and all a manifest can honestly say.
 */
public object ServiceKeySerializer : KSerializer<ServiceKey<*>> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("cx.m42.superizer.runtime.ServiceKey", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ServiceKey<*>) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): ServiceKey<*> = ServiceKey<Any>(decoder.decodeString())
}
