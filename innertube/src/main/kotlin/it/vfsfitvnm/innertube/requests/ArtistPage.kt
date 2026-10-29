package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.MusicCarouselShelfRenderer
import it.vfsfitvnm.innertube.models.MusicShelfRenderer
import it.vfsfitvnm.innertube.models.SectionListRenderer
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable
import java.text.Normalizer

suspend fun Innertube.artistPage(body: BrowseBody): Result<Innertube.ArtistPage>? =
    runCatchingNonCancellable {
        val response = client.post(browse) {
            setBody(body)
            mask("contents,header")
        }.body<BrowseResponse>()

        val sections: List<SectionListRenderer.Content> = response.contents
            ?.singleColumnBrowseResultsRenderer
            ?.tabs
            ?.firstOrNull()
            ?.tabRenderer
            ?.content
            ?.sectionListRenderer
            ?.contents
            ?: response.contents?.sectionListRenderer?.contents
            ?: emptyList()

        // YouTube Music no garantiza el orden ni el idioma de las secciones: con
        // `hl=es` el título es "Canciones más escuchadas" en lugar de "Top songs".
        // Por eso se buscan por estructura y, para álbumes/singles, por los
        // parámetros del botón "ver todo", que no dependen del idioma.
        val songsSection = sections.firstOrNull { it.musicShelfRenderer != null }
        val carousels = sections.mapNotNull { it.musicCarouselShelfRenderer }

        val albumsSection = carousels.firstOrNull { it.isAlbumsSection() }
            ?: carousels.firstOrNull { normalizeTitle(it.titleText())?.let(ALBUMS_TITLES::contains) == true }
        val singlesSection = carousels.firstOrNull { it.isSinglesSection() }
            ?: carousels.firstOrNull { normalizeTitle(it.titleText())?.let(SINGLES_TITLES::contains) == true }

        Innertube.ArtistPage(
            name = response
                .header
                ?.musicImmersiveHeaderRenderer
                ?.title
                ?.text,
            description = response
                .header
                ?.musicImmersiveHeaderRenderer
                ?.description
                ?.text,
            subscriberCountText = response
                .header
                ?.musicImmersiveHeaderRenderer
                ?.subscriptionButton
                ?.subscribeButtonRenderer
                ?.subscriberCountText
                ?.text,
            thumbnail = (response
                .header
                ?.musicImmersiveHeaderRenderer
                ?.foregroundThumbnail
                ?: response
                .header
                ?.musicImmersiveHeaderRenderer
                ?.thumbnail)
                ?.musicThumbnailRenderer
                ?.thumbnail
                ?.thumbnails
                ?.getOrNull(0),
            shuffleEndpoint = response
                .header
                ?.musicImmersiveHeaderRenderer
                ?.playButton
                ?.buttonRenderer
                ?.navigationEndpoint
                ?.watchEndpoint,
            radioEndpoint = response
                .header
                ?.musicImmersiveHeaderRenderer
                ?.startRadioButton
                ?.buttonRenderer
                ?.navigationEndpoint
                ?.watchEndpoint,
            songs = songsSection
                ?.musicShelfRenderer
                ?.contents
                ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                ?.mapNotNull(Innertube.SongItem::from),
            songsEndpoint = songsSection
                ?.musicShelfRenderer
                ?.bottomEndpoint
                ?.browseEndpoint
                ?: songsSection
                    ?.musicShelfRenderer
                    ?.title
                    ?.runs
                    ?.firstOrNull()
                    ?.navigationEndpoint
                    ?.browseEndpoint,
            albums = albumsSection
                ?.contents
                ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                ?.mapNotNull(Innertube.AlbumItem::from),
            albumsEndpoint = albumsSection
                ?.moreContentEndpoint(),
            singles = singlesSection
                ?.contents
                ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                ?.mapNotNull(Innertube.AlbumItem::from),
            singlesEndpoint = singlesSection
                ?.moreContentEndpoint(),
        )
    }

/** Parámetros del endpoint "ver todo" de álbumes (independientes del idioma). */
private const val ALBUMS_MORE_PARAMS = "ggMIegYIARoCAQI"

/** Parámetros del endpoint "ver todo" de singles y EPs. */
private const val SINGLES_MORE_PARAMS = "ggMIegYIAhoCAQI"

private fun MusicCarouselShelfRenderer.moreContentEndpoint() =
    header
        ?.musicCarouselShelfBasicHeaderRenderer
        ?.moreContentButton
        ?.buttonRenderer
        ?.navigationEndpoint
        ?.browseEndpoint

private fun MusicCarouselShelfRenderer.titleText(): String? =
    header
        ?.musicCarouselShelfBasicHeaderRenderer
        ?.title
        ?.text

private fun MusicCarouselShelfRenderer.isAlbumsSection() =
    moreContentEndpoint()?.params?.contains(ALBUMS_MORE_PARAMS) == true

private fun MusicCarouselShelfRenderer.isSinglesSection() =
    moreContentEndpoint()?.params?.contains(SINGLES_MORE_PARAMS) == true

/**
 * Minúsculas y sin acentos, para que "Álbumes" y "Albumes" se comparen igual.
 */
private fun normalizeTitle(text: String?): String? =
    text
        ?.let { Normalizer.normalize(it, Normalizer.Form.NFD) }
        ?.replace(Regex("\\p{M}+"), "")
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }

private val ALBUMS_TITLES = setOf(
    "albums", "albumes", "alben", "albuns", "album", "albumi", "albumy", "albume",
    "albümler", "альбомы", "альбомдар", "アルバム", "앨범", "专辑", "專輯", "อัลบั้ม",
    "ألبومات", "આલ્બમ", "ಆಲ್ಬಂಗಳು", "ఆల్బమ్లు", "ആൽബങ്ങൾ", "ඇල්බම", "ඇල්බම්"
)

private val SINGLES_TITLES = setOf(
    "singles", "singles & eps", "singles & ep", "singles y eps", "sencillos",
    "singles et ep", "singoli ed ep", "singles e eps", "single", "single & ep",
    "single & eps", "singles - ep", "синглы и выпуски", "シングルと ep", "シングルとep",
    "싱글 및 ep", "单曲与 ep", "單曲與 ep", "singlové", "single a ep", "single și ep",
    "single i ep", "singles y ep", "સિંગલ્સ", "ಸಿಂಗಲ್ಸ್", "సింగిల్స్"
)
