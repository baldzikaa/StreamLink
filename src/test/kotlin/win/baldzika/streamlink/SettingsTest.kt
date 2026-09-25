package win.baldzika.streamlink

import org.bukkit.configuration.file.YamlConfiguration
import win.baldzika.streamlink.event.EventType
import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.rule.Action
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SettingsTest {

    private fun load(yaml: String): Pair<Settings, List<String>> {
        val warnings = mutableListOf<String>()
        val config = YamlConfiguration().apply { loadFromString(yaml) }
        return Settings.load(config) { warnings += it } to warnings
    }

    @Test
    fun `default config loads cleanly`() {
        val config = YamlConfiguration.loadConfiguration(javaClass.getResource("/config.yml")!!.openStream().reader())
        val warnings = mutableListOf<String>()
        val settings = Settings.load(config) { warnings += it }

        assertEquals(emptyList(), warnings)
        assertEquals(listOf(Link("Notch", mapOf(Platform.TWITCH to "notch"))), settings.links)
        assertEquals(6, settings.rules.size)
        val tnt = settings.rules.first { it.name == "chat-tnt" }
        assertEquals(setOf(EventType.CHAT), tnt.events)
        assertEquals(30.seconds, tnt.cooldown)
        assertTrue(tnt.match!!.matches("!TNT"))
    }

    @Test
    fun `links and rules`() {
        val (settings, warnings) = load(
            """
            links:
              Streamer:
                twitch: streamer
                kick: streamer
                kick-chatroom: 1234
                youtube: "@streamer"
              Nobody: {}
            rules:
              gifts:
                events: gift
                platforms: [tiktok]
                gift: [Rose, Galaxy]
                min: 10
                chance: 2
                actions:
                  - spawn: zombie
              broken:
                events: [teleport]
                actions:
                  - message: hi
              empty:
                events: chat
            """.trimIndent(),
        )

        val link = settings.links.single()
        assertEquals("Streamer", link.player)
        assertEquals(mapOf(Platform.TWITCH to "streamer", Platform.KICK to "streamer", Platform.YOUTUBE to "@streamer"), link.accounts)
        assertEquals(1234L, link.kickChatroom)

        val rule = settings.rules.single()
        assertEquals(setOf(Platform.TIKTOK), rule.platforms)
        assertEquals(setOf("rose", "galaxy"), rule.gifts)
        assertEquals(1.0, rule.chance)
        assertEquals(listOf<Action>(Action.Spawn("zombie", "1", 4.0)), rule.actions)

        assertEquals(3, warnings.size)
        assertTrue(warnings.any { "Nobody" in it })
        assertTrue(warnings.any { "unknown event 'teleport'" in it })
        assertTrue(warnings.any { "'empty': no actions" in it })
    }

    @Test
    fun durations() {
        assertEquals(30.seconds, Settings.duration("30s"))
        assertEquals(30.seconds, Settings.duration("30"))
        assertEquals(500.milliseconds, Settings.duration("500ms"))
        assertEquals(1.5.minutes, Settings.duration("1.5m"))
        assertFailsWith<IllegalArgumentException> { Settings.duration("soon") }
        assertFailsWith<IllegalArgumentException> { Settings.duration("5d") }
    }
}
