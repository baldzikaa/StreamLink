package win.baldzika.streamlink.source.tiktok

import io.github.jwdeveloper.tiktok.TikTokLive
import io.github.jwdeveloper.tiktok.exceptions.TikTokLiveOfflineHostException
import io.github.jwdeveloper.tiktok.exceptions.TikTokLiveUnknownHostException
import io.github.jwdeveloper.tiktok.live.LiveClient
import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent
import win.baldzika.streamlink.source.Source
import java.util.concurrent.ScheduledExecutorService
import java.util.logging.Level
import java.util.logging.Logger

class TikTokSource(
    channel: String,
    emit: (StreamEvent) -> Unit,
    scheduler: ScheduledExecutorService,
    log: Logger,
    private val apiKey: String = "",
) : Source(Platform.TIKTOK, channel.removePrefix("@"), emit, scheduler, log) {

    @Volatile
    private var client: LiveClient? = null

    override fun open(session: Int) {
        val tiktok = Platform.TIKTOK
        client = TikTokLive.newClient(channel)
            .configure { settings ->
                settings.isPrintToConsole = false
                settings.logLevel = Level.OFF
                settings.isRetryOnConnectionFailure = false
                if (apiKey.isNotBlank()) settings.apiKey = apiKey
            }
            .onConnected { _, _ -> connected(session) }
            .onComment { _, event -> send(session, StreamEvent.Chat(tiktok, event.user.name, event.text)) }
            .onFollow { _, event -> send(session, StreamEvent.Follow(tiktok, event.user.name)) }
            .onShare { _, event -> send(session, StreamEvent.Share(tiktok, event.user.name)) }
            .onLike { _, event -> send(session, StreamEvent.Like(tiktok, event.user.name, event.likes)) }
            .onSubscribe { _, event -> send(session, StreamEvent.Sub(tiktok, event.user.name)) }
            // fires once per gift, or once at the end of a combo streak
            .onGift { _, event ->
                val gift = event.gift
                send(session, StreamEvent.Gift(tiktok, event.user.name, gift.name, maxOf(1, event.combo), gift.diamondCost))
            }
            .onLiveEnded { _, _ -> failed(session, "live ended", offline = true) }
            .onDisconnected { _, event -> failed(session, "disconnected: ${event.reason}") }
            .onError { _, event -> error(session, event.exception) }
            .build()
        client?.connectAsync()?.exceptionally { error ->
            error(session, error)
            null
        }
    }

    override fun close() {
        val current = client ?: return
        client = null
        runCatching { current.disconnect() }
    }

    private fun send(session: Int, event: StreamEvent) {
        if (current(session)) emit(event)
    }

    private fun error(session: Int, error: Throwable) {
        val cause = generateSequence(error) { it.cause }.firstOrNull {
            it is TikTokLiveOfflineHostException || it is TikTokLiveUnknownHostException
        }
        when (cause) {
            is TikTokLiveOfflineHostException -> failed(session, "not live", offline = true)
            is TikTokLiveUnknownHostException -> failed(session, "no tiktok user called $channel", offline = true)
            else -> failed(session, error.message ?: error.javaClass.simpleName)
        }
    }
}
