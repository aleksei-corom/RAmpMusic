package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.Context
import it.vfsfitvnm.innertube.models.PlayerResponse
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Resuelve los streams de audio de [body.videoId] probando varios clientes de
 * InnerTube hasta encontrar una respuesta `OK` con una URL de audio.
 *
 * Estrategia:
 *
 * - Si la app aporta un PO token (BotGuard), se intenta primero WEB_REMIX con
 *   `serviceIntegrityDimensions` y se añade `&pot=` a la URL de audio. Es la
 *   combinación documentada para descargar la canción completa.
 * - Si no hay token, se usa ANDROID_VR, que según la guía de PO tokens de yt-dlp
 *   no necesita token para googlevideo. Requiere `visitorData` de sesión.
 * - IOS queda como fallback (YouTube exige PO token de GVS para iOS y suele
 *   cortar la descarga tras el primer trozo).
 */
suspend fun Innertube.player(body: PlayerBody) = runCatchingNonCancellable {
    val visitorData = Innertube.ensureVisitorData()
    val canDecipher = Innertube.cipherDecipherer != null

    // El token de streaming (GVS) va ligado al visitorData y se añade como &pot= a la URL de audio.
    val streamingPot = try {
        visitorData?.let { Innertube.streamingPotProvider?.invoke(it) }
    } catch (throwable: Throwable) {
        null
    }

    // playerPot (ligado al videoId) solo se usa para WEB_REMIX en serviceIntegrityDimensions.
    val playerPot = if (canDecipher) {
        try {
            Innertube.mediaPotProvider?.invoke(body.videoId)
        } catch (throwable: Throwable) {
            null
        }
    } else {
        null
    }

    val urlPot = streamingPot ?: playerPot


    // Solo hace falta para el cliente web; el JS del reproductor pesa ~3 MB.
    val signatureTimestamp = if (canDecipher) {
        (try {
            Innertube.signatureTimestampProvider?.invoke(body.videoId)
        } catch (throwable: Throwable) {
            null
        }) ?: Innertube.ensureSignatureTimestamp()
    } else {
        null
    }

    val baseContexts = buildList {
        // VISIONOS entrega URLs directas de alta calidad (Opus 251 / AAC 140) y googlevideo
        // acepta peticiones Range arbitrarias (sin el corte 403 del cliente iOS).
        add(Context.DefaultVisionOs)
        if (canDecipher) add(Context.DefaultWebWithLocale)
        add(Context.DefaultIos)
        add(Context.DefaultAndroidVr)
        if (!canDecipher) add(Context.DefaultWebWithLocale)
        add(Context.DefaultAndroid)
        add(Context.DefaultTv)
        add(
            Context.DefaultWeb.copy(
                thirdParty = Context.ThirdParty(
                    embedUrl = "https://www.youtube.com/watch?v=${body.videoId}"
                )
            )
        )
    }

    val contexts = baseContexts
        .map { context ->
            if (visitorData == null) {
                context
            } else {
                context.copy(client = context.client.copy(visitorData = visitorData))
            }
        }
        .distinctBy { context -> context.client.clientName + "|" + context.thirdParty?.embedUrl }

    var firstResponse: PlayerResponse? = null
    var firstPlayableResponse: PlayerResponse? = null
    var lastError: Throwable? = null

    for (context in contexts) {
        val requestBody = if (context.client.clientName == "WEB_REMIX") {
            var webBody = body.copy(context = context)

            if (signatureTimestamp != null) {
                webBody = webBody.copy(
                    playbackContext = PlayerBody.PlaybackContext(
                        PlayerBody.ContentPlaybackContext(signatureTimestamp)
                    )
                )
            }

            if (!playerPot.isNullOrBlank()) {
                webBody = webBody.copy(
                    serviceIntegrityDimensions = PlayerBody.ServiceIntegrityDimensions(playerPot)
                )
            }

            webBody
        } else {
            body.copy(context = context)
        }

        val response = try {
            client.post(player) {
                setBody(requestBody)
                visitorData?.let { header("X-Goog-Visitor-Id", it) }
                mask("playabilityStatus,playerConfig.audioConfig,streamingData.adaptiveFormats,videoDetails.videoId")
            }.body<PlayerResponse>()
        } catch (throwable: Throwable) {
            lastError = throwable
            continue
        }

        if (firstResponse == null) firstResponse = response

        val hasPlayableFormat = response.streamingData
            ?.adaptiveFormats
            ?.any { format -> format.isPlayable || (canDecipher && format.signatureCipher != null) } == true

        if (response.playabilityStatus?.status == "OK" && hasPlayableFormat) {
            val responseWithUrls = if (canDecipher) {
                response.withDecipheredUrls(body.videoId)
            } else {
                response
            }

            val hasAudioUrl = responseWithUrls.streamingData
                ?.adaptiveFormats
                ?.any { format ->
                    format.mimeType.startsWith("audio/") && !format.url.isNullOrBlank()
                } == true

            if (!hasAudioUrl) {
                // El cliente devolvió URL(s) pero no se pudo deofuscar ninguna firma.
                continue
            }

            val usePot = !urlPot.isNullOrBlank()

            val responseWithPot = if (usePot) {
                responseWithUrls.withPot(urlPot!!)
            } else {
                responseWithUrls
            }

            if (firstPlayableResponse == null) {
                firstPlayableResponse = responseWithPot.copy(mediaHeaders = mediaHeadersOf(context))
                Innertube.lastPlayerClient = context.client.clientName
                Innertube.lastPlayerUsedPot = usePot
            }

            // Comprobamos el segundo trozo (512 KB) antes de dar el cliente por
            // bueno: sin PO token válido, googlevideo devuelve 403 y el error
            // aparecía a los ~30 s de reproducción.
            val headers = mediaHeadersOf(context)
            if (!isSecondChunkPlayable(responseWithPot, headers)) {
                continue
            }

            Innertube.lastPlayerClient = context.client.clientName
            Innertube.lastPlayerUsedPot = usePot

            return@runCatchingNonCancellable responseWithPot.copy(mediaHeaders = headers)
        }
    }

    // Ningún cliente superó la comprobación, pero devolvemos el primero que sí
    // tenía formatos para que el llamador muestre el error real.
    firstPlayableResponse?.let { direct ->
        // Último recurso: instancias Piped, que proxyfican el stream y no
        // dependen de los tokens/limitaciones de googlevideo en el cliente.
        pipedPlayer(body.videoId)?.let { return@runCatchingNonCancellable it }
        return@runCatchingNonCancellable direct
    }

    firstResponse ?: throw (lastError ?: IllegalStateException("Player request failed for every client"))
}

