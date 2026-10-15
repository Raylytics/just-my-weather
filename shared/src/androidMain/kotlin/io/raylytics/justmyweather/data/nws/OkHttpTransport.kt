package io.raylytics.justmyweather.data.nws

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Production transport over OkHttp with NWS's recommended 10s timeout. */
class OkHttpTransport(
    private val client: OkHttpClient = defaultClient,
) : HttpTransport {
    override suspend fun get(url: String, headers: Map<String, String>): HttpResult =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url).get()
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                HttpResult(
                    status = resp.code,
                    body = resp.body?.string() ?: "",
                    retryAfter = resp.header("Retry-After"),
                )
            }
        }

    companion object {
        /**
         * Deliberately no logging interceptor. Every request URL holds the
         * place's coordinates, and a bug report attaches this process's recent
         * logcat — the privacy policy promises that mail carries no location.
         * Debug logging of requests would quietly break that promise.
         */
        val defaultClient: OkHttpClient =
            OkHttpClient.Builder()
                .callTimeout(10, TimeUnit.SECONDS)
                .build()
    }
}
