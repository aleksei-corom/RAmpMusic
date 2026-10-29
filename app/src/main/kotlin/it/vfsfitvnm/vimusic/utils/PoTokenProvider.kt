package it.vfsfitvnm.vimusic.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Genera PO tokens de YouTube (BotGuard) con un WebView y el mismo flujo que usa
 * NewPipe. Sin PO token, googlevideo devuelve 403 al pedir el segundo rango de una
 * canción, que es el error de reproducción que aparecía a los ~30 s.
 *
 * El token se añade a la URL de audio como `&pot=...`.
 */
object PoTokenProvider {
    private const val TAG = "PoTokenProvider"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var generator: PoTokenWebView? = null

    @Volatile
    private var disabledUntil: Long = 0L

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    private var streamingPotCache: Pair<String, String>? = null

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                if (generator == null) {
                    generator = PoTokenWebView(appCtx)
                }
            } catch (throwable: Throwable) {
                Log.w(TAG, "No se pudo precalentar PoTokenWebView", throwable)
            }
        }
    }

    suspend fun getPoToken(videoId: String): String? {
        if (System.currentTimeMillis() < disabledUntil) return null
        val context = appContext ?: return null

        return try {
            val current = generator ?: withContext(Dispatchers.Main) {
                PoTokenWebView(context).also { generator = it }
            }
            current.generate(videoId).also { lastError = null }
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // El llamador canceló la corrutina (ej. StatsForNerds o reintento de carga).
            // NO destruimos el WebView ni desactivamos el proveedor.
            Log.d(TAG, "Llamada a getPoToken cancelada: ${cancellation.message}")
            throw cancellation
        } catch (throwable: Throwable) {
            lastError = "${throwable.javaClass.simpleName}: ${throwable.message}"
            Log.e(TAG, "No se pudo generar el PO token", throwable)
            // Se reintenta en 30 s si falló de verdad.
            disabledUntil = System.currentTimeMillis() + 30_000
            runCatching { withContext(Dispatchers.Main) { generator?.close() } }
            generator = null
            null
        }
    }


    /**
     * PO token de streaming (GVS), ligado al `visitorData`. Se reutiliza durante
     * la sesión mientras el visitorData no cambie.
     */
    suspend fun getStreamingPot(visitorData: String): String? {
        streamingPotCache?.let { (cachedVisitorData, token) ->
            if (cachedVisitorData == visitorData) return token
        }

        val token = getPoToken(visitorData) ?: return null
        streamingPotCache = visitorData to token
        return token
    }
}

@SuppressLint("SetJavaScriptEnabled")
internal class PoTokenWebView(private val context: Context) {
    private companion object {
        const val TAG = "PoTokenWebView"
        const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        const val API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        const val JS_INTERFACE = "PoTokenBridge"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        const val CREATE_URL = "https://www.youtube.com/api/jnn/v1/Create"
        const val GENERATE_URL = "https://www.youtube.com/api/jnn/v1/GenerateIT"
    }

    private val webView = WebView(context)
    private val ready = CompletableDeferred<Unit>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var closed = false

