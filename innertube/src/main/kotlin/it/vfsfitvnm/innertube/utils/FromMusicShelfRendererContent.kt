package it.vfsfitvnm.innertube.utils

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.MusicShelfRenderer

fun Innertube.SongItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.SongItem? {
    // El parseo real vive en FromMusicResponsiveListItemRenderer: clasifica las
    // columnas por tipo de endpoint en lugar de asumir una posición fija.
    val renderer = content.musicResponsiveListItemRenderer ?: return null
    return Innertube.SongItem.from(renderer)
}

fun Innertube.VideoItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.VideoItem? {
    val (mainRuns, otherRuns) = content.runs

    val titleRun = mainRuns.firstOrNull() ?: return null
    val watchEndpoint = titleRun.navigationEndpoint?.watchEndpoint ?: return null

    return runCatching {
        Innertube.VideoItem(
            info = Innertube.Info(titleRun.text, watchEndpoint),
            authors = otherRuns
                .getOrNull(otherRuns.lastIndex - 2)
                ?.map(Innertube::Info),
            viewsText = otherRuns
                .getOrNull(otherRuns.lastIndex - 1)
                ?.firstOrNull()
                ?.text,
            durationText = otherRuns
                .getOrNull(otherRuns.lastIndex)
                ?.firstOrNull()
                ?.text,
            thumbnail = content
                .thumbnail
        )
    }.getOrNull()

}

fun Innertube.AlbumItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.AlbumItem? {
    val (mainRuns, otherRuns) = content.runs

    return Innertube.AlbumItem(
        info = Innertube.Info(
            name = mainRuns
                .firstOrNull()
                ?.text,
            endpoint = content
                .musicResponsiveListItemRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
        ),
        authors = otherRuns
            .getOrNull(otherRuns.lastIndex - 1)
            ?.map(Innertube::Info),
        year = otherRuns
            .getOrNull(otherRuns.lastIndex)
            ?.firstOrNull()
            ?.text,
        thumbnail = content
            .thumbnail
    ).takeIf { it.info?.endpoint?.browseId != null }
}

fun Innertube.ArtistItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.ArtistItem? {
    val (mainRuns, otherRuns) = content.runs

    return Innertube.ArtistItem(
        info = Innertube.Info(
            name = mainRuns
                .firstOrNull()
                ?.text,
            endpoint = content
                .musicResponsiveListItemRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
        ),
        subscribersCountText = otherRuns
            .lastOrNull()
            ?.last()
            ?.text,
        thumbnail = content
            .thumbnail
    ).takeIf { it.info?.endpoint?.browseId != null }
}

fun Innertube.PlaylistItem.Companion.from(content: MusicShelfRenderer.Content): Innertube.PlaylistItem? {
    val (mainRuns, otherRuns) = content.runs
    val allRuns = content.musicResponsiveListItemRenderer
        ?.flexColumns
        ?.mapNotNull { it.musicResponsiveListItemFlexColumnRenderer?.text?.runs }
        ?.flatten()
        .orEmpty()

    return Innertube.PlaylistItem(
        info = Innertube.Info(
            name = mainRuns
                .firstOrNull()
                ?.text,
            endpoint = content
                .musicResponsiveListItemRenderer
                ?.navigationEndpoint
                ?.browseEndpoint
        ),
        channel = allRuns
            .firstOrNull { run ->
                run.navigationEndpoint?.browseEndpoint?.type == "MUSIC_PAGE_TYPE_ARTIST"
            }
            ?.let { run -> Innertube.Info(run.text, run.navigationEndpoint?.browseEndpoint) },
        songCount = otherRuns
            .lastOrNull()
            ?.firstOrNull()
            ?.text
            ?.takeIf { it.trim().all(Char::isDigit) }
            ?.toIntOrNull(),
        thumbnail = content
            .thumbnail
    ).takeIf { it.info?.endpoint?.browseId != null }
}

