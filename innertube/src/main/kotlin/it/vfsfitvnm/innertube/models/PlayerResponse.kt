package it.vfsfitvnm.innertube.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus?,
    val playerConfig: PlayerConfig?,
    val streamingData: StreamingData?,
    val videoDetails: VideoDetails?,
    // No viene del JSON: lo rellena Innertube.player con los headers que exige el
    // cliente con el que se resolvió la URL de audio (User-Agent/Referer/Origin).
    val mediaHeaders: Map<String, String> = emptyMap(),
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String?,
        val reason: String? = null
    )

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig?
    ) {
        @Serializable
        data class AudioConfig(
            private val loudnessDb: Double? = null,
            // Los clientes móviles (iOS) no mandan loudnessDb sino perceptualLoudnessDb.
            private val perceptualLoudnessDb: Double? = null,
        ) {
            // For music clients only
            val normalizedLoudnessDb: Float?
                get() = (loudnessDb ?: perceptualLoudnessDb)?.plus(7)?.toFloat()
        }
    }

    @Serializable
    data class StreamingData(
        val adaptiveFormats: List<AdaptiveFormat>?
    ) {
        val autoMaxQualityFormat: AdaptiveFormat?
            get() = adaptiveFormats?.sortedByDescending { it.itag }?.firstOrNull {
                it.itag == 251 || it.itag == 141 ||
                it.itag == 250 || it.itag == 140 ||
                it.itag == 249 || it.itag == 139
            }

        val highestQualityFormat: AdaptiveFormat?
            get() = adaptiveFormats?.findLast { it.itag == 251 || it.itag == 141 }
            //get() = adaptiveFormats?.findLast { it.itag == 251 || it.itag == 140 }

        val mediumQualityFormat: AdaptiveFormat?
            get() = adaptiveFormats?.findLast { it.itag == 250 || it.itag == 140 }

        val lowestQualityFormat: AdaptiveFormat?
            get() = adaptiveFormats?.findLast { it.itag == 249 || it.itag == 139 }

        /**
         * YouTube dejó de mandar los números como números: `contentLength`,
         * `approxDurationMs`, `lastModified` y `audioSampleRate` llegan como string
         * (p. ej. "5216923"). El deserializador por defecto lanzaba
         * SerializationException y la reproducción fallaba aunque el cliente
         * devolviese una URL válida. Este serializador acepta ambos formatos.
         */
        @Serializable(with = AdaptiveFormatSerializer::class)
        data class AdaptiveFormat(
            val itag: Int,
            val mimeType: String,
            val bitrate: Long?,
            val averageBitrate: Long?,
            val contentLength: Long?,
            val audioQuality: String?,
            val approxDurationMs: Long?,
            val lastModified: Long?,
            val loudnessDb: Double?,
            val audioSampleRate: Int?,
            val url: String?,
            val signatureCipher: String? = null,
        ) {
            val isPlayable: Boolean
                get() = !url.isNullOrBlank()
        }
    }

    @Serializable
    data class VideoDetails(
        val videoId: String?
    )
}

internal object AdaptiveFormatSerializer : KSerializer<PlayerResponse.StreamingData.AdaptiveFormat> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("AdaptiveFormat")

    override fun deserialize(decoder: Decoder): PlayerResponse.StreamingData.AdaptiveFormat {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("AdaptiveFormat solo se puede deserializar desde JSON")
        val obj = jsonDecoder.decodeJsonElement() as? JsonObject
            ?: throw SerializationException("Se esperaba un objeto JSON para AdaptiveFormat")

        fun raw(key: String): String? {
            val value = obj[key] as? JsonPrimitive ?: return null
            if (value is JsonNull) return null
            return value.content.takeIf { it.isNotBlank() }
        }

        return PlayerResponse.StreamingData.AdaptiveFormat(
            itag = raw("itag")?.toIntOrNull() ?: 0,
            mimeType = raw("mimeType").orEmpty(),
            bitrate = raw("bitrate")?.toLongOrNull(),
            averageBitrate = raw("averageBitrate")?.toLongOrNull(),
            contentLength = raw("contentLength")?.toLongOrNull(),
            audioQuality = raw("audioQuality"),
            approxDurationMs = raw("approxDurationMs")?.toLongOrNull(),
            lastModified = raw("lastModified")?.toLongOrNull(),
            loudnessDb = raw("loudnessDb")?.toDoubleOrNull(),
            audioSampleRate = raw("audioSampleRate")?.toIntOrNull(),
            url = raw("url"),
            signatureCipher = raw("signatureCipher"),
        )
    }

    override fun serialize(encoder: Encoder, value: PlayerResponse.StreamingData.AdaptiveFormat) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("AdaptiveFormat solo se puede serializar a JSON")
        jsonEncoder.encodeJsonElement(buildJsonObject {
            put("itag", value.itag)
            put("mimeType", value.mimeType)
            value.bitrate?.let { put("bitrate", it) }
            value.averageBitrate?.let { put("averageBitrate", it) }
            value.contentLength?.let { put("contentLength", it) }
            value.audioQuality?.let { put("audioQuality", it) }
            value.approxDurationMs?.let { put("approxDurationMs", it) }
            value.lastModified?.let { put("lastModified", it) }
            value.loudnessDb?.let { put("loudnessDb", it) }
            value.audioSampleRate?.let { put("audioSampleRate", it) }
            value.url?.let { put("url", it) }
            value.signatureCipher?.let { put("signatureCipher", it) }
        })
    }
}
