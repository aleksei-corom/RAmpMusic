package it.vfsfitvnm.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.BrowseResponse
import it.vfsfitvnm.innertube.models.MusicCarouselShelfRenderer
import it.vfsfitvnm.innertube.models.NextResponse
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.NextBody
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.innertube.utils.runCatchingNonCancellable

suspend fun Innertube.relatedSongs(body: NextBody) = runCatchingNonCancellable {
    val nextResponse = client.post(next) {
        setBody(body)
        mask("contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.tabRenderer(endpoint,title)")
    }.body<NextResponse>()

    val browseId = nextResponse.relatedTabBrowseId() ?: return@runCatchingNonCancellable null

    val response = client.post(browse) {
        setBody(BrowseBody(browseId = browseId))
    }.body<BrowseResponse>()

    val songsSection = response.contents
        ?.sectionListContent()
        ?.firstOrNull { section ->
            section.musicCarouselShelfRenderer
                ?.contents
                ?.firstOrNull { it.musicResponsiveListItemRenderer != null } != null
        }

    Innertube.RelatedSongs(
        songs = songsSection
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicResponsiveListItemRenderer)
            ?.mapNotNull(Innertube.SongItem::from)
    )
}
