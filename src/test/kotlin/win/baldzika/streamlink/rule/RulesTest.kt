package win.baldzika.streamlink.rule

import win.baldzika.streamlink.event.EventType
import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RulesTest {

    private val say = listOf(Action.Message("hi"))

    @Test
    fun `matches on event type and platform`() {
        val rule = Rule("r", setOf(EventType.FOLLOW), setOf(Platform.KICK), actions = say)

        assertTrue(rule.matches(StreamEvent.Follow(Platform.KICK, "a")))
        assertFalse(rule.matches(StreamEvent.Follow(Platform.TWITCH, "a")))
        assertFalse(rule.matches(StreamEvent.Share(Platform.KICK, "a")))
    }

    @Test
    fun `min and max use the event amount`() {
        val rule = Rule("r", setOf(EventType.DONATION, EventType.GIFT), min = 100.0, max = 500.0, actions = say)

        assertTrue(rule.matches(StreamEvent.Donation(Platform.TWITCH, "a", 100.0, "bits")))
        assertFalse(rule.matches(StreamEvent.Donation(Platform.TWITCH, "a", 99.0, "bits")))
        assertFalse(rule.matches(StreamEvent.Donation(Platform.TWITCH, "a", 501.0, "bits")))
        assertTrue(rule.matches(StreamEvent.Gift(Platform.TIKTOK, "a", "Rose", 20, 5)))
        assertFalse(rule.matches(StreamEvent.Gift(Platform.TIKTOK, "a", "Rose", 19, 5)))
    }

    @Test
    fun `chat ignores min`() {
        val rule = Rule("r", setOf(EventType.CHAT), min = 10.0, actions = say)
        assertTrue(rule.matches(StreamEvent.Chat(Platform.TWITCH, "a", "hey")))
    }

    @Test
    fun `match checks chat and donation messages`() {
        val rule = Rule("r", setOf(EventType.CHAT, EventType.DONATION, EventType.FOLLOW), match = Regex("^!tnt$", RegexOption.IGNORE_CASE), actions = say)

        assertTrue(rule.matches(StreamEvent.Chat(Platform.TWITCH, "a", "!TNT")))
        assertFalse(rule.matches(StreamEvent.Chat(Platform.TWITCH, "a", "!tnt please")))
        assertTrue(rule.matches(StreamEvent.Donation(Platform.YOUTUBE, "a", 5.0, "$", "!tnt")))
        assertFalse(rule.matches(StreamEvent.Follow(Platform.TWITCH, "a")))
    }

    @Test
    fun `gift names are case insensitive`() {
        val rule = Rule("r", setOf(EventType.GIFT), gifts = setOf("rose"), actions = say)
        assertTrue(rule.matches(StreamEvent.Gift(Platform.TIKTOK, "a", "Rose", 1, 1)))
        assertFalse(rule.matches(StreamEvent.Gift(Platform.TIKTOK, "a", "Galaxy", 1, 1000)))
    }

    @Test
    fun `cooldown is per rule and per player`() {
        var now = 0L
        val rules = Rules(listOf(Rule("tnt", setOf(EventType.CHAT), cooldown = 30.seconds, actions = say)), clock = { now })
        val chat = StreamEvent.Chat(Platform.TWITCH, "a", "!tnt")

        assertEquals(1, rules.fire("Alex", chat).size)
        assertEquals(0, rules.fire("alex", chat).size)
        assertEquals(1, rules.fire("Sam", chat).size)

        now += 30.seconds.inWholeNanoseconds
        assertEquals(1, rules.fire("Alex", chat).size)
    }

    @Test
    fun `chance rolls`() {
        val rules = Rules(listOf(Rule("coin", setOf(EventType.LIKE), chance = 0.5, actions = say)), random = Random(42))
        val hits = (1..1000).count { rules.fire("p", StreamEvent.Like(Platform.TIKTOK, "a", 1)).isNotEmpty() }
        assertTrue(hits in 400..600, "hit $hits times")
    }
}
