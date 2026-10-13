package io.raylytics.justmyweather.data.nws

import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.IOException
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLCache
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS twin of OkHttpTransport: one GET over NSURLSession, with the same 10 s
 * budget. A failure to reach the server is an [IOException] — okio's, which on
 * Android IS java.io's — so the shared code's "transient network, try again"
 * paths treat both platforms alike.
 *
 * Deliberately no request logging, for the reason OkHttpTransport gives: every
 * URL carries the place's coordinates, and a bug report attaches the app log.
 */
class UrlSessionTransport(private val session: NSURLSession) : HttpTransport {
    @OptIn(BetaInteropApi::class)
    override suspend fun get(url: String, headers: Map<String, String>): HttpResult =
        suspendCancellableCoroutine { continuation ->
            val nsUrl = NSURL.URLWithString(url) ?: return@suspendCancellableCoroutine continuation.resumeWithException(
                IOException("not a URL"),
            )
            val request =
                NSMutableURLRequest(uRL = nsUrl).apply {
                    setTimeoutInterval(TIMEOUT_SECONDS)
                    headers.forEach { (name, value) -> setValue(value, forHTTPHeaderField = name) }
                }
            val task =
                session.dataTaskWithRequest(request) { data, response, error ->
                    val http = response as? NSHTTPURLResponse
                    when {
                        error != null || http == null ->
                            continuation.resumeWithException(IOException(error?.localizedDescription ?: "no response"))
                        else ->
                            continuation.resume(
                                HttpResult(
                                    status = http.statusCode.toInt(),
                                    body = data?.let { NSString.create(it, NSUTF8StringEncoding)?.toString() }
                                        .orEmpty(),
                                    retryAfter = http.valueForHTTPHeaderField("Retry-After"),
                                ),
                            )
                    }
                }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }

    companion object {
        private const val TIMEOUT_SECONDS = 10.0

        /** For NWS: no cache at all — a tap on Refresh must reach the network,
         * and NWS's cache headers would otherwise swallow it. */
        fun uncached(): UrlSessionTransport =
            UrlSessionTransport(
                NSURLSession.sessionWithConfiguration(
                    NSURLSessionConfiguration.ephemeralSessionConfiguration.apply {
                        requestCachePolicy = NSURLRequestReloadIgnoringLocalCacheData
                        setURLCache(null)
                    },
                ),
            )

        /** For MET Norway: a small HTTP cache, since MET's terms ask a client
         * to honour Expires and revalidate with If-Modified-Since — which
         * NSURLSession does by itself once it has a cache to keep. */
        fun cached(): UrlSessionTransport =
            UrlSessionTransport(
                NSURLSession.sessionWithConfiguration(
                    NSURLSessionConfiguration.defaultSessionConfiguration.apply {
                        setURLCache(
                            NSURLCache(memoryCapacity = 0u, diskCapacity = ONE_MEGABYTE, diskPath = "metno-http"),
                        )
                    },
                ),
            )

        private const val ONE_MEGABYTE: ULong = 1_048_576uL
    }
}
