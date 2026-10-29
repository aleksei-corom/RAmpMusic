package it.vfsfitvnm.vimusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.vimusic.enums.CoilDiskCacheMaxSize
import it.vfsfitvnm.vimusic.utils.PoTokenProvider
import it.vfsfitvnm.vimusic.utils.coilDiskCacheMaxSizeKey
import it.vfsfitvnm.vimusic.utils.getEnum
import it.vfsfitvnm.vimusic.utils.preferences

class MainApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        DatabaseInitializer()

        // PO tokens de BotGuard: sin ellos googlevideo corta la descarga tras el
        // primer trozo (error de reproducción a los ~30 s).
        PoTokenProvider.init(this)
        Innertube.mediaPotProvider = { videoId ->
            PoTokenProvider.getPoToken(videoId)
        }
        Innertube.streamingPotProvider = { visitorData ->
            PoTokenProvider.getStreamingPot(visitorData)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .respectCacheHeaders(false)
            .diskCache(
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil"))
                    .maxSizeBytes(
                        preferences.getEnum(
                            coilDiskCacheMaxSizeKey,
                            CoilDiskCacheMaxSize.`128MB`
                        ).bytes
                    )
                    .build()
            )
            .build()
    }
}
