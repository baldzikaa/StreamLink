package win.baldzika.streamlink.rule

import net.kyori.adventure.text.minimessage.MiniMessage

object Placeholders {

    private val PATTERN = Regex("\\{([a-z_]+)}")
    private val SAFE_NAME = Regex("[^A-Za-z0-9_.-]")
    private val SAFE_TEXT = Regex("[^\\p{L}\\p{N} .,!?'-]")

    fun fill(template: String, values: Map<String, String>, escape: (String) -> String = { it }): String =
        PATTERN.replace(template) { match -> values[match.groupValues[1]]?.let(escape) ?: match.value }

    /**
     * for minimessage templates. viewers can't sneak in tags like click events.
     */
    fun text(template: String, values: Map<String, String>): String =
        fill(template, values) { MiniMessage.miniMessage().escapeTags(it) }

    /**
     * for console commands. anything that could break out of the command is stripped.
     */
    fun command(template: String, values: Map<String, String>): String =
        fill(template, values.mapValues { (key, value) -> safe(key, value) })

    private fun safe(key: String, value: String): String = when (key) {
        "user", "player", "gift" -> value.replace(SAFE_NAME, "").take(32)
        else -> value.replace(SAFE_TEXT, "").trim().take(100)
    }
}
