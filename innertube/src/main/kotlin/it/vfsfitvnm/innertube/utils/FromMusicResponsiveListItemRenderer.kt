package it.vfsfitvnm.innertube.utils

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.MusicResponsiveListItemRenderer
import it.vfsfitvnm.innertube.models.NavigationEndpoint

private val DURATION_REGEX = Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$")

/**
 * Convierte una fila de YouTube Music en una canción.
 *
 * No se asume una posición fija para el artista, el álbum o la duración: según
 * el tipo de página (álbum, playlist, búsqueda, artista) las columnas cambian y
 * la tercera puede ser el número de reproducciones en vez del álbum. Por eso se
 * clasifican los `runs` por el tipo de endpoint que llevan.
 */
fun Innertube.SongItem.Companion.from(renderer: MusicResponsiveListItemRenderer): Innertube.SongItem? {
    val runs = renderer.flexColumns.mapNotNull { column ->
        column.musicResponsiveListItemFlexColumnRenderer?.text?.runs
    }

    val titleRun = runs.firstOrNull()?.firstOrNull() ?: return null

    val watchEndpoint = titleRun.navigationEndpoint?.watchEndpoint
        ?: renderer.playlistItemData?.videoId?.let { videoId ->
            NavigationEndpoint.Endpoint.Watch(videoId = videoId)
        }
        ?: return null

    val info = Innertube.Info(titleRun.text, watchEndpoint)

    val browseRuns = runs.flatten()

    val authors = browseRuns
        .mapNotNull { run ->
            run.navigationEndpoint
                ?.browseEndpoint
                ?.takeIf { it.type == "MUSIC_PAGE_TYPE_ARTIST" }
                ?.let { endpoint -> Innertube.Info(run.text, endpoint) }
        }
        .distinctBy { it.endpoint?.browseId }
        .takeIf { it.isNotEmpty() }

    val album = browseRuns
        .firstNotNullOfOrNull { run ->
            run.navigationEndpoint
                ?.browseEndpoint
                ?.takeIf { it.type == "MUSIC_PAGE_TYPE_ALBUM" }
                ?.let { endpoint -> Innertube.Info(run.text, endpoint) }
        }

    val durationText = renderer.fixedColumns
        ?.firstOrNull()
        ?.musicResponsiveListItemFlexColumnRenderer
        ?.text
        ?.runs
        ?.firstOrNull()
        ?.text
        ?: browseRuns.firstOrNull { run ->
            run.navigationEndpoint == null && run.text?.matches(DURATION_REGEX) == true
        }?.text

    return Innertube.SongItem(
        info = info,
        authors = authors,
        album = album,
        durationText = durationText,
        thumbnail = renderer
            .thumbnail
            ?.musicThumbnailRenderer
            ?.thumbnail
            ?.thumbnails
            ?.firstOrNull(),
        explicit = renderer
            .badges
            ?.find {
                it.musicInlineBadgeRenderer.icon.iconType == "MUSIC_EXPLICIT_BADGE"
            } != null,
    )
}
