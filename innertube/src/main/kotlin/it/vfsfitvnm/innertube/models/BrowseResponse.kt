package it.vfsfitvnm.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class BrowseResponse(
    val contents: Contents?,
    val header: Header?,
    val microformat: Microformat?,
    val responseContext: ResponseContext? = null,
) {
    @Serializable
    data class ResponseContext(
        val visitorData: String? = null,
    )

    @Serializable
    data class Contents(
        val singleColumnBrowseResultsRenderer: Tabs?,
        val twoColumnBrowseResultsRenderer: TwoColumnBrowseResultsRenderer? = null,
        val sectionListRenderer: SectionListRenderer?,
    )

    /**
     * Layout que YouTube Music usa desde hace un tiempo para álbumes y playlists:
     * la cabecera va en `tabs[0].tabRenderer.content.sectionListRenderer` y la
     * lista de canciones en `secondaryContents.sectionListRenderer`.
     */
    @Serializable
    data class TwoColumnBrowseResultsRenderer(
        val secondaryContents: SecondaryContents? = null,
        val tabs: List<Tabs.Tab>? = null,
    )

    @Serializable
    data class SecondaryContents(
        val sectionListRenderer: SectionListRenderer? = null,
    )

    @Serializable
    data class Header @OptIn(ExperimentalSerializationApi::class) constructor(
        @JsonNames("musicVisualHeaderRenderer")
        val musicImmersiveHeaderRenderer: MusicImmersiveHeaderRenderer?,
        val musicDetailHeaderRenderer: MusicDetailHeaderRenderer?,
    ) {
        @Serializable
        data class MusicDetailHeaderRenderer(
            val title: Runs?,
            val description: Runs? = null,
            val subtitle: Runs?,
            val secondSubtitle: Runs?,
            val thumbnail: ThumbnailRenderer?,
        )

        @Serializable
        data class MusicImmersiveHeaderRenderer(
            val description: Runs?,
            val playButton: PlayButton?,
            val startRadioButton: StartRadioButton?,
            val thumbnail: ThumbnailRenderer?,
            val foregroundThumbnail: ThumbnailRenderer?,
            val title: Runs?,
            val subscriptionButton: SubscriptionButton?
        ) {
            @Serializable
            data class PlayButton(
                val buttonRenderer: ButtonRenderer?
            )

            @Serializable
            data class StartRadioButton(
                val buttonRenderer: ButtonRenderer?
            )

        }
    }

    @Serializable
    data class Microformat(
        val microformatDataRenderer: MicroformatDataRenderer?
    ) {
        @Serializable
        data class MicroformatDataRenderer(
            val urlCanonical: String? = null,
            val title: String? = null,
            val description: String? = null,
            val thumbnail: MicroformatThumbnail? = null,
        )

        @Serializable
        data class MicroformatThumbnail(
            val thumbnails: List<Thumbnail>? = null,
        )
    }
}
