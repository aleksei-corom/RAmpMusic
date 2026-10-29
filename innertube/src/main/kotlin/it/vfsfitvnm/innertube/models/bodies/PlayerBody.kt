package it.vfsfitvnm.innertube.models.bodies

import it.vfsfitvnm.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class PlayerBody(
    val context: Context = Context.DefaultAndroid,
    //val context: Context = Context.DefaultWeb,
    val videoId: String,
    val playlistId: String? = null,
    val playbackContext: PlaybackContext? = null,
    val serviceIntegrityDimensions: ServiceIntegrityDimensions? = null,
) {
    @Serializable
    data class PlaybackContext(
        val contentPlaybackContext: ContentPlaybackContext? = null,
    )

    @Serializable
    data class ContentPlaybackContext(
        val signatureTimestamp: Int? = null,
    )

    @Serializable
    data class ServiceIntegrityDimensions(
        val poToken: String,
    )
}