private val PIPED_INSTANCES = listOf(
    "https://api.piped.private.coffee",
    "https://pipedapi.tokhmi.xyz",
    "https://pipedapi.drgns.space",
    "https://pipedapi.smnz.de",
    "https://pa.il.ax"
)

private data class PipedResult(
    val response: PlayerResponse,
    val instance: String
)

/**
 * Pide a varias instancias Piped (en paralelo) una URL de audio ya proxyficada.
 * Se usa solo si ninguna URL directa de YouTube pasa la comprobación del segundo
 * trozo.
 */
private suspend fun Innertube.pipedPlayer(videoId: String): PlayerResponse? {
    val innertube = this

    return kotlinx.coroutines.coroutineScope {
        val results = PIPED_INSTANCES
            .map { instance ->
                async {
                    innertube.fetchPiped(instance, videoId)?.let { response ->
                        PipedResult(response, instance)
                    }
                }
            }
            .awaitAll()

        val best: PipedResult = results.filterNotNull().firstOrNull()
            ?: return@coroutineScope null

        innertube.lastPlayerClient = "PIPED:${best.instance.substringAfter("//")}"
        innertube.lastPlayerUsedPot = false
        best.response
    }
}

private suspend fun Innertube.fetchPiped(
    instance: String,
    videoId: String
): PlayerResponse? = kotlinx.coroutines.withTimeoutOrNull(4_000) {
    val body = runCatching {
        client.get("$instance/streams/$videoId") {
            header("Accept", "application/json")
        }.bodyAsText()
    }.getOrNull() ?: return@withTimeoutOrNull null

    val root = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject
    }.getOrNull() ?: return@withTimeoutOrNull null

    val audioStreams = runCatching { root["audioStreams"]?.jsonArray }.getOrNull()
        ?: return@withTimeoutOrNull null

    val formats = audioStreams.mapNotNull { element ->
        val stream = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
        val url = stream["url"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.startsWith("https://") }
            ?: return@mapNotNull null
        val mimeType = stream["mimeType"]?.jsonPrimitive?.contentOrNull ?: "audio/mp4"
        val itag = stream["itag"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val bitrate = stream["bitrate"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        val contentLength = stream["contentLength"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

        PlayerResponse.StreamingData.AdaptiveFormat(
            itag = itag,
            mimeType = mimeType,
            bitrate = bitrate,
            averageBitrate = null,
            contentLength = contentLength,
            audioQuality = null,
            approxDurationMs = null,
            lastModified = null,
            loudnessDb = null,
            audioSampleRate = null,
            url = url
        )
    }

    if (formats.isEmpty()) {
        null
    } else {
        PlayerResponse(
            playabilityStatus = PlayerResponse.PlayabilityStatus(status = "OK"),
            playerConfig = null,
            streamingData = PlayerResponse.StreamingData(adaptiveFormats = formats),
            videoDetails = PlayerResponse.VideoDetails(videoId = videoId)
        )
    }
}

/**
 * Comprueba con una petición Range si el servidor entrega el segundo trozo del
 * audio. Un 403 aquí significa que el PO token no es válido para ese cliente.
 */
private suspend fun isSecondChunkPlayable(
    response: PlayerResponse,
    headers: Map<String, String>
): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val url = response.streamingData
        ?.adaptiveFormats
        ?.filter { it.mimeType.startsWith("audio/") && !it.url.isNullOrBlank() }
        ?.maxByOrNull { it.bitrate ?: 0L }
        ?.url
        ?: return@withContext false

    runCatching {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        connection.setRequestProperty("Range", "bytes=524288-1048575")

        val code = connection.responseCode
        connection.disconnect()

        // 416 = el archivo es más corto que 512 KB, no es un fallo.
        code == 200 || code == 206 || code == 416
    }.getOrDefault(false)
}

/**
 * Cabeceras que googlevideo exige al descargar el stream. Deben ser coherentes
 * con el cliente con el que se resolvió la URL; si no, la petición puede recibir
 * un 403 tras el primer rango servido.
 */
private fun mediaHeadersOf(context: Context): Map<String, String> {
    val client = context.client
    return buildMap {
        client.userAgent?.let { put("User-Agent", it) }

        when (client.clientName) {
            "WEB_REMIX" -> {
                put("Referer", "https://music.youtube.com/")
                put("Origin", "https://music.youtube.com")
            }
            "WEB", "MWEB" -> {
                put("Referer", "https://www.youtube.com/")
                put("Origin", "https://www.youtube.com")
            }
        }
    }
}

/**
 * Añade el PO token a las URLs de audio. Sin este parámetro googlevideo corta la
 * descarga tras el primer trozo (403), que es lo que producía el error a los ~30 s.
 */
private fun PlayerResponse.withPot(poToken: String): PlayerResponse {
    val encodedPot = java.net.URLEncoder.encode(poToken, "UTF-8")
    val data = streamingData ?: return this

    val formats = data.adaptiveFormats?.map { format ->
        val url = format.url ?: return@map format
        if (url.contains("pot=")) {
            format
        } else {
            format.copy(
                url = url + if (url.contains("?")) "&pot=$encodedPot" else "?pot=$encodedPot"
            )
        }
    }

    return copy(streamingData = data.copy(adaptiveFormats = formats))
}

/**
 * Convierte los `signatureCipher` del cliente web en URLs directas, deofuscando
 * la firma con el player JS (Rhino/NewPipeExtractor).
 */
private fun PlayerResponse.withDecipheredUrls(videoId: String): PlayerResponse {
    val decipher = Innertube.cipherDecipherer ?: return this
    val data = streamingData ?: return this

    val formats = data.adaptiveFormats?.map { format ->
        val cipher = format.signatureCipher

        if (!format.url.isNullOrBlank() || cipher.isNullOrBlank()) {
            return@map format
        }

        // Solo se reproducen/descargan formatos de audio.
        if (!format.mimeType.startsWith("audio/")) {
            return@map format
        }

        runCatching {
            val params = io.ktor.http.parseQueryString(cipher)
            val obfuscatedSignature = params["s"] ?: return@runCatching null
            val signatureParameter = params["sp"] ?: "signature"
            val baseUrl = params["url"] ?: return@runCatching null
            val signature = decipher(videoId, obfuscatedSignature) ?: return@runCatching null
            val encodedSignature = java.net.URLEncoder.encode(signature, "UTF-8")

            format.copy(
                url = baseUrl +
                    (if (baseUrl.contains("?")) "&" else "?") +
                    signatureParameter + "=" + encodedSignature
            )
        }.getOrNull() ?: format
    }

    return copy(streamingData = data.copy(adaptiveFormats = formats))
}
