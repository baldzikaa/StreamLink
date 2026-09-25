package win.baldzika.streamlink.rule

import win.baldzika.streamlink.event.EventType
import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent
import kotlin.time.Duration

data class Rule(
    val name: String,
    val events: Set<EventType>,
    val platforms: Set<Platform> = emptySet(),
    val min: Double = 0.0,
    val max: Double = Double.MAX_VALUE,
    val match: Regex? = null,
    val gifts: Set<String> = emptySet(),
    val chance: Double = 1.0,
    val cooldown: Duration = Duration.ZERO,
    val actions: List<Action>,
) {

    fun matches(event: StreamEvent): Boolean {
        if (event.type !in events) return false
        if (platforms.isNotEmpty() && event.platform !in platforms) return false
        if (event.type != EventType.CHAT && event.type != EventType.FOLLOW && event.type != EventType.SHARE) {
            if (event.amount < min || event.amount > max) return false
        }
        if (match != null) {
            val text = (event as? StreamEvent.Chat)?.message ?: (event as? StreamEvent.Donation)?.message ?: return false
            if (!match.containsMatchIn(text)) return false
        }
        if (gifts.isNotEmpty()) {
            val gift = (event as? StreamEvent.Gift)?.gift ?: return false
            if (gift.lowercase() !in gifts) return false
        }
        return true
    }
}
