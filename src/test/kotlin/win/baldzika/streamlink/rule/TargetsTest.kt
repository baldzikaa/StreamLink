package win.baldzika.streamlink.rule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TargetsTest {

    private val online = listOf("Baldzika", "Steve", "alex", "Notch")

    @Test
    fun `streamer only`() {
        assertEquals(Targets.STREAMER, Targets.parse("streamer"))
        assertEquals(listOf("Baldzika"), Targets.STREAMER.resolve("baldzika", online))
    }

    @Test
    fun everyone() {
        assertEquals(Targets.EVERYONE, Targets.parse(" ALL "))
        assertEquals(online, Targets.EVERYONE.resolve("baldzika", online))
    }

    @Test
    fun `names split by commas`() {
        val targets = Targets.parse("Steve, Alex,  Herobrine")
        assertEquals(Targets(everyone = false, streamer = false, players = setOf("Steve", "Alex", "Herobrine")), targets)
        assertEquals(listOf("Steve", "alex"), targets.resolve("baldzika", online))
    }

    @Test
    fun `streamer mixed with names`() {
        val targets = Targets.parse("streamer, Notch")
        assertEquals(listOf("Baldzika", "Notch"), targets.resolve("Baldzika", online))
    }

    @Test
    fun `offline players are skipped`() {
        assertEquals(emptyList(), Targets.parse("Herobrine").resolve("baldzika", online))
        assertEquals(emptyList(), Targets.STREAMER.resolve("SomeoneOffline", online))
    }

    @Test
    fun `bad input is explained`() {
        assertEquals("targets is empty, use streamer, all or player names", assertFailsWith<IllegalArgumentException> { Targets.parse(" , ") }.message)
        assertEquals("'all' already means everyone, drop the other names", assertFailsWith<IllegalArgumentException> { Targets.parse("all, Steve") }.message)
        assertEquals("'not a name' isn't a valid player name", assertFailsWith<IllegalArgumentException> { Targets.parse("Steve, not a name") }.message)
    }
}
