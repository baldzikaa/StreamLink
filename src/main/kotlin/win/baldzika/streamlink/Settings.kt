package win.baldzika.streamlink

import org.bukkit.configuration.ConfigurationSection
import win.baldzika.streamlink.event.EventType
import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.rule.Action
import win.baldzika.streamlink.rule.Rule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class Link(val player: String, val accounts: Map<Platform, String>, val kickChatroom: Long? = null)

data class Settings(
    val links: List<Link>,
    val rules: List<Rule>,
    val relayChat: Boolean,
    val relayFormat: String,
    val onlyWhenOnline: Boolean,
    val maxSpawn: Int,
    val maxGive: Int,
    val tiktokApiKey: String,
) {

    companion object {

        const val DEFAULT_RELAY = "<dark_gray>[<{color}>{platform}</{color}>]</dark_gray> <gray>{user}:</gray> <white>{message}"

        fun load(config: ConfigurationSection, warn: (String) -> Unit): Settings = Settings(
            links = links(config.getConfigurationSection("links"), warn),
            rules = rules(config.getConfigurationSection("rules"), warn),
            relayChat = config.getBoolean("relay-chat.enabled", true),
            relayFormat = config.getString("relay-chat.format") ?: DEFAULT_RELAY,
            onlyWhenOnline = config.getBoolean("only-when-online", true),
            maxSpawn = config.getInt("limits.max-spawn", 25).coerceIn(0, 500),
            maxGive = config.getInt("limits.max-give", 64).coerceIn(0, 2304),
            tiktokApiKey = config.getString("tiktok.api-key").orEmpty(),
        )

        private fun links(section: ConfigurationSection?, warn: (String) -> Unit): List<Link> {
            section ?: return emptyList()
            return section.getKeys(false).mapNotNull { player ->
                val link = section.getConfigurationSection(player) ?: return@mapNotNull null
                val accounts = Platform.entries.mapNotNull { platform ->
                    link.getString(platform.key)?.trim()?.takeIf { it.isNotEmpty() }?.let { platform to it }
                }.toMap()
                if (accounts.isEmpty()) {
                    warn("link '$player' has no accounts, add twitch, kick, youtube or tiktok under it")
                    null
                } else {
                    Link(player, accounts, link.getLong("kick-chatroom").takeIf { it > 0 })
                }
            }
        }

        private fun rules(section: ConfigurationSection?, warn: (String) -> Unit): List<Rule> {
            section ?: return emptyList()
            return section.getKeys(false).mapNotNull { name ->
                runCatching { rule(name, section.getConfigurationSection(name)!!) }
                    .onFailure { warn("skipping rule '$name': ${it.message}") }
                    .getOrNull()
            }
        }

        private fun rule(name: String, section: ConfigurationSection): Rule {
            val events = list(section, "events").map { EventType.of(it) ?: throw IllegalArgumentException("unknown event '$it'") }
            require(events.isNotEmpty()) { "'events' is missing" }
            val platforms = list(section, "platforms").map { Platform.of(it) ?: throw IllegalArgumentException("unknown platform '$it'") }
            val actions = section.getMapList("actions").map { Action.parse(it) }
            require(actions.isNotEmpty()) { "no actions" }
            return Rule(
                name = name,
                events = events.toSet(),
                platforms = platforms.toSet(),
                min = section.getDouble("min", 0.0),
                max = section.getDouble("max", Double.MAX_VALUE),
                match = section.getString("match")?.let { Regex(it, RegexOption.IGNORE_CASE) },
                gifts = list(section, "gift").map { it.lowercase() }.toSet(),
                chance = section.getDouble("chance", 1.0).coerceIn(0.0, 1.0),
                cooldown = section.getString("cooldown")?.let(::duration) ?: Duration.ZERO,
                actions = actions,
            )
        }

        private fun list(section: ConfigurationSection, key: String): List<String> =
            if (section.isList(key)) section.getStringList(key) else listOfNotNull(section.getString(key))

        fun duration(text: String): Duration {
            val value = text.trim().lowercase()
            val number = value.takeWhile { it.isDigit() || it == '.' }.toDoubleOrNull()
                ?: throw IllegalArgumentException("bad duration '$text'")
            return when (value.substring(value.takeWhile { it.isDigit() || it == '.' }.length).trim()) {
                "ms" -> number.milliseconds
                "", "s" -> number.seconds
                "m" -> number.minutes
                "h" -> number.hours
                else -> throw IllegalArgumentException("bad duration '$text', use something like 30s, 5m or 1h")
            }
        }
    }
}
