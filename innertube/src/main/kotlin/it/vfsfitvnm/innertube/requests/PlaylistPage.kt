package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.ContinuationResponse
import it.vfsfitvnm.innertube.models.MusicCarouselShelfRenderer
import it.vfsfitvnm.innertube.models.MusicShelfRenderer
import it.vfsfitvnm.innertube.models.Runs
import it.vfsfitvnm.innertube.models.SectionListRenderer
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.ContinuationBody
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

/**
 * Página de un álbum o de una playlist de YouTube Music.
 *
 * YouTube cambió el layout: ahora la respuesta usa `twoColumnBrowseResultsRenderer`
 * con la cabecera dentro de `tabs[0]` y las canciones en `secondaryContents`.
 * El código anterior solo leía `singleColumnBrowseResultsRenderer`, por lo que
 * la lista de canciones quedaba vacía.
 *
 * No se usa `X-Goog-FieldMask` aquí a propósito: al filtrar campos YouTube omite
 * el contenido de `musicPlaylistShelfRenderer` y las columnas de duración.
 */
suspend fun Innertube.playlistPage(body: BrowseBody) = runCatchingNonCancellable {
    val response = client.post(browse) {
        setBody(body)
    }.body<BrowseResponse>()

    val twoColumn = response.contents?.twoColumnBrowseResultsRenderer
    val singleColumnSectionList = response.contents
        ?.singleColumnBrowseResultsRenderer
        ?.tabs
        ?.firstOrNull()
        ?.tabRenderer
        ?.content
        ?.sectionListRenderer

    val sections: List<SectionListRenderer.Content> = twoColumn
        ?.secondaryContents
        ?.sectionListRenderer
        ?.contents
        ?: singleColumnSectionList?.contents
        ?: response.contents?.sectionListRenderer?.contents
        ?: emptyList()

    val responsiveHeader = twoColumn
        ?.tabs
        ?.firstNotNullOfOrNull { it.tabRenderer?.content?.sectionListRenderer }
        ?.contents
        ?.firstNotNullOfOrNull { it.musicResponsiveHeaderRenderer }

    val detailHeader = response.header?.musicDetailHeaderRenderer
    val microformat = response.microformat?.microformatDataRenderer

    val subtitle: Runs? = responsiveHeader?.subtitle ?: detailHeader?.subtitle
    val subtitleGroups = subtitle?.splitBySeparator()

    val authors = responsiveHeader
        ?.straplineTextOne
        ?.splitBySeparator()
        ?.firstOrNull()
        ?.mapNotNull { run -> run.text?.let { Innertube.Info(it, run.navigationEndpoint?.browseEndpoint) } }
        ?.takeIf { it.isNotEmpty() }
        ?: detailHeader
            ?.subtitle
            ?.splitBySeparator()
            ?.getOrNull(1)
            ?.mapNotNull { run -> run.text?.let { Innertube.Info(it, run.navigationEndpoint?.browseEndpoint) } }

    val musicShelfRenderer = sections
        .firstOrNull { it.musicShelfRenderer?.contents?.isNotEmpty() == true }
        ?.musicShelfRenderer

    val musicCarouselShelfRenderer = sections
        .firstOrNull { it.musicCarouselShelfRenderer != null }
        ?.musicCarouselShelfRenderer

    val songsPage = musicShelfRenderer?.toSongsPage(
        fallbackContinuation = (
            twoColumn?.secondaryContents?.sectionListRenderer?.continuations
                ?: singleColumnSectionList?.continuations
            )
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation
    )

    Innertube.PlaylistOrAlbumPage(
        title = responsiveHeader?.title?.text
            ?: detailHeader?.title?.text
            ?: microformat?.title,
        thumbnail = responsiveHeader
            ?.thumbnail
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
            ?: detailHeader
                ?.thumbnail
                ?.musicThumbnailRenderer
                ?.thumbnail
                ?.thumbnails
                ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
            ?: microformat
                ?.thumbnail
                ?.thumbnails
                ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
            ?: songsPage?.items?.firstOrNull()?.thumbnail,
        authors = authors,
        year = subtitleGroups
            ?.getOrNull(2)
            ?.firstOrNull()
            ?.text
            ?.takeIf(::looksLikeYear)
            ?: subtitleGroups
                ?.getOrNull(1)
                ?.firstOrNull()
                ?.text
                ?.takeIf(::looksLikeYear),
        url = microformat?.urlCanonical,
        songsPage = songsPage,
        otherVersions = musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.AlbumItem::from)
    )
}

suspend fun Innertube.playlistPage(body: ContinuationBody) = runCatchingNonCancellable {
    val response = client.post(browse) {
        setBody(body)
        mask("continuationContents.musicPlaylistShelfContinuation(continuations,contents.$musicResponsiveListItemRendererMask),continuationContents.musicShelfContinuation(continuations,contents.$musicResponsiveListItemRendererMask)")
    }.body<ContinuationResponse>()

    response
        .continuationContents
        ?.musicShelfContinuation
        ?.toSongsPage()
}

private fun MusicShelfRenderer?.toSongsPage(fallbackContinuation: String? = null) =
    Innertube.ItemsPage(
        items = this
            ?.contents
            ?.mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
            ?.mapNotNull(Innertube.SongItem::from),
        continuation = this
            ?.continuations
            ?.firstOrNull()
            ?.nextContinuationData
            ?.continuation
            ?: fallbackContinuation
    )

private fun looksLikeYear(text: String?): Boolean =
    text?.matches(Regex("^(19|20)\\d{2}$")) == true
