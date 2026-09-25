package win.baldzika.streamlink.event

enum class EventType(val key: String) {
    CHAT("chat"),
    FOLLOW("follow"),
    SUB("sub"),
    GIFT_SUBS("gift-subs"),
    DONATION("donation"),
    GIFT("gift"),
    RAID("raid"),
    LIKE("like"),
    SHARE("share");

    companion object {
        fun of(key: String): EventType? = entries.firstOrNull { it.key == key.lowercase() }
    }
}

/**
 * something that happened on a stream. amount is what rules compare min and max against.
 */
sealed interface StreamEvent {
    val platform: Platform
    val user: String
    val type: EventType
    val amount: Double
    val count: Int

    fun values(): Map<String, String> = mapOf(
        "message" to "",
        "currency" to "",
        "gift" to "",
        "months" to "",
        "user" to user,
        "platform" to platform.display,
        "amount" to formatNumber(amount),
        "count" to count.toString(),
    )

    data class Chat(override val platform: Platform, override val user: String, val message: String) : StreamEvent {
        override val type get() = EventType.CHAT
        override val amount get() = 0.0
        override val count get() = 1
        override fun values() = super.values() + ("message" to message)
    }

    data class Follow(override val platform: Platform, override val user: String) : StreamEvent {
        override val type get() = EventType.FOLLOW
        override val amount get() = 0.0
        override val count get() = 1
    }

    data class Sub(
        override val platform: Platform,
        override val user: String,
        val months: Int = 1,
        val gifted: Boolean = false,
    ) : StreamEvent {
        override val type get() = EventType.SUB
        override val amount get() = months.toDouble()
        override val count get() = 1
        override fun values() = super.values() + ("months" to months.toString())
    }

    data class GiftSubs(override val platform: Platform, override val user: String, override val count: Int) : StreamEvent {
        override val type get() = EventType.GIFT_SUBS
        override val amount get() = count.toDouble()
    }

    data class Donation(
        override val platform: Platform,
        override val user: String,
        val value: Double,
        val currency: String,
        val message: String = "",
    ) : StreamEvent {
        override val type get() = EventType.DONATION
        override val amount get() = value
        override val count get() = maxOf(1, value.toInt())
        override fun values() = super.values() + mapOf("currency" to currency, "message" to message)
    }

    /**
     * tiktok gifts. amount is the total coin value so rules can say min: 100 and mean it.
     */
    data class Gift(
        override val platform: Platform,
        override val user: String,
        val gift: String,
        override val count: Int,
        val coins: Int,
    ) : StreamEvent {
        override val type get() = EventType.GIFT
        override val amount get() = (coins * count).toDouble()
        override fun values() = super.values() + mapOf("gift" to gift, "currency" to "coins")
    }

    data class Raid(override val platform: Platform, override val user: String, val viewers: Int) : StreamEvent {
        override val type get() = EventType.RAID
        override val amount get() = viewers.toDouble()
        override val count get() = viewers
    }

    data class Like(override val platform: Platform, override val user: String, override val count: Int) : StreamEvent {
        override val type get() = EventType.LIKE
        override val amount get() = count.toDouble()
    }

    data class Share(override val platform: Platform, override val user: String) : StreamEvent {
        override val type get() = EventType.SHARE
        override val amount get() = 0.0
        override val count get() = 1
    }
}

internal fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, value)
