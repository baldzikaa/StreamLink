package win.baldzika.streamlink.source.kick

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent

/**
 * kick sends pusher frames where data is a json string inside the json.
 */
object KickEvents {

    private val EMOTE = Regex("\\[emote:\\d+:([^\\]]+)]")

    data class Frame(val event: String, val channel: String?, val data: JsonObject?)

    fun frame(text: String): Frame? {
        val root = JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val event = root.string("event") ?: return null
        val data = root.get("data")?.let { raw ->
            when {
                raw.isJsonObject -> raw.asJsonObject
                raw.isJsonPrimitive -> JsonParser.parseString(raw.asString).takeIf { it.isJsonObject }?.asJsonObject
                else -> null
            }
        }
        return Frame(event, root.string("channel"), data)
    }

    fun from(frame: Frame): StreamEvent? {
        val data = frame.data ?: return null
        return when (frame.event.substringAfterLast('\\')) {
            "ChatMessageEvent" -> {
                val user = data.obj("sender")?.string("username") ?: return null
                StreamEvent.Chat(Platform.KICK, user, data.string("content").orEmpty().replace(EMOTE, "$1"))
            }
            "SubscriptionEvent" -> StreamEvent.Sub(
                Platform.KICK,
                data.string("username") ?: return null,
                data.int("months") ?: 1,
            )
            "GiftedSubscriptionsEvent" -> StreamEvent.GiftSubs(
                Platform.KICK,
                data.string("gifter_username") ?: return null,
                data.getAsJsonArray("gifted_usernames")?.size() ?: 1,
            )
            "StreamHostEvent" -> StreamEvent.Raid(
                Platform.KICK,
                data.string("host_username") ?: return null,
                data.int("number_viewers") ?: 0,
            )
            "FollowersUpdated" -> {
                if (data.get("followed")?.asBoolean != true) return null
                StreamEvent.Follow(Platform.KICK, data.string("username") ?: return null)
            }
            "KicksGifted" -> {
                val gift = data.obj("gift") ?: return null
                StreamEvent.Gift(
                    Platform.KICK,
                    data.obj("sender")?.string("username") ?: return null,
                    gift.string("name") ?: "Kicks",
                    1,
                    gift.int("amount") ?: 0,
                )
            }
            else -> null
        }
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.int(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.toInt()

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject
}
