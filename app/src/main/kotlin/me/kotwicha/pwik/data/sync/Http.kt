package me.kotwicha.pwik.data.sync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HttpResponse(
    val code: Int,
    val body: ByteArray,
    val etag: String?,
    val lastModified: String?,
    val finalUrl: String,
) {
    val notModified: Boolean get() = code == HttpURLConnection.HTTP_NOT_MODIFIED
}

class HttpException(val code: Int, url: String) : IOException("HTTP $code: $url")

class Http(private val userAgent: String) {

    suspend fun get(
        url: String,
        etag: String? = null,
        lastModified: String? = null,
        maxBytes: Int = 40 * 1024 * 1024,
    ): HttpResponse = withContext(Dispatchers.IO) {
        var current = url
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 45_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "text/html,application/pdf,*/*")
                setRequestProperty("Accept-Language", "pl,en;q=0.5")
                etag?.let { setRequestProperty("If-None-Match", it) }
                lastModified?.let { setRequestProperty("If-Modified-Since", it) }
            }
            try {
                val code = conn.responseCode
                if (code in 300..399 && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
                    val location = conn.getHeaderField("Location") ?: throw HttpException(code, current)
                    current = URL(URL(current), location).toString()
                    return@repeat
                }
                if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    return@withContext HttpResponse(code, ByteArray(0), etag, lastModified, current)
                }
                if (code !in 200..299) throw HttpException(code, current)
                val body = conn.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) throw IOException("plik jest zbyt duży")
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
                return@withContext HttpResponse(
                    code, body, conn.getHeaderField("ETag"), conn.getHeaderField("Last-Modified"), current,
                )
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("zbyt wiele przekierowań: $url")
    }
}
