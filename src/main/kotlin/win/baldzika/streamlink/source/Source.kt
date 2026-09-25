package win.baldzika.streamlink.source

import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

enum class Status {
    CONNECTING,
    LIVE,
    OFFLINE,
    RETRYING,
    STOPPED,
}

/**
 * one connection to one channel. reconnects by itself until stopped.
 */
abstract class Source(
    val platform: Platform,
    val channel: String,
    protected val emit: (StreamEvent) -> Unit,
    protected val scheduler: ScheduledExecutorService,
    protected val log: Logger,
) {

    @Volatile
    var status = Status.STOPPED
        private set

    @Volatile
    var detail = ""
        private set

    private val backoff = Backoff()
    private val lock = Any()
    private var session = 0
    private var running = false

    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
        }
        scheduler.execute(::attempt)
    }

    fun stop() {
        synchronized(lock) {
            running = false
            session++
        }
        status = Status.STOPPED
        detail = ""
        runCatching { close() }
    }

    /**
     * opens the connection. call [connected] once events are flowing and [failed] if anything goes wrong.
     */
    protected abstract fun open(session: Int)

    protected abstract fun close()

    protected fun current(session: Int) = synchronized(lock) { running && session == this.session }

    protected fun connected(session: Int, detail: String = "") {
        if (!current(session)) return
        backoff.reset()
        status = Status.LIVE
        this.detail = detail
        log.info("${platform.display} $channel: connected")
    }

    /**
     * drops the connection and tries again later. offline means the channel isn't live, which is normal.
     */
    protected fun failed(session: Int, reason: String, offline: Boolean = false, retry: Duration? = null) {
        val next = synchronized(lock) {
            if (!running || session != this.session) return
            this.session++
            this.session
        }
        runCatching { close() }
        val delay = retry ?: if (offline) 2.minutes else backoff.next()
        status = if (offline) Status.OFFLINE else Status.RETRYING
        detail = reason
        if (!offline) {
            log.warning("${platform.display} $channel: $reason, retrying in ${delay.inWholeSeconds}s")
        }
        scheduler.schedule({ if (current(next)) attempt() }, delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    }

    private fun attempt() {
        val id = synchronized(lock) { session }
        status = Status.CONNECTING
        try {
            open(id)
        } catch (e: Exception) {
            failed(id, e.message ?: e.javaClass.simpleName)
        }
    }
}
