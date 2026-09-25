package win.baldzika.streamlink.source

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class Backoff(private val first: Duration = 5.seconds, private val max: Duration = 5.minutes) {

    private var next = first

    fun next(): Duration = next.also { next = minOf(next * 2, max) }

    fun reset() {
        next = first
    }
}
