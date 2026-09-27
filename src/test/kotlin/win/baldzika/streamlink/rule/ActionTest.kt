package win.baldzika.streamlink.rule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ActionTest {

    @Test
    fun `short and long forms`() {
        assertEquals(Action.Message("hi"), Action.parse(mapOf("message" to "hi")))
        assertEquals(Action.Spawn("zombie", "1", 4.0), Action.parse(mapOf("spawn" to "zombie")))
        assertEquals(Action.Spawn("zombie", "{count}", 6.0), Action.parse(mapOf("spawn" to mapOf("entity" to "zombie", "amount" to "{count}", "radius" to 6))))
        assertEquals(Action.Sound("entity.player.levelup", 1f, 1f), Action.parse(mapOf("sound" to "entity.player.levelup")))
        assertEquals(Action.Sound("block.note_block.pling", 0.5f, 2f), Action.parse(mapOf("sound" to mapOf("sound" to "block.note_block.pling", "volume" to 0.5, "pitch" to 2))))
        assertEquals(Action.Title("a", "b"), Action.parse(mapOf("title" to mapOf("title" to "a", "subtitle" to "b"))))
        assertEquals(Action.Effect("speed", 10, 2), Action.parse(mapOf("effect" to mapOf("type" to "speed", "seconds" to 10, "level" to 2))))
        assertEquals(Action.Give("cake", "1"), Action.parse(mapOf("give" to "cake")))
        assertEquals(Action.Lightning(true), Action.parse(mapOf("lightning" to mapOf<String, Any>())))
        assertEquals(Action.Lightning(false), Action.parse(mapOf("lightning" to mapOf("harmless" to false))))
        assertEquals(Action.Command("say hi"), Action.parse(mapOf("command" to "/say hi")))
    }

    @Test
    fun `keeps numbers in a safe range`() {
        assertEquals(Action.Spawn("zombie", "1", 16.0), Action.parse(mapOf("spawn" to mapOf("entity" to "zombie", "radius" to 500))))
        assertEquals(Action.Spawn("zombie", "1", 0.0), Action.parse(mapOf("spawn" to mapOf("entity" to "zombie", "radius" to -3))))
        assertEquals(Action.Effect("speed", 3600, 255), Action.parse(mapOf("effect" to mapOf("type" to "speed", "seconds" to 999999, "level" to 1000))))
        assertEquals(Action.Effect("speed", 1, 1), Action.parse(mapOf("effect" to mapOf("type" to "speed", "seconds" to 0, "level" to 0))))
    }

    @Test
    fun `rejects unknown or ambiguous actions`() {
        assertFailsWith<IllegalArgumentException> { Action.parse(mapOf("explode" to "yes")) }
        assertFailsWith<IllegalArgumentException> { Action.parse(mapOf("message" to "a", "broadcast" to "b")) }
    }
}
