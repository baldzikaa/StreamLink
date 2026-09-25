package win.baldzika.streamlink.rule

import win.baldzika.streamlink.event.StreamEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * picks which rules fire for an event. cooldowns are per rule and per linked player.
 */
class Rules(
    val all: List<Rule>,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::nanoTime,
) {

    private val lastFired = ConcurrentHashMap<String, Long>()

    fun fire(player: String, event: StreamEvent): List<Rule> = all.filter { rule ->
        rule.matches(event) && rolled(rule) && offCooldown(player, rule)
    }

    private fun rolled(rule: Rule) = rule.chance >= 1.0 || random.nextDouble() < rule.chance

    private fun offCooldown(player: String, rule: Rule): Boolean {
        if (!rule.cooldown.isPositive()) return true
        val now = clock()
        val key = "${player.lowercase()}:${rule.name}"
        var allowed = false
        lastFired.compute(key) { _, last ->
            if (last == null || now - last >= rule.cooldown.inWholeNanoseconds) {
                allowed = true
                now
            } else {
                last
            }
        }
        return allowed
    }
}
