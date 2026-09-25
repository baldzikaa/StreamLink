package win.baldzika.streamlink.source.youtube

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent

/**
 * reads get_live_chat responses.
 */
object YouTubeChat {

    data class Batch(val events: List<StreamEvent>, val continuation: String?, val waitMillis: Long)

    private const val GIFT_PURCHASE = "liveChatSponsorshipsGiftPurchaseAnnouncementRenderer"
    private val NUMBER = Regex("\\d[\\d.,\\s]*")
    private val FIRST_INT = Regex("\\d+")

    fun parse(body: String): Batch {
        val root = JsonParser.parseString(body).asJsonObject
        val chat = root.path("continuationContents", "liveChatContinuation")
            ?: return Batch(emptyList(), null, 0)

        var continuation: String? = null
        var wait = 5_000L
        chat.getAsJsonArray("continuations")?.firstOrNull()?.asJsonObject?.let { holder ->
            val data = holder.entrySet().firstOrNull()?.value?.asJsonObject
            continuation = data?.text("continuation")
            wait = data?.get("timeoutMs")?.asLong ?: wait
        }

        val events = chat.getAsJsonArray("actions").orEmpty().mapNotNull { action ->
            val item = action.asJsonObject.path("addChatItemAction", "item") ?: return@mapNotNull null
            event(item)
        }
        return Batch(events, continuation, wait)
    }

    private fun event(item: JsonObject): StreamEvent? {
        val (type, body) = item.entrySet().firstOrNull()?.let { it.key to it.value.asJsonObject } ?: return null
        if (type == GIFT_PURCHASE) return gifts(body)
        val user = body.path("authorName")?.text("simpleText")?.removePrefix("@") ?: return null
        return when (type) {
            "liveChatTextMessageRenderer" -> StreamEvent.Chat(Platform.YOUTUBE, user, runs(body.path("message")))
            "liveChatPaidMessageRenderer", "liveChatPaidStickerRenderer" -> {
                val (value, currency) = money(body.path("purchaseAmountText")?.text("simpleText").orEmpty())
                StreamEvent.Donation(Platform.YOUTUBE, user, value, currency, runs(body.path("message")))
            }
            "liveChatMembershipItemRenderer" -> {
                val months = FIRST_INT.find(runs(body.path("headerPrimaryText")))?.value?.toIntOrNull() ?: 1
                StreamEvent.Sub(Platform.YOUTUBE, user, months)
            }
            else -> null
        }
    }

    private fun gifts(body: JsonObject): StreamEvent? {
        val header = body.path("header", "liveChatSponsorshipsHeaderRenderer") ?: return null
        val user = header.path("authorName")?.text("simpleText")?.removePrefix("@") ?: return null
        val count = FIRST_INT.find(runs(header.path("primaryText")))?.value?.toIntOrNull() ?: 1
        return StreamEvent.GiftSubs(Platform.YOUTUBE, user, count)
    }

    /**
     * "$5.00", "€5,00", "CA$10.00", "₹1,000.00" into a number and whatever currency text was around it.
     */
    fun money(text: String): Pair<Double, String> {
        val match = NUMBER.find(text) ?: return 0.0 to text.trim()
        val currency = text.removeRange(match.range).trim()
        var digits = match.value.replace(Regex("\\s"), "")
        val lastComma = digits.lastIndexOf(',')
        val lastDot = digits.lastIndexOf('.')
        digits = when {
            lastComma > lastDot && digits.length - lastComma == 3 -> digits.replace(".", "").replace(',', '.')
            else -> digits.replace(",", "")
        }
        return (digits.toDoubleOrNull() ?: 0.0) to currency
    }

    private fun runs(holder: JsonObject?): String {
        holder ?: return ""
        holder.text("simpleText")?.let { return it }
        return holder.getAsJsonArray("runs").orEmpty().joinToString("") { run ->
            val obj = run.asJsonObject
            obj.text("text") ?: obj.path("emoji")?.getAsJsonArray("shortcuts")?.firstOrNull()?.asString.orEmpty()
        }
    }

    private fun JsonObject.path(vararg keys: String): JsonObject? {
        var current: JsonObject = this
        for (key in keys) {
            current = current.get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        }
        return current
    }

    private fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonArray?.orEmpty(): List<JsonElement> = this?.toList() ?: emptyList()
}
