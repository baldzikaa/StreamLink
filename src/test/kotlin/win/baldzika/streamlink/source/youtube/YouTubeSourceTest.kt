package win.baldzika.streamlink.source.youtube

import com.sun.net.httpserver.HttpExchange
import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent
import win.baldzika.streamlink.source.Events
import win.baldzika.streamlink.source.Status
import win.baldzika.streamlink.source.base
import win.baldzika.streamlink.source.httpServer
import win.baldzika.streamlink.source.next
import win.baldzika.streamlink.source.quietLog
import win.baldzika.streamlink.source.testScheduler
import java.net.http.HttpClient
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YouTubeSourceTest {

    private val server = httpServer()
    private val http = HttpClient.newHttpClient()
    private val scheduler = testScheduler()
    private val events = Events()
    private val posts = LinkedBlockingQueue<String>()

    @AfterTest
    fun tearDown() {
        server.stop(0)
        scheduler.shutdownNow()
        http.close()
    }

    private fun resource(name: String) = javaClass.getResource("/youtube/$name")!!.readText()

    private fun HttpExchange.reply(body: String) {
        val bytes = body.toByteArray()
        sendResponseHeaders(200, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun waitFor(status: Status, source: YouTubeSource) {
        repeat(100) { if (source.status == status) return; Thread.sleep(20) }
        assertEquals(status, source.status)
    }

    @Test
    fun `skips backlog then emits new chat until the stream ends`() {
        server.createContext("/@streamer/live") { it.reply(resource("live.html")) }
        server.createContext("/live_chat") { exchange ->
            assertEquals("is_popout=1&v=abcdefghijk", exchange.requestURI.rawQuery)
            exchange.reply(resource("popout.html"))
        }
        var calls = 0
        server.createContext("/youtubei/v1/live_chat/get_live_chat") { exchange ->
            posts.add(exchange.requestBody.readAllBytes().decodeToString())
            calls++
            exchange.reply(
                when (calls) {
                    1, 2 -> resource("chat.json").replace("\"timeoutMs\": 10000", "\"timeoutMs\": 1")
                    else -> resource("ended.json")
                },
            )
        }
        val source = YouTubeSource("@streamer", events.emit, scheduler, quietLog, http, server.base)
        source.start()

        assertTrue("\"continuation\":\"first-page-token\"" in posts.next())
        assertTrue("\"clientVersion\":\"2.20260924.00.00\"" in posts.next())
        assertEquals(StreamEvent.Chat(Platform.YOUTUBE, "FirstViewer", "gg :fire: !tnt"), events.queue.next())
        repeat(5) { events.queue.next() }

        waitFor(Status.OFFLINE, source)
        assertEquals("stream ended", source.detail)
        assertEquals(0, events.queue.size)
        source.stop()
    }

    @Test
    fun `offline channel waits quietly`() {
        server.createContext("/@streamer/live") { it.reply(resource("offline.html")) }
        val source = YouTubeSource("@streamer", events.emit, scheduler, quietLog, http, server.base)
        source.start()

        waitFor(Status.OFFLINE, source)
        assertEquals("not live", source.detail)
        source.stop()
    }
}
