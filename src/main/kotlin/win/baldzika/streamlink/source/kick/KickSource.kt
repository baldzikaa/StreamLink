package win.baldzika.streamlink.source.kick

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent
import win.baldzika.streamlink.source.BROWSER_AGENT
import win.baldzika.streamlink.source.WebSocketSource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.ScheduledExecutorService
import java.util.logging.Logger

class KickSource(
    channel: String,
    emit: (StreamEvent) -> Unit,
    scheduler: ScheduledExecutorService,
    log: Logger,
    http: HttpClient,
    private val chatroomOverride: Long? = null,
    private val api: String = "https://kick.com/api/v2/channels/",
    private val pusher: URI = URI.create("wss://ws-us2.pusher.com/app/$PUSHER_KEY?protocol=7&client=js&version=8.4.0&flash=false"),
) : WebSocketSource(Platform.KICK, channel.lowercase(), emit, scheduler, log, http) {

    private data class Ids(val channel: Long?, val chatroom: Long)

    @Volatile
    private var ids: Ids? = null

    override fun open(session: Int) {
        ids = ids ?: lookup()
        super.open(session)
    }

    override fun uri(session: Int) = pusher

    override fun onOpen(session: Int, socket: WebSocket) {
    }

    override fun onMessage(session: Int, socket: WebSocket, text: String) {
        val frame = KickEvents.frame(text) ?: return
        when (frame.event) {
            "pusher:connection_established" -> subscribe(socket)
            "pusher:ping" -> send(socket, """{"event":"pusher:pong","data":{}}""")
            "pusher_internal:subscription_succeeded" -> if (frame.channel?.startsWith("chatrooms.") == true) connected(session)
            "pusher:error" -> failed(session, "pusher error: ${frame.data}")
            else -> KickEvents.from(frame)?.let(emit)
        }
    }

    private fun subscribe(socket: WebSocket) {
        val ids = ids ?: return
        val channels = buildList {
            add("chatrooms.${ids.chatroom}.v2")
            ids.channel?.let {
                add("channel.$it")
                add("channel_$it")
            }
        }
        for (name in channels) {
            val data = JsonObject().apply {
                addProperty("auth", "")
                addProperty("channel", name)
            }
            val frame = JsonObject().apply {
                addProperty("event", "pusher:subscribe")
                add("data", data)
            }
            send(socket, frame.toString())
        }
    }

    private fun lookup(): Ids {
        val request = HttpRequest.newBuilder(URI.create(api + channel))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
            .header("User-Agent", BROWSER_AGENT)
            .build()
        val response = runCatching { http.send(request, HttpResponse.BodyHandlers.ofString()) }.getOrNull()
        if (response == null || response.statusCode() != 200) {
            chatroomOverride?.let { return Ids(null, it) }
            val code = response?.statusCode()?.toString() ?: "no response"
            throw IllegalStateException("couldn't look up the channel ($code), set kick-chatroom in the config")
        }
        val json = JsonParser.parseString(response.body()).asJsonObject
        val chatroom = chatroomOverride ?: json.getAsJsonObject("chatroom").get("id").asLong
        return Ids(json.get("id").asLong, chatroom)
    }

    companion object {
        const val PUSHER_KEY = "32cbd69e4b950bf97679"
    }
}
