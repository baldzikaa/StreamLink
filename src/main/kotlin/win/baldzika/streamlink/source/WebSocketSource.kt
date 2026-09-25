package win.baldzika.streamlink.source

import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.time.Duration.Companion.minutes

abstract class WebSocketSource(
    platform: Platform,
    channel: String,
    emit: (StreamEvent) -> Unit,
    scheduler: ScheduledExecutorService,
    log: Logger,
    protected val http: HttpClient,
) : Source(platform, channel, emit, scheduler, log) {

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var lastMessage = 0L

    @Volatile
    private var idleCheck: ScheduledFuture<*>? = null

    protected abstract fun uri(session: Int): URI

    protected abstract fun onOpen(session: Int, socket: WebSocket)

    protected abstract fun onMessage(session: Int, socket: WebSocket, text: String)

    override fun open(session: Int) {
        http.newWebSocketBuilder()
            .buildAsync(uri(session), Listener(session))
            .whenComplete { ws, error ->
                if (error != null) {
                    failed(session, "couldn't connect: ${error.cause?.message ?: error.message}")
                } else if (!current(session)) {
                    ws.abort()
                } else {
                    socket = ws
                }
            }
    }

    override fun close() {
        idleCheck?.cancel(false)
        socket?.abort()
        socket = null
    }

    protected fun send(socket: WebSocket, text: String) {
        socket.sendText(text, true)
    }

    private inner class Listener(private val session: Int) : WebSocket.Listener {

        private val buffer = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            lastMessage = System.nanoTime()
            // both twitch and kick talk at least every few minutes, silence means the socket died quietly
            idleCheck = scheduler.scheduleAtFixedRate({
                if (System.nanoTime() - lastMessage > 6.minutes.inWholeNanoseconds) {
                    failed(session, "no messages for 6 minutes")
                }
            }, 1, 1, TimeUnit.MINUTES)
            onOpen(session, webSocket)
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                val text = buffer.toString()
                buffer.setLength(0)
                lastMessage = System.nanoTime()
                if (current(session)) {
                    runCatching { onMessage(session, webSocket, text) }
                        .onFailure { log.warning("${platform.display} $channel: couldn't read a message: ${it.message}") }
                }
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            failed(session, "closed ($statusCode${if (reason.isBlank()) "" else " $reason"})")
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            failed(session, error.message ?: error.javaClass.simpleName)
        }
    }
}
