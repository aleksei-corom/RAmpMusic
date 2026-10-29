package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.MusicCarouselShelfRenderer
import it.vfsfitvnm.innertube.models.NextResponse
import it.vfsfitvnm.innertube.models.SectionListRenderer
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.NextBody
import it.vfsfitvnm.innertube.models.NextResponse.Contents.SingleColumnMusicWatchNextResultsRenderer.TabbedRenderer.WatchNextTabbedResultsRenderer.Tab as WatchNextTab
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.relatedPage(body: NextBody) = runCatchingNonCancellable {
    val nextResponse = client.post(next) {
        setBody(body)
        mask("contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.tabRenderer(endpoint,title)")
    }.body<NextResponse>()

    val browseId = nextResponse.relatedTabBrowseId() ?: return@runCatchingNonCancellable null

    val response = client.post(browse) {
        setBody(BrowseBody(browseId = browseId))
    }.body<BrowseResponse>()

    // Las secciones de "Relacionado" vienen en `contents.sectionListRenderer` o,
    // según el layout, dentro de singleColumn/twoColumn.
    val sections: List<SectionListRenderer.Content> = response.contents
        ?.sectionListContent()
        ?: emptyList()

    fun MusicCarouselShelfRenderer.firstTwoRowItem() = contents
        ?.firstOrNull { it.musicTwoRowItemRenderer != null }
        ?.musicTwoRowItemRenderer

    val songsSection = sections.firstOrNull { section ->
        section.musicCarouselShelfRenderer
            ?.contents
            ?.firstOrNull { it.musicResponsiveListItemRenderer != null } != null
    }

    val playlistsSection = sections.firstOrNull { section ->
        section.musicCarouselShelfRenderer?.firstTwoRowItem()?.isPlaylist == true
    }

    val albumsSection = sections.firstOrNull { section ->
        section.musicCarouselShelfRenderer?.firstTwoRowItem()?.isAlbum == true
    }

    val artistsSection = sections.firstOrNull { section ->
        section.musicCarouselShelfRenderer?.firstTwoRowItem()?.isArtist == true
    }

    Innertube.RelatedPage(
        songs = songsSection
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicResponsiveListItemRenderer)
            ?.mapNotNull(Innertube.SongItem::from),
        playlists = playlistsSection
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.PlaylistItem::from),
        albums = albumsSection
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.AlbumItem::from),
        artists = artistsSection
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Innertube.ArtistItem::from),
    )
}

/**
 * YouTube cambió las pestañas de `/next`: ahora son
 * [A continuación, Letra, Comentarios, Relacionado] y la de "Relacionado" es la
 * última que lleva un `browseEndpoint` (la de comentarios no tiene endpoint).
 * Antes se usaba `tabs[2]`, que devolvía null y dejaba la portada sin "selecciones
 * rápidas".
 */
internal fun NextResponse.relatedTabBrowseId(): String? {
    val tabs: List<WatchNextTab> = contents
        ?.singleColumnMusicWatchNextResultsRenderer
        ?.tabbedRenderer
        ?.watchNextTabbedResultsRenderer
        ?.tabs
        ?: return null

    val tabsWithEndpoint = tabs.mapNotNull { tab ->
        val tabRenderer = tab.tabRenderer ?: return@mapNotNull null
        val endpoint = tabRenderer.endpoint?.browseEndpoint ?: return@mapNotNull null
        tabRenderer to endpoint
    }

    // Preferimos la pestaña de relacionado por título (cualquier idioma conocido).
    val relatedTitles = setOf("relacionado", "related", "relacionados", "similares")
    val relatedByTitle = tabsWithEndpoint.firstOrNull { (tabRenderer, _) ->
        val title = tabRenderer.title?.lowercase() ?: return@firstOrNull false
        relatedTitles.any { title.contains(it) }
    }

    return (relatedByTitle ?: tabsWithEndpoint.lastOrNull())?.second?.browseId
}
