package win.baldzika.streamlink

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class StreamLinkPlugin : JavaPlugin() {

    lateinit var streams: Streams
        private set

    private lateinit var scheduler: ScheduledExecutorService
    private lateinit var http: HttpClient
    private lateinit var runner: ActionRunner

    override fun onEnable() {
        saveDefaultConfig()
        scheduler = Executors.newScheduledThreadPool(2, Thread.ofPlatform().name("StreamLink-", 0).daemon().factory())
        http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(scheduler)
            .build()
        runner = ActionRunner(this)
        streams = Streams(http, scheduler, logger, runner)
        reload()

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(StreamLinkCommand(this).build(), "stream events in game", listOf("sl"))
        }
    }

    override fun onDisable() {
        if (::streams.isInitialized) streams.stop()
        if (::scheduler.isInitialized) scheduler.shutdownNow()
        if (::http.isInitialized) http.close()
    }

    fun reload() {
        reloadConfig()
        val settings = Settings.load(config) { logger.warning(it) }
        settings.rules.forEach { rule ->
            runner.problems(rule).forEach { logger.warning("rule '${rule.name}': $it") }
        }
        streams.start(settings)
        logger.info("${settings.links.size} linked players, ${streams.connections.size} connections, ${settings.rules.size} rules")
    }
}
