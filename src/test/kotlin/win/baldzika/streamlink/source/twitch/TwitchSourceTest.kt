package win.baldzika.streamlink.source.twitch

import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent
import win.baldzika.streamlink.source.Events
import win.baldzika.streamlink.source.FakeSocketServer
import win.baldzika.streamlink.source.Status
import win.baldzika.streamlink.source.next
import win.baldzika.streamlink.source.quietLog
import win.baldzika.streamlink.source.testScheduler
import java.net.http.HttpClient
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TwitchSourceTest {

    private val server = FakeSocketServer().apply { startAndWait() }
    private val http = HttpClient.newHttpClient()
    private val scheduler = testScheduler()
    private val events = Events()
    private val source = TwitchSource("#SomeStreamer", events.emit, scheduler, quietLog, http, server.uri)

    @AfterTest
    fun tearDown() {
        source.stop()
        server.stop(1000)
        scheduler.shutdownNow()
        http.close()
    }

    private fun handshake(): org.java_websocket.WebSocket {
        val conn = server.connections.next()
        val login = List(4) { server.received.next() }
        assertEquals("CAP REQ :twitch.tv/tags twitch.tv/commands", login[0])
        assertTrue(login[2].startsWith("NICK justinfan"))
        assertEquals("JOIN #somestreamer", login[3])
        return conn
    }

    @Test
    fun `logs in anonymously and reads events`() {
        source.start()
        val conn = handshake()
        assertEquals(Status.CONNECTING, source.status)

        conn.send("@emote-only=0 :tmi.twitch.tv ROOMSTATE #somestreamer\r\n")
        conn.send(
            "@display-name=Viewer :viewer!viewer@viewer.tmi.twitch.tv PRIVMSG #somestreamer :!tnt\r\n" +
                "@display-name=Viewer;msg-id=resub;msg-param-cumulative-months=2 :tmi.twitch.tv USERNOTICE #somestreamer\r\n",
        )

        assertEquals(StreamEvent.Chat(Platform.TWITCH, "Viewer", "!tnt"), events.queue.next())
        assertEquals(StreamEvent.Sub(Platform.TWITCH, "Viewer", 2), events.queue.next())
        assertEquals(Status.LIVE, source.status)
    }

    @Test
    fun `answers pings`() {
        source.start()
        val conn = handshake()
        conn.send("PING :tmi.twitch.tv\r\n")
        assertEquals("PONG :tmi.twitch.tv", server.received.next())
    }

    @Test
    fun `reconnects when twitch asks`() {
        source.start()
        val first = handshake()
        first.send("RECONNECT\r\n")

        handshake()
        assertEquals(Status.CONNECTING, source.status)
    }

    @Test
    fun `stop closes the socket and ignores late messages`() {
        source.start()
        val conn = handshake()
        source.stop()
        runCatching { conn.send("@display-name=Late :late!late@late.tmi.twitch.tv PRIVMSG #somestreamer :hi\r\n") }

        repeat(50) { if (conn.isClosed) return@repeat; Thread.sleep(20) }
        assertTrue(conn.isClosed)
        assertEquals(Status.STOPPED, source.status)
        assertTrue(events.queue.isEmpty())
    }
}
