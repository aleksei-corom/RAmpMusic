package it.vfsfitvnm.innertube.models

import it.vfsfitvnm.innertube.utils.LocalePreferences
import kotlinx.serialization.Serializable

@Serializable
data class Context(
    val client: Client,
    val thirdParty: ThirdParty? = null,
) {
    @Serializable
    data class Client(
        val clientName: String,
        val clientVersion: String,
        val platform: String,
        val hl: String? = "en",
        //val hl: String = Locale.getDefault().toLanguageTag(), //"en",
        //val hl: String = Innertube.localeHl,
        val gl: String? = null,
        val visitorData: String? = null,
        val androidSdkVersion: Int? = null,
        val userAgent: String? = null,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
    )

    @Serializable
    data class ThirdParty(
        val embedUrl: String,
    )

    companion object {
        /*
        val DefaultWeb = Context(
            client = Client(
                clientName = "WEB_REMIX",
                clientVersion = "1.20220918",
                platform = "DESKTOP",
            )
        )


        val DefaultAndroid = Context(
            client = Client(
                clientName = "ANDROID_MUSIC",
                clientVersion = "5.28.1",
                platform = "MOBILE",
                androidSdkVersion = 30,
                userAgent = "com.google.android.apps.youtube.music/5.28.1 (Linux; U; Android 11) gzip"
            )
        )
        */

        // Versión del cliente web de YouTube Music. Si es demasiado antigua YouTube
        // devuelve respuestas degradadas (sin cabecera, sin listas de canciones) y el
        // reproductor contesta "Video unavailable" a todo.
        private const val WEB_REMIX_VERSION = "1.20261027.10.00"

        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        // `gl`/`hl` vacíos hacen que YouTube devuelva HTTP 400 en TODAS las
        // peticiones (búsqueda, sugerencias, portada...). Se normalizan a null.
        val hl = LocalePreferences.preference?.hl?.takeIf { it.isNotBlank() }
        val gl = LocalePreferences.preference?.gl?.takeIf { it.isNotBlank() }

        val DefaultWeb = Context(
            client = Client(
                clientName = "WEB_REMIX",
                clientVersion = WEB_REMIX_VERSION,
                platform = "DESKTOP",
                hl = hl,
                gl = gl,
                userAgent = DESKTOP_USER_AGENT
            )
        )


        val DefaultWebWithLocale = DefaultWeb


        val DefaultAndroid = Context(
            client = Client(
                clientName = "ANDROID_MUSIC",
                clientVersion = "8.10.51",
                platform = "MOBILE",
                androidSdkVersion = 34,
                osName = "Android",
                osVersion = "14",
                userAgent = "com.google.android.apps.youtube.music/8.10.51 (Linux; U; Android 14; en_US) gzip"
            )
        )

        /**
         * Cliente iOS: es el que hoy devuelve `streamingData` con URLs directas
         * (`itag 140/251`, sin `signatureCipher` ni PO token) incluso sin cuenta.
         * Es el primer cliente que se prueba al resolver la reproducción.
         */
        val DefaultIos = Context(
            client = Client(
                clientName = "IOS",
                clientVersion = "20.10.4",
                platform = "MOBILE",
                hl = hl,
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                osName = "iPhone",
                osVersion = "18.5.0.22F76",
                userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_5_0 like Mac OS X;)",
            )
        )

        val DefaultAndroidVr = Context(
            client = Client(
                clientName = "ANDROID_VR",
                clientVersion = "1.65.10",
                platform = "MOBILE",
                androidSdkVersion = 32,
                osName = "Android",
                osVersion = "12",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12; GB) gzip",
            )
        )

        // Cliente "Apple Vision Pro" reportado como funcional en 2026: devuelve URLs de
        // audio sin firma ni PO token (OuterTune #1279). Se mantiene como fallback.
        val DefaultVisionOs = Context(
            client = Client(
                clientName = "VISIONOS",
                clientVersion = "0.1",
                platform = "MOBILE",
                userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
                osName = "visionOS",
                osVersion = "1.3.21O771",
                deviceMake = "Apple",
                deviceModel = "RealityDevice14,1"
            )
        )

        val DefaultTv = Context(
            client = Client(
                clientName = "TVHTML5",
                clientVersion = "7.20250101.00.00",
                platform = "TV",
                userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
            )
        )

        val DefaultAgeRestrictionBypass = Context(
            client = Client(
                clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientVersion = "2.0",
                platform = "TV"
            )
        )
    }
}
