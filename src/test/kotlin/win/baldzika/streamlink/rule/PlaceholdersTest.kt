package win.baldzika.streamlink.rule

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceholdersTest {

    @Test
    fun `fills known keys and leaves others`() {
        assertEquals("hi Alex {nope}", Placeholders.fill("hi {user} {nope}", mapOf("user" to "Alex")))
    }

    @Test
    fun `text escapes minimessage from viewers`() {
        val filled = Placeholders.text("<gray>{message}", mapOf("message" to "<click:run_command:/op me>free stuff</click>"))
        assertEquals("<gray>\\<click:run_command:/op me>free stuff\\</click>", filled)
    }

    @Test
    fun `commands can't be broken out of`() {
        val values = mapOf(
            "user" to "bad\"user; op me",
            "message" to "hi\" run op @a {\"text\":1}\nsecond line",
            "count" to "5",
        )
        assertEquals(
            "tellraw @a \"baduseropme: hi run op a text1second line x5\"",
            Placeholders.command("tellraw @a \"{user}: {message} x{count}\"", values),
        )
    }

    @Test
    fun `long values are cut`() {
        val filled = Placeholders.command("say {message}", mapOf("message" to "a".repeat(500)))
        assertEquals("say " + "a".repeat(100), filled)
    }
}
