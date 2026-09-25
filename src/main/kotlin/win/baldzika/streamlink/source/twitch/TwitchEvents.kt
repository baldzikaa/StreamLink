package win.baldzika.streamlink.source.twitch

import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent

object TwitchEvents {

    private val CHEER = Regex("(?i)\\b[a-z]+\\d+\\b")

    fun from(message: IrcMessage): StreamEvent? {
        val user = message.tags["display-name"]?.takeIf { it.isNotBlank() } ?: message.tags["login"] ?: message.nick ?: return null
        return when (message.command) {
            "PRIVMSG" -> privmsg(message, user)
            "USERNOTICE" -> usernotice(message, user)
            else -> null
        }
    }

    private fun privmsg(message: IrcMessage, user: String): StreamEvent {
        val bits = message.tags["bits"]?.toIntOrNull()
        val text = message.trailing
        if (bits != null && bits > 0) {
            val clean = text.replace(CHEER, "").replace(Regex("\\s+"), " ").trim()
            return StreamEvent.Donation(Platform.TWITCH, user, bits.toDouble(), "bits", clean)
        }
        return StreamEvent.Chat(Platform.TWITCH, user, text.removePrefix("\u0001ACTION ").removeSuffix("\u0001"))
    }

    private fun usernotice(message: IrcMessage, user: String): StreamEvent? {
        val tags = message.tags
        fun int(key: String) = tags[key]?.toIntOrNull()
        return when (tags["msg-id"]) {
            "sub", "resub" -> StreamEvent.Sub(Platform.TWITCH, user, int("msg-param-cumulative-months") ?: 1)
            "subgift" -> {
                // part of a gift bomb, the bomb itself already arrived as submysterygift
                if (tags["msg-param-community-gift-id"] != null) return null
                StreamEvent.GiftSubs(Platform.TWITCH, user, 1)
            }
            "submysterygift" -> StreamEvent.GiftSubs(Platform.TWITCH, user, int("msg-param-mass-gift-count") ?: 1)
            "raid" -> StreamEvent.Raid(
                Platform.TWITCH,
                tags["msg-param-displayName"] ?: user,
                int("msg-param-viewerCount") ?: 0,
            )
            else -> null
        }
    }
}
