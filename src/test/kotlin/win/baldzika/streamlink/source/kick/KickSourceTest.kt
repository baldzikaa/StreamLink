package win.baldzika.streamlink.source.kick

import com.google.gson.JsonParser
import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent
import win.baldzika.streamlink.source.Events
import win.baldzika.streamlink.source.FakeSocketServer
import win.baldzika.streamlink.source.Status
import win.baldzika.streamlink.source.base
import win.baldzika.streamlink.source.httpServer
import win.baldzika.streamlink.source.next
import win.baldzika.streamlink.source.quietLog
import win.baldzika.streamlink.source.testScheduler
import java.net.http.HttpClient
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KickSourceTest {

    private val pusher = FakeSocketServer().apply { startAndWait() }
    private val api = httpServer()
    private val http = HttpClient.newHttpClient()
    private val scheduler = testScheduler()
    private val events = Events()

    @AfterTest
    fun tearDown() {
        pusher.stop(1000)
        api.stop(0)
        scheduler.shutdownNow()
        http.close()
    }

    private fun subscribedTo(): Set<String> = List(3) {
        JsonParser.parseString(pusher.received.next()).asJsonObject.getAsJsonObject("data").get("channel").asString
    }.toSet()

    @Test
    fun `looks up the chatroom subscribes and reads events`() {
        api.createContext("/api/v2/channels/streamer") { exchange ->
            val body = """{"id":668,"slug":"streamer","chatroom":{"id":4242}}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        val source = KickSource("Streamer", events.emit, scheduler, quietLog, http, null, "${api.base}/api/v2/channels/", pusher.uri)
        source.start()

        val conn = pusher.connections.next()
        conn.send("""{"event":"pusher:connection_established","data":"{\"socket_id\":\"1.1\"}"}""")
        assertEquals(setOf("chatrooms.4242.v2", "channel.668", "channel_668"), subscribedTo())

        conn.send("""{"event":"pusher_internal:subscription_succeeded","data":"{}","channel":"chatrooms.4242.v2"}""")
        conn.send("""{"event":"App\\Events\\ChatMessageEvent","data":"{\"content\":\"hello\",\"sender\":{\"username\":\"Viewer\"}}","channel":"chatrooms.4242.v2"}""")
        assertEquals(StreamEvent.Chat(Platform.KICK, "Viewer", "hello"), events.queue.next())
        assertEquals(Status.LIVE, source.status)

        conn.send("""{"event":"pusher:ping","data":{}}""")
        assertEquals("""{"event":"pusher:pong","data":{}}""", pusher.received.next())
        source.stop()
    }

    @Test
    fun `falls back to the configured chatroom when the lookup is blocked`() {
        api.createContext("/api/v2/channels/streamer") { exchange ->
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
        }
        val source = KickSource("streamer", events.emit, scheduler, quietLog, http, 777, "${api.base}/api/v2/channels/", pusher.uri)
        source.start()

        pusher.connections.next().send("""{"event":"pusher:connection_established","data":"{}"}""")
        val subscribe = JsonParser.parseString(pusher.received.next()).asJsonObject
        assertEquals("chatrooms.777.v2", subscribe.getAsJsonObject("data").get("channel").asString)
        source.stop()
    }

    @Test
    fun `explains a blocked lookup without a fallback`() {
        api.createContext("/api/v2/channels/streamer") { exchange ->
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
        }
        val source = KickSource("streamer", events.emit, scheduler, quietLog, http, null, "${api.base}/api/v2/channels/", pusher.uri)
        source.start()

        repeat(50) { if (source.status == Status.RETRYING) return@repeat; Thread.sleep(20) }
        assertEquals(Status.RETRYING, source.status)
        assertEquals("couldn't look up the channel (403), set kick-chatroom in the config", source.detail)
        source.stop()
    }
}
