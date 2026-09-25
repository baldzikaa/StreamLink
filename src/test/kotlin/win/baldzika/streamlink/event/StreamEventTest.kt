package win.baldzika.streamlink.event

import win.baldzika.streamlink.rule.Placeholders
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamEventTest {

    private fun render(event: StreamEvent) = Placeholders.fill("{user} sent {amount} {currency}{gift}{message}", event.values())

    @Test
    fun `every placeholder has a value`() {
        assertEquals("a sent 250 bits", render(StreamEvent.Donation(Platform.TWITCH, "a", 250.0, "bits")))
        assertEquals("a sent 50 coinsRose", render(StreamEvent.Gift(Platform.TIKTOK, "a", "Rose", 50, 1)))
        assertEquals("a sent 0 ", render(StreamEvent.Follow(Platform.KICK, "a")))
    }

    @Test
    fun amounts() {
        assertEquals("4.99", StreamEvent.Donation(Platform.YOUTUBE, "a", 4.99, "$").values()["amount"])
        assertEquals(4, StreamEvent.Donation(Platform.YOUTUBE, "a", 4.99, "$").count)
        assertEquals(1, StreamEvent.Donation(Platform.YOUTUBE, "a", 0.5, "$").count)
        assertEquals(500.0, StreamEvent.Gift(Platform.TIKTOK, "a", "Galaxy", 5, 100).amount)
        assertEquals("3", StreamEvent.Sub(Platform.TWITCH, "a", 3).values()["months"])
    }
}