    init {
        webView.settings.apply {
            javaScriptEnabled = true
            userAgentString = USER_AGENT
            blockNetworkLoads = false
        }

        webView.addJavascriptInterface(this, JS_INTERFACE)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.d(TAG, "JS: ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                if (message.message().contains("Uncaught")) {
                    fail(IllegalStateException(message.message()))
                }
                return super.onConsoleMessage(message)
            }
        }

        val html = context.assets
            .open("po_token.html")
            .bufferedReader()
            .use { it.readText() }
            .replaceFirst(
                "</script>",
                "\n$JS_INTERFACE.downloadAndRunBotguard()</script>"
            )

        webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
    }

    @JavascriptInterface
    fun downloadAndRunBotguard() {
        scope.launch {
            try {
                val challenge = postJson(CREATE_URL, JsonArray(listOf(JsonPrimitive(REQUEST_KEY))).toString())
                val challengeData = parseChallengeData(challenge)

                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript(
                        """try {
                            data = $challengeData;
                            runBotGuard(data).then(function (result) {
                                this.webPoSignalOutput = result.webPoSignalOutput;
                                $JS_INTERFACE.onRunBotguardResult(result.botguardResponse);
                            }, function (error) {
                                $JS_INTERFACE.onJsInitializationError(String(error));
                            });
                        } catch (error) {
                            $JS_INTERFACE.onJsInitializationError(String(error));
                        }""",
                        null
                    )
                }
            } catch (throwable: Throwable) {
                fail(throwable)
            }
        }
    }

    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        scope.launch {
            try {
                val body = JsonArray(
                    listOf(
                        JsonPrimitive(REQUEST_KEY),
                        JsonPrimitive(botguardResponse)
                    )
                ).toString()

                val rawIntegrityToken = postJson(GENERATE_URL, body)
                val tokenData = parseIntegrityTokenData(rawIntegrityToken)

                withContext(Dispatchers.Main) {
                    webView.evaluateJavascript(
                        "this.integrityToken = ${tokenData.first}"
                    ) {
                        ready.complete(Unit)
                    }
                }
            } catch (throwable: Throwable) {
                fail(throwable)
            }
        }
    }

    @JavascriptInterface
    fun onJsInitializationError(error: String) {
        fail(IllegalStateException(error))
    }

    @JavascriptInterface
    fun onObtainPoTokenResult(identifier: String, poTokenU8: String) {
        val token = runCatching { u8ToBase64(poTokenU8) }.getOrNull()
        val deferred = pending.remove(identifier)

        if (token == null) {
            deferred?.completeExceptionally(IllegalStateException("PO token inválido"))
        } else {
            deferred?.complete(token)
        }
    }

    @JavascriptInterface
    fun onObtainPoTokenError(identifier: String, error: String) {
        pending.remove(identifier)?.completeExceptionally(IllegalStateException(error))
    }

    suspend fun generate(identifier: String): String {
        withTimeout(15_000) { ready.await() }

        val deferred = CompletableDeferred<String>()
        pending[identifier] = deferred

        val identifierLiteral = JsonPrimitive(identifier).toString()
        val identifierU8 = "new Uint8Array([" +
            identifier.toByteArray(Charsets.UTF_8)
                .joinToString(",") { (it.toInt() and 0xFF).toString() } +
            "])"

        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                """try {
                    var poTokenU8 = obtainPoToken(webPoSignalOutput, integrityToken, $identifierU8);
                    var poTokenU8String = "";
                    for (var i = 0; i < poTokenU8.length; i++) {
                        if (i != 0) poTokenU8String += ",";
                        poTokenU8String += poTokenU8[i];
                    }
                    $JS_INTERFACE.onObtainPoTokenResult($identifierLiteral, poTokenU8String);
                } catch (error) {
                    $JS_INTERFACE.onObtainPoTokenError($identifierLiteral, String(error));
                }""",
                null
            )
        }

        return withTimeout(10_000) { deferred.await() }
    }

    fun close() {
        if (closed) return
        closed = true
        webView.loadUrl("about:blank")
        webView.onPause()
        webView.removeAllViews()
        webView.destroy()
    }

    private fun fail(throwable: Throwable) {
        if (!ready.isCompleted) {
            ready.completeExceptionally(throwable)
        }
        pending.values.forEach { it.completeExceptionally(throwable) }
        pending.clear()
    }

    private fun postJson(url: String, body: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json+protobuf")
            connection.setRequestProperty("x-goog-api-key", API_KEY)
            connection.setRequestProperty("x-user-agent", "grpc-web-javascript/0.1")

            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("BotGuard HTTP $code")
            }

            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Convierte la respuesta de `Create` (posiblemente ofuscada) al objeto JSON que
     * espera `runBotGuard()` en po_token.html.
     */
    private fun parseChallengeData(raw: String): String {
        val root = Json.parseToJsonElement(raw).jsonArray

        val challenge: JsonArray = run {
            val second = root.getOrNull(1)
            if (root.size > 1 && second is JsonPrimitive && second.isString) {
                Json.parseToJsonElement(descramble(second.content)).jsonArray
            } else if (root.size == 1 && root[0] is JsonPrimitive && (root[0] as JsonPrimitive).isString) {
                Json.parseToJsonElement(descramble((root[0] as JsonPrimitive).content)).jsonArray
            } else if (root.getOrNull(0) is JsonArray) {
                root[0].jsonArray
            } else {
                root
            }
        }


        fun text(index: Int): String? =
            (challenge.getOrNull(index) as? JsonPrimitive)?.contentOrNull

        fun nestedText(index: Int): String? =
            (challenge.getOrNull(index) as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.firstOrNull()

        val interpreterJavaScript = nestedText(1)
        val interpreterUrl = nestedText(2)

        return buildJsonObject {
            put("messageId", text(0) ?: "")
            putJsonObject("interpreterJavascript") {
                put("privateDoNotAccessOrElseSafeScriptWrappedValue", interpreterJavaScript)
                put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", interpreterUrl)
            }
            put("interpreterHash", text(3) ?: "")
            put("program", text(4) ?: "")
            put("globalName", text(5) ?: "")
            put("clientExperimentsStateBlob", text(7) ?: "")
        }.toString()
    }

    private fun parseIntegrityTokenData(raw: String): Pair<String, Long> {
        val array = Json.parseToJsonElement(raw).jsonArray
        val token = array[0].jsonPrimitive.content
        val expiresInSeconds = array.getOrNull(1)?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

        val bytes = decodeYouTubeBase64(token)
        val literal = "new Uint8Array([" +
            bytes.joinToString(",") { (it.toInt() and 0xFF).toString() } +
            "])"

        return literal to expiresInSeconds
    }

    private fun u8ToBase64(poTokenU8: String): String {
        val bytes = poTokenU8
            .split(",")
            .filter { it.isNotBlank() }
            .map { it.trim().toInt().toByte() }
            .toByteArray()

        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private fun decodeYouTubeBase64(value: String): ByteArray {
        val normalized = value
            .replace('-', '+')
            .replace('_', '/')
            .replace('.', '=')

        return Base64.decode(normalized, Base64.DEFAULT)
    }

    private fun descramble(scrambled: String): String {
        val bytes = decodeYouTubeBase64(scrambled)
        return bytes.map { (it + 97).toByte() }.toByteArray().toString(Charsets.UTF_8)
    }
}
