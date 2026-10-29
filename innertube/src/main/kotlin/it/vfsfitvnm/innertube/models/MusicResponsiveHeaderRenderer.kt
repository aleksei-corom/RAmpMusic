package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

/**
 * Cabecera que YouTube Music devuelve en las páginas que usan
 * `twoColumnBrowseResultsRenderer` (álbumes y playlists). Sustituye al antiguo
 * `musicDetailHeaderRenderer`, que ya no aparece en esas respuestas.
 */
@Serializable
data class MusicResponsiveHeaderRenderer(
    val title: Runs? = null,
    val subtitle: Runs? = null,
    val secondSubtitle: Runs? = null,
    val straplineTextOne: Runs? = null,
    val straplineThumbnail: ThumbnailRenderer? = null,
    val thumbnail: ThumbnailRenderer? = null,
    val buttons: List<Button>? = null,
) {
    @Serializable
    data class Button(
        val musicPlayButtonRenderer: MusicPlayButtonRenderer? = null,
    ) {
        @Serializable
        data class MusicPlayButtonRenderer(
            val playNavigationEndpoint: NavigationEndpoint? = null,
        )
    }
}
