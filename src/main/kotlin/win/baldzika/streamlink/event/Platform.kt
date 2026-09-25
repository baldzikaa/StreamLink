package win.baldzika.streamlink.event

enum class Platform(val display: String, val color: String) {
    TWITCH("Twitch", "#9146FF"),
    KICK("Kick", "#53FC18"),
    YOUTUBE("YouTube", "#FF0033"),
    TIKTOK("TikTok", "#FE2C55");

    val key: String = name.lowercase()

    companion object {
        fun of(key: String): Platform? = entries.firstOrNull { it.key == key.lowercase() }
    }
}
