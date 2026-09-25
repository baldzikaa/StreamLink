package win.baldzika.streamlink.source

import com.sun.net.httpserver.HttpServer
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import win.baldzika.streamlink.event.StreamEvent
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

fun freePort(): Int = ServerSocket(0).use { it.localPort }

fun testScheduler(): ScheduledExecutorService = Executors.newScheduledThreadPool(2) { Thread(it).apply { isDaemon = true } }

val quietLog: Logger = Logger.getLogger("streamlink-test").apply { useParentHandlers = false }

fun <T> LinkedBlockingQueue<T>.next(seconds: Long = 5): T =
    poll(seconds, TimeUnit.SECONDS) ?: throw AssertionError("nothing arrived within ${seconds}s")

class Events {
    val queue = LinkedBlockingQueue<StreamEvent>()
    val emit: (StreamEvent) -> Unit = { queue.add(it) }
}

/**
 * websocket server that hands every message and connection to the test.
 */
class FakeSocketServer : WebSocketServer(InetSocketAddress("127.0.0.1", freePort())) {

    val received = LinkedBlockingQueue<String>()
    val connections = LinkedBlockingQueue<WebSocket>()

    val uri: URI get() = URI.create("ws://127.0.0.1:$port")

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        connections.add(conn)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        received.add(message)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
    }

    override fun onStart() {
    }

    fun startAndWait() {
        isReuseAddr = true
        start()
        repeat(50) {
            runCatching { java.net.Socket("127.0.0.1", port).close() }.onSuccess { return }
            Thread.sleep(20)
        }
    }
}

fun httpServer(): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.start() }

val HttpServer.base: String get() = "http://127.0.0.1:${address.port}"
