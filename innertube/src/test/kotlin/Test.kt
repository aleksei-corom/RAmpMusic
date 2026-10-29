import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.models.bodies.NextBody
import it.vfsfitvnm.innertube.models.bodies.PlayerBody
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.models.bodies.SearchSuggestionsBody
import it.vfsfitvnm.innertube.requests.albumPage
import it.vfsfitvnm.innertube.requests.artistPage
import it.vfsfitvnm.innertube.requests.itemsPage
import it.vfsfitvnm.innertube.requests.player
import it.vfsfitvnm.innertube.requests.playlistPage
import it.vfsfitvnm.innertube.requests.relatedPage
import it.vfsfitvnm.innertube.requests.relatedSongs
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.requests.searchSuggestions
import it.vfsfitvnm.innertube.utils.LocalePreferenceItem
import it.vfsfitvnm.innertube.utils.LocalePreferences
import it.vfsfitvnm.innertube.utils.from
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.junit.Test

class Test {
    @Test
    @Throws(Exception::class)
    fun test() = runBlocking {
        // Reproduce lo que hace MainActivity: hl = "es-CO", gl = "".
        // Un gl vacío provocaba HTTP 400 en todas las peticiones.
        LocalePreferences.preference = LocalePreferenceItem(hl = "es-CO", gl = "")
        println("CONTEXT hl=${it.vfsfitvnm.innertube.models.Context.DefaultWeb.client.hl} gl=${it.vfsfitvnm.innertube.models.Context.DefaultWeb.client.gl}")

        val suggestions = Innertube.searchSuggestions(SearchSuggestionsBody(input = "eminem"))?.getOrNull()
        println("SUGGESTIONS count=${suggestions?.size} first=${suggestions?.firstOrNull()}")

        val artist = Innertube.artistPage(BrowseBody(browseId = "UCedvOgsKFzcK3hA5taf3KoQ"))?.getOrNull()
        println("ARTIST name=${artist?.name} songs=${artist?.songs?.size} albums=${artist?.albums?.size} singles=${artist?.singles?.size}")

        artist?.songsEndpoint?.let { endpoint ->
            val browseId = endpoint.browseId ?: return@let
            val allSongs = Innertube.itemsPage(
                body = BrowseBody(browseId = browseId, params = endpoint.params),
                fromMusicResponsiveListItemRenderer = Innertube.SongItem.Companion::from
            )?.getOrNull()
            println("ALL SONGS count=${allSongs?.items?.size} first=${allSongs?.items?.firstOrNull()?.info?.name}")
        }

        artist?.albumsEndpoint?.let { endpoint ->
            val browseId = endpoint.browseId ?: return@let
            val discography = Innertube.itemsPage(
                body = BrowseBody(browseId = browseId, params = endpoint.params),
                fromMusicTwoRowItemRenderer = Innertube.AlbumItem.Companion::from
            )?.getOrNull()
            println("DISCOGRAPHY albums=${discography?.items?.size} first=${discography?.items?.firstOrNull()?.info?.name}")
        }

        val album = Innertube.albumPage(BrowseBody(browseId = "MPREb_DmBhgAVua1o"))?.getOrNull()
        println("ALBUM title=${album?.title} authors=${album?.authors?.joinToString { it.name ?: "" }} year=${album?.year} songs=${album?.songsPage?.items?.size}")
        album?.songsPage?.items?.firstOrNull()?.let {
            println("ALBUM song='${it.info?.name}' authors=${it.authors?.joinToString { a -> a.name ?: "" }} album=${it.album?.name} duration=${it.durationText}")
        }

        val playlist = Innertube.playlistPage(BrowseBody(browseId = "VLPLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"))?.getOrNull()
        println("PLAYLIST title=${playlist?.title} songs=${playlist?.songsPage?.items?.size}")
        playlist?.songsPage?.items?.firstOrNull()?.let {
            println("PLAYLIST song='${it.info?.name}' authors=${it.authors?.joinToString { a -> a.name ?: "" }} duration=${it.durationText}")
        }

        val searchSongs = Innertube.searchPage(
            body = SearchBody(query = "eminem", params = Innertube.SearchFilter.Song.value),
            fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
        )?.getOrNull()
        println("SEARCH songs=${searchSongs?.items?.size} first='${searchSongs?.items?.firstOrNull()?.info?.name}' authors=${searchSongs?.items?.firstOrNull()?.authors?.joinToString { a -> a.name ?: "" }} album=${searchSongs?.items?.firstOrNull()?.album?.name} duration=${searchSongs?.items?.firstOrNull()?.durationText}")

        val searchAlbums = Innertube.searchPage(
            body = SearchBody(query = "eminem", params = Innertube.SearchFilter.Album.value),
            fromMusicShelfRendererContent = Innertube.AlbumItem.Companion::from
        )?.getOrNull()
        println("SEARCH albums=${searchAlbums?.items?.size} first='${searchAlbums?.items?.firstOrNull()?.info?.name}' year=${searchAlbums?.items?.firstOrNull()?.year}")

        val searchArtists = Innertube.searchPage(
            body = SearchBody(query = "eminem", params = Innertube.SearchFilter.Artist.value),
            fromMusicShelfRendererContent = Innertube.ArtistItem.Companion::from
        )?.getOrNull()
        println("SEARCH artists=${searchArtists?.items?.size} first='${searchArtists?.items?.firstOrNull()?.info?.name}' key=${searchArtists?.items?.firstOrNull()?.key}")

        val searchPlaylists = Innertube.searchPage(
            body = SearchBody(query = "eminem", params = Innertube.SearchFilter.CommunityPlaylist.value),
            fromMusicShelfRendererContent = Innertube.PlaylistItem.Companion::from
        )?.getOrNull()
        println("SEARCH playlists=${searchPlaylists?.items?.size} first='${searchPlaylists?.items?.firstOrNull()?.info?.name}' channel=${searchPlaylists?.items?.firstOrNull()?.channel?.name}")

        val searchVideos = Innertube.searchPage(
            body = SearchBody(query = "eminem", params = Innertube.SearchFilter.Video.value),
            fromMusicShelfRendererContent = Innertube.VideoItem.Companion::from
        )?.getOrNull()
        println("SEARCH videos=${searchVideos?.items?.size} first='${searchVideos?.items?.firstOrNull()?.info?.name}' authors=${searchVideos?.items?.firstOrNull()?.authors?.joinToString { a -> a.name ?: "" }} views=${searchVideos?.items?.firstOrNull()?.viewsText} duration=${searchVideos?.items?.firstOrNull()?.durationText}")

        val related = Innertube.relatedPage(NextBody(videoId = "4wOLVrGHiIU"))?.getOrNull()
        println("RELATED songs=${related?.songs?.size} playlists=${related?.playlists?.size} albums=${related?.albums?.size} artists=${related?.artists?.size}")
        println("RELATED firstSong='${related?.songs?.firstOrNull()?.info?.name}' firstPlaylist='${related?.playlists?.firstOrNull()?.info?.name}' firstAlbum='${related?.albums?.firstOrNull()?.info?.name}' firstArtist='${related?.artists?.firstOrNull()?.info?.name}'")

        val relatedSongs = Innertube.relatedSongs(NextBody(videoId = "4wOLVrGHiIU"))?.getOrNull()
        println("RELATED SONGS=${relatedSongs?.songs?.size} first='${relatedSongs?.songs?.firstOrNull()?.info?.name}'")

        println("DEBUG visitorData=${Innertube.ensureVisitorData()}")
        for (u in listOf("https://music.youtube.com/", "https://www.youtube.com/sw.js_data", "https://music.youtube.com/sw.js_data")) {
            runCatching {
                val resp = Innertube.client.get(u)
                val text = resp.bodyAsText()
                println("DEBUG GET $u status=${resp.status} len=${text.length} hasVisitor=${text.contains("VISITOR_DATA")} head=${text.take(80).replace("\n"," ")}")
                println("DEBUG regex=${Regex("\"VISITOR_DATA\":\"([^\"]+)\"").find(text)?.groupValues?.getOrNull(1)?.take(30)}")
            }.onFailure { println("DEBUG GET $u ERROR ${it.message}") }
        }

        val player = Innertube.player(PlayerBody(videoId = "4wOLVrGHiIU"))?.getOrNull()
        val audio = player?.streamingData?.highestQualityFormat
        println("PLAYER status=${player?.playabilityStatus?.status} itag=${audio?.itag} url=${audio?.url?.take(60)}")
        println("PLAYER headers=${player?.mediaHeaders}")
        println("PLAYER visitorData=${Innertube.visitorData?.take(20)}")

        println("PLAYER client=${Innertube.lastPlayerClient}")

        audio?.url?.let { url ->
            for (range in listOf("bytes=0-524287", "bytes=524288-1048575", "bytes=524288-")) {
                try {
                    val connection = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
                    player?.mediaHeaders?.forEach { (k, v) -> connection.setRequestProperty(k, v) }
                    connection.setRequestProperty("Range", range)
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    val code = connection.responseCode
                    val length = connection.contentLengthLong
                    println("PLAYER range header $range -> $code len=$length")
                    connection.disconnect()
                } catch (e: Exception) {
                    println("PLAYER range header $range -> ERROR ${e.message}")
                }
            }
        }
        Unit
    }
}


