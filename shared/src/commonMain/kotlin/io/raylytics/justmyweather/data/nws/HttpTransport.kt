package io.raylytics.justmyweather.data.nws

/** One GET worth of HTTP, decoupled from the client so retry/parse logic can
 * be tested with a fake transport. Mirrors the TS client's injectable fetch. */
fun interface HttpTransport {
    suspend fun get(url: String, headers: Map<String, String>): HttpResult
}

/** The minimal slice of a response [NwsClient] needs: status, body, Retry-After. */
data class HttpResult(
    val status: Int,
    val body: String,
    val retryAfter: String?,
)
