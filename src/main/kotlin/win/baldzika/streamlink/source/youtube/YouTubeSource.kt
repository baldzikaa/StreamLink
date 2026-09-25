package win.baldzika.streamlink.source.youtube

import com.google.gson.JsonObject
import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent
import win.baldzika.streamlink.source.BROWSER_AGENT
import win.baldzika.streamlink.source.Source
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * polls live chat the same way the youtube website does, so no api key or quota is needed.
 */
class YouTubeSource(
    channel: String,
    emit: (StreamEvent) -> Unit,
    scheduler: ScheduledExecutorService,
    log: Logger,
    private val http: HttpClient,
    private val base: String = "https://www.youtube.com",
) : Source(Platform.YOUTUBE, channel, emit, scheduler, log) {

    @Volatile
    private var poll: ScheduledFuture<*>? = null

    override fun open(session: Int) {
        val video = YouTubePages.directVideo(channel) ?: YouTubePages.liveVideo(get(rebase(YouTubePages.livePage(channel))))
        if (video == null) {
            failed(session, "not live", offline = true)
            return
        }
        val chat = YouTubePages.chat(get("$base/live_chat?is_popout=1&v=$video"))
        if (chat == null) {
            failed(session, "stream $video has no live chat", offline = true)
            return
        }
        connected(session, video)
        schedule(session, chat, chat.continuation, first = true, delay = 0)
    }

    override fun close() {
        poll?.cancel(false)
        poll = null
    }

    private fun schedule(session: Int, chat: YouTubePages.Chat, continuation: String, first: Boolean, delay: Long) {
        if (!current(session)) return
        poll = scheduler.schedule({ fetch(session, chat, continuation, first) }, delay, TimeUnit.MILLISECONDS)
    }

    private fun fetch(session: Int, chat: YouTubePages.Chat, continuation: String, first: Boolean) {
        if (!current(session)) return
        val batch = try {
            YouTubeChat.parse(post(chat, continuation))
        } catch (e: Exception) {
            failed(session, "chat request failed: ${e.message}")
            return
        }
        val next = batch.continuation
        if (next == null) {
            failed(session, "stream ended", offline = true)
            return
        }
        // the first batch is backlog from before we joined, don't replay it
        if (!first) batch.events.forEach(emit)
        schedule(session, chat, next, first = false, delay = batch.waitMillis.coerceIn(1_000, 10_000))
    }

    private fun get(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", BROWSER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "SOCS=CAI")
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "youtube answered ${response.statusCode()}" }
        return response.body()
    }

    private fun post(chat: YouTubePages.Chat, continuation: String): String {
        val client = JsonObject().apply {
            addProperty("clientName", "WEB")
            addProperty("clientVersion", chat.clientVersion)
        }
        val body = JsonObject().apply {
            add("context", JsonObject().apply { add("client", client) })
            addProperty("continuation", continuation)
        }
        val request = HttpRequest.newBuilder(URI.create("$base/youtubei/v1/live_chat/get_live_chat?key=${chat.apiKey}&prettyPrint=false"))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .header("User-Agent", BROWSER_AGENT)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "youtube answered ${response.statusCode()}" }
        return response.body()
    }

    private fun rebase(url: String) = if (base == "https://www.youtube.com") url else url.replace("https://www.youtube.com", base)
}
