package win.baldzika.streamlink.source.twitch

import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent
import win.baldzika.streamlink.source.WebSocketSource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.ScheduledExecutorService
import java.util.logging.Logger
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * reads chat anonymously, no token needed. follows need oauth on twitch so they aren't covered.
 */
class TwitchSource(
    channel: String,
    emit: (StreamEvent) -> Unit,
    scheduler: ScheduledExecutorService,
    log: Logger,
    http: HttpClient,
    private val url: URI = URI.create("wss://irc-ws.chat.twitch.tv:443"),
) : WebSocketSource(Platform.TWITCH, channel.lowercase().removePrefix("#"), emit, scheduler, log, http) {

    override fun uri(session: Int) = url

    override fun onOpen(session: Int, socket: WebSocket) {
        send(socket, "CAP REQ :twitch.tv/tags twitch.tv/commands")
        send(socket, "PASS SCHMOOPIIE")
        send(socket, "NICK justinfan${Random.nextInt(10_000, 99_999)}")
        send(socket, "JOIN #$channel")
    }

    override fun onMessage(session: Int, socket: WebSocket, text: String) {
        for (line in text.split("\r\n")) {
            val message = IrcMessage.parse(line) ?: continue
            when (message.command) {
                "PING" -> send(socket, "PONG :${message.trailing}")
                "ROOMSTATE" -> connected(session)
                "RECONNECT" -> failed(session, "twitch asked us to reconnect", retry = 1.seconds)
                "NOTICE" -> notice(session, message)
                else -> TwitchEvents.from(message)?.let(emit)
            }
        }
    }

    private fun notice(session: Int, message: IrcMessage) {
        when (message.tags["msg-id"]) {
            "msg_channel_suspended", "msg_banned" -> failed(session, message.trailing, offline = true)
        }
    }
}
