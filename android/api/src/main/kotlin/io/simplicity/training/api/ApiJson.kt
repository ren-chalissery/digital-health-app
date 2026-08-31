package io.simplicity.training.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.util.UUID

/**
 * The one JSON configuration every caller must use.
 *
 * The generated models annotate every identifier `@Contextual java.util.UUID`, and
 * kotlinx.serialization has no built-in serializer for `UUID`. Without the module below, **thirty
 * of the generated models fail to deserialise at runtime** with "Serializer for class 'UUID' is
 * not found" — and only at runtime, because the annotation compiles perfectly well.
 *
 * That failure is worse than it sounds: models with no identifier, such as `PlaybackResponse`,
 * work fine, so the client appears to function until it reaches anything with an id.
 */
object ApiJson {

    val instance: Json = Json {
        // The server adds fields before clients are rebuilt, and an unknown one should not fail
        // a response the app otherwise understands.
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule = SerializersModule {
            contextual(UuidSerializer)
        }
    }
}

private object UuidSerializer : KSerializer<UUID> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.util.UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}
