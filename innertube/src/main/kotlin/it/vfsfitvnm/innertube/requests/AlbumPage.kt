package it.vfsfitvnm.innertube.requests

import io.ktor.http.Url
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.NavigationEndpoint
import it.vfsfitvnm.innertube.models.bodies.BrowseBody

suspend fun Innertube.albumPage(body: BrowseBody): Result<Innertube.PlaylistOrAlbumPage>? {
    return playlistPage(body)?.map { album ->
        // La página del álbum suele traer ya todas las canciones. Solo se pide la
        // playlist del álbum (browseId "VL...") si la respuesta vino sin ellas.
        val albumWithSongs = if (album.songsPage?.items.isNullOrEmpty()) {
            album.url
                ?.let { Url(it).parameters["list"] }
                ?.let { playlistId ->
                    playlistPage(BrowseBody(browseId = "VL$playlistId"))
                        ?.getOrNull()
                        ?.let { playlist -> album.copy(songsPage = playlist.songsPage) }
                }
                ?: album
        } else {
            album
        }

        val albumInfo = Innertube.Info(
            name = albumWithSongs.title,
            endpoint = NavigationEndpoint.Endpoint.Browse(
                browseId = body.browseId,
                params = body.params
            )
        )

        albumWithSongs.copy(
            songsPage = albumWithSongs.songsPage?.copy(
                items = albumWithSongs.songsPage.items?.map { song ->
                    song.copy(
                        authors = song.authors ?: albumWithSongs.authors,
                        album = albumInfo,
                        thumbnail = albumWithSongs.thumbnail
                    )
                }
            )
        )
    }
}
