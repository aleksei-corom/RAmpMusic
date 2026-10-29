package it.vfsfitvnm.innertube

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.compression.brotli
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import it.vfsfitvnm.innertube.models.MusicNavigationButtonRenderer
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.models.Runs
import it.vfsfitvnm.innertube.models.Thumbnail
import it.vfsfitvnm.innertube.models.YouTubeClient
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.utils.ProxyPreferences
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLDecoder
import java.util.Locale

object Innertube {
    val client = HttpClient(OkHttp) {
        BrowserUserAgent()

        expectSuccess = true

        install(ContentNegotiation) {
            @OptIn(ExperimentalSerializationApi::class)
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            })
        }

        install(ContentEncoding) {
            brotli()
        }

        ProxyPreferences.preference?.let {
            engine {
                proxy = Proxy(
                    it.proxyMode,
                    InetSocketAddress(
                        it.proxyHost,
                        it.proxyPort
                    )
                )
            }
        }

        defaultRequest {
            url(scheme = "https", host ="music.youtube.com") {
                headers.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                headers.append("X-Goog-Api-Key", "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8")
                parameters.append("prettyPrint", "false")
            }
        }
    }

    var proxy: Proxy? = null
        set(value) {
            field = value
            client.close()
            client
        }

    /**
     * `visitorData` de la sesión. Algunos clientes (p. ej. ANDROID_VR) lo necesitan
     * para no responder LOGIN_REQUIRED. Se obtiene una vez de youtube.com y se
     * reutiliza durante la sesión.
     */
    @Volatile
    var visitorData: String? = null
        private set

    /**
     * Genera un PO token (BotGuard) para un videoId. Lo aporta la app porque el
     * módulo innertube es JVM puro. Si es null, se reproduce sin `pot`.
     */
    @Volatile
    var mediaPotProvider: (suspend (String) -> String?)? = null

    /**
     * Genera el PO token de streaming (GVS) ligado al `visitorData` de la sesión.
     * Es el que se añade a la URL de audio como `&pot=`.
     */
    @Volatile
    var streamingPotProvider: (suspend (String) -> String?)? = null

    /**
     * Deofusca la firma (`s`) de un `signatureCipher` para un videoId. Lo aporta la
     * app (Rhino/NewPipeExtractor); el módulo innertube no puede ejecutar JS.
     */
    @Volatile
    var cipherDecipherer: ((String, String) -> String?)? = null

    /**
     * Devuelve el `signatureTimestamp` del player JS. Lo aporta la app y se cachea.
     */
    @Volatile
    var signatureTimestampProvider: (suspend (String) -> Int?)? = null

    /**
     * Último cliente con el que se resolvió una reproducción y si se pudo añadir
     * el PO token. Se usa para el diagnóstico que se muestra en el error.
     */
    @Volatile
    var lastPlayerClient: String? = null

    @Volatile
    var lastPlayerUsedPot: Boolean = false

    @Volatile
    private var cachedSignatureTimestamp: Int? = null

    @Volatile
    private var signatureTimestampFetched: Boolean = false

    /**
     * YouTube exige `playbackContext.contentPlaybackContext.signatureTimestamp` en
     * los clientes web (`WEB_REMIX`). Sin él responde `UNPLAYABLE / Video
     * unavailable` aunque el resto de la petición sea correcta. Se extrae del
     * player JS, que cambia con cada actualización de YouTube.
     */
    suspend fun ensureSignatureTimestamp(): Int? {
        cachedSignatureTimestamp?.let { return it }
        if (signatureTimestampFetched) return null

        return runCatching {
            val iframeApi = client.get("https://www.youtube.com/iframe_api") {
                header("Accept-Language", "en-US,en;q=0.9")
            }.bodyAsText()

            val playerVersion = Regex("/s/player/([0-9a-f]+)/")
                .find(iframeApi.replace("\\/", "/"))
                ?.groupValues
                ?.getOrNull(1)
                ?: return@runCatching null

            val playerJs = client.get(
                "https://www.youtube.com/s/player/$playerVersion/player_ias.vflset/en_US/base.js"
            ).bodyAsText()

            Regex("signatureTimestamp[=:](\\d+)")
                .find(playerJs)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }.getOrNull().also {
            cachedSignatureTimestamp = it
            if (it != null) signatureTimestampFetched = true
        }
    }

    suspend fun ensureVisitorData(): String? {
        visitorData?.let { return it }

        // 1) La propia API de browse devuelve el visitorData de la sesión.
        runCatching {
            val response = client.post(browse) {
                setBody(BrowseBody(browseId = "FEmusic_home"))
                mask("responseContext.visitorData")
            }.body<BrowseResponse>()

            response.responseContext?.visitorData
                ?.let { URLDecoder.decode(it, "UTF-8") }
                ?.takeIf { it.isNotBlank() }
                ?.also { visitorData = it }
        }

        visitorData?.let { return it }

        // 2) Fallback: la página de YouTube Music incluye ytcfg con VISITOR_DATA.
        return runCatching {
            val html = client.get("https://music.youtube.com/") {
                header("Accept-Language", "en-US,en;q=0.9")
            }.bodyAsText()

            Regex("\"VISITOR_DATA\":\"([^\"]+)\"")
                .find(html)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { URLDecoder.decode(it, "UTF-8") }
                ?.takeIf { it.isNotBlank() }
                ?.also { visitorData = it }
        }.getOrNull()
    }


    //var localeHl =  "en"
    //var localeHl =  Locale.getDefault().toLanguageTag() //"en"

    internal const val browse = "/youtubei/v1/browse"
    internal const val next = "/youtubei/v1/next"
    internal const val player = "/youtubei/v1/player"
    internal const val queue = "/youtubei/v1/music/get_queue"
    internal const val search = "/youtubei/v1/search"
    internal const val searchSuggestions = "/youtubei/v1/music/get_search_suggestions"

    internal const val musicResponsiveListItemRendererMask = "musicResponsiveListItemRenderer(flexColumns,fixedColumns,thumbnail,navigationEndpoint)"
    internal const val musicTwoRowItemRendererMask = "musicTwoRowItemRenderer(thumbnailRenderer,title,subtitle,navigationEndpoint)"
    const val playlistPanelVideoRendererMask = "playlistPanelVideoRenderer(title,navigationEndpoint,longBylineText,shortBylineText,thumbnail,lengthText)"

    internal fun HttpRequestBuilder.mask(value: String = "*") =
        header("X-Goog-FieldMask", value)



    data class Info<T : NavigationEndpoint.Endpoint>(
        val name: String?,
        val endpoint: T?
    ) {
        @Suppress("UNCHECKED_CAST")
        constructor(run: Runs.Run) : this(
            name = run.text,
            endpoint = run.navigationEndpoint?.endpoint as T?
        )
    }

    @JvmInline
    value class SearchFilter(val value: String) {
        companion object {
            val Song = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            val Video = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
            val Album = SearchFilter("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D")
            val Artist = SearchFilter("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
            val CommunityPlaylist = SearchFilter("EgeKAQQoAEABagoQAxAEEAoQCRAF")
            val FeaturedPlaylist = SearchFilter("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D")
        }
    }

    sealed class Item {
        abstract val thumbnail: Thumbnail?
        abstract val key: String
    }

    data class SongItem(
        val info: Info<NavigationEndpoint.Endpoint.Watch>?,
        val authors: List<Info<NavigationEndpoint.Endpoint.Browse>>?,
        val album: Info<NavigationEndpoint.Endpoint.Browse>?,
        val durationText: String?,
        override val thumbnail: Thumbnail?,
        val explicit: Boolean = false
    ) : Item() {
        override val key get() = info!!.endpoint!!.videoId!!

        companion object
    }

    data class VideoItem(
        val info: Info<NavigationEndpoint.Endpoint.Watch>?,
        val authors: List<Info<NavigationEndpoint.Endpoint.Browse>>?,
        val viewsText: String?,
        val durationText: String?,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.videoId!!

        val isOfficialMusicVideo: Boolean
            get() = info
                ?.endpoint
                ?.watchEndpointMusicSupportedConfigs
                ?.watchEndpointMusicConfig
                ?.musicVideoType == "MUSIC_VIDEO_TYPE_OMV"

        val isUserGeneratedContent: Boolean
            get() = info
                ?.endpoint
                ?.watchEndpointMusicSupportedConfigs
                ?.watchEndpointMusicConfig
                ?.musicVideoType == "MUSIC_VIDEO_TYPE_UGC"

        companion object
    }

    data class AlbumItem(
        val info: Info<NavigationEndpoint.Endpoint.Browse>?,
        val authors: List<Info<NavigationEndpoint.Endpoint.Browse>>?,
        val year: String?,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.browseId!!

        companion object
    }

    data class ArtistItem(
        val info: Info<NavigationEndpoint.Endpoint.Browse>?,
        val subscribersCountText: String?,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.browseId!!

        companion object
    }

    data class PlaylistItem(
        val info: Info<NavigationEndpoint.Endpoint.Browse>?,
        val channel: Info<NavigationEndpoint.Endpoint.Browse>?,
        val songCount: Int?,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.browseId!!

        companion object
    }

    data class ArtistPage(
        val name: String?,
        val description: String?,
        val subscriberCountText: String?,
        val thumbnail: Thumbnail?,
        val shuffleEndpoint: NavigationEndpoint.Endpoint.Watch?,
        val radioEndpoint: NavigationEndpoint.Endpoint.Watch?,
        val songs: List<SongItem>?,
        val songsEndpoint: NavigationEndpoint.Endpoint.Browse?,
        val albums: List<AlbumItem>?,
        val albumsEndpoint: NavigationEndpoint.Endpoint.Browse?,
        val singles: List<AlbumItem>?,
        val singlesEndpoint: NavigationEndpoint.Endpoint.Browse?,
    )

    data class PlaylistOrAlbumPage(
        val title: String?,
        val authors: List<Info<NavigationEndpoint.Endpoint.Browse>>?,
        val year: String?,
        val thumbnail: Thumbnail?,
        val url: String?,
        val songsPage: ItemsPage<SongItem>?,
        val otherVersions: List<AlbumItem>?
    )

    data class NextPage(
        val itemsPage: ItemsPage<SongItem>?,
        val playlistId: String?,
        val params: String? = null,
        val playlistSetVideoId: String? = null
    )

    data class RelatedPage(
        val songs: List<SongItem>? = null,
        val playlists: List<PlaylistItem>? = null,
        val albums: List<AlbumItem>? = null,
        val artists: List<ArtistItem>? = null,
    )
    data class RelatedSongs(
        val songs: List<SongItem>? = null
    )

    data class DiscoverPage(
        val newReleaseAlbums: List<AlbumItem>,
        val moods: List<Mood.Item>
    )

    data class DiscoverPageAlbums(
        val newReleaseAlbums: List<AlbumItem>

    )

    data class Mood(
        val title: String,
        val items: List<Item>
    ) {
        data class Item(
            val title: String,
            val stripeColor: Long,
            val endpoint: NavigationEndpoint.Endpoint.Browse
        )
    }

    fun MusicNavigationButtonRenderer.toMood(): Mood.Item? {
        return Mood.Item(
            title = buttonText.runs.firstOrNull()?.text ?: return null,
            stripeColor = solid?.leftStripeColor ?: return null,
            endpoint = clickCommand.browseEndpoint ?: return null
        )
    }


    data class ItemsPage<T : Item>(
        var items: List<T>?,
        val continuation: String?
    )
}
