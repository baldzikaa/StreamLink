package win.baldzika.streamlink

import org.bukkit.Bukkit
import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent
import win.baldzika.streamlink.rule.Rules
import win.baldzika.streamlink.source.Source
import win.baldzika.streamlink.source.kick.KickSource
import win.baldzika.streamlink.source.tiktok.TikTokSource
import win.baldzika.streamlink.source.twitch.TwitchSource
import win.baldzika.streamlink.source.youtube.YouTubeSource
import java.net.http.HttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.logging.Logger

/**
 * owns every connection and sends their events through the rules.
 */
class Streams(
    private val http: HttpClient,
    private val scheduler: ScheduledExecutorService,
    private val log: Logger,
    private val runner: ActionRunner,
) {

    data class Connection(val link: Link, val source: Source)

    @Volatile
    var settings: Settings = Settings(emptyList(), emptyList(), false, Settings.DEFAULT_RELAY, true, 0, 0, "")
        private set

    @Volatile
    private var rules = Rules(emptyList())

    @Volatile
    var connections: List<Connection> = emptyList()
        private set

    private val paused = ConcurrentHashMap.newKeySet<String>()

    fun start(settings: Settings) {
        stop()
        this.settings = settings
        rules = Rules(settings.rules)
        connections = settings.links.flatMap { link ->
            link.accounts.map { (platform, account) -> Connection(link, source(link, platform, account)) }
        }
        connections.forEach { it.source.start() }
    }

    fun stop() {
        connections.forEach { it.source.stop() }
        connections = emptyList()
    }

    fun link(player: String): Link? = settings.links.firstOrNull { it.player.equals(player, ignoreCase = true) }

    fun togglePause(player: String): Boolean {
        val key = player.lowercase()
        return if (paused.remove(key)) false else paused.add(key)
    }

    fun handle(player: String, event: StreamEvent) {
        val settings = settings
        val values = event.values() + ("player" to player) + ("color" to event.platform.color)
        if (event is StreamEvent.Chat && settings.relayChat) {
            Bukkit.getPlayerExact(player)?.sendMessage(runner.text(settings.relayFormat, values))
        }
        if (player.lowercase() in paused) return
        for (rule in rules.fire(player, event)) {
            runner.run(player, rule, values, settings)
        }
    }

    private fun source(link: Link, platform: Platform, account: String): Source {
        val emit: (StreamEvent) -> Unit = { event ->
            runCatching { handle(link.player, event) }
                .onFailure { log.warning("couldn't handle ${event.type.key} from ${platform.display}: ${it.message}") }
        }
        return when (platform) {
            Platform.TWITCH -> TwitchSource(account, emit, scheduler, log, http)
            Platform.KICK -> KickSource(account, emit, scheduler, log, http, link.kickChatroom)
            Platform.YOUTUBE -> YouTubeSource(account, emit, scheduler, log, http)
            Platform.TIKTOK -> TikTokSource(account, emit, scheduler, log, settings.tiktokApiKey)
        }
    }
}
