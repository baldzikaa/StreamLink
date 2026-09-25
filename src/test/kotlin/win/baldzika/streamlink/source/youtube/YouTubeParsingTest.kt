package win.baldzika.streamlink.source.youtube

import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YouTubeParsingTest {

    private fun resource(name: String) = javaClass.getResource("/youtube/$name")!!.readText()

    @Test
    fun `reads every chat item type`() {
        val batch = YouTubeChat.parse(resource("chat.json"))

        assertEquals("next-page-token", batch.continuation)
        assertEquals(10_000, batch.waitMillis)
        assertEquals(
            listOf(
                StreamEvent.Chat(Platform.YOUTUBE, "FirstViewer", "gg :fire: !tnt"),
                StreamEvent.Donation(Platform.YOUTUBE, "BigFan", 5.0, "€", "love the stream"),
                StreamEvent.Donation(Platform.YOUTUBE, "StickerFan", 1000.0, "CA$", ""),
                StreamEvent.Sub(Platform.YOUTUBE, "LongMember", 6),
                StreamEvent.Sub(Platform.YOUTUBE, "NewMember", 1),
                StreamEvent.GiftSubs(Platform.YOUTUBE, "Generous", 5),
            ),
            batch.events,
        )
    }

    @Test
    fun `no continuation means the stream ended`() {
        val batch = YouTubeChat.parse(resource("ended.json"))
        assertNull(batch.continuation)
        assertEquals(emptyList(), batch.events)
    }

    @Test
    fun `money in different formats`() {
        assertEquals(5.0 to "$", YouTubeChat.money("$5.00"))
        assertEquals(5.0 to "€", YouTubeChat.money("€5,00"))
        assertEquals(1000.0 to "₹", YouTubeChat.money("₹1,000.00"))
        assertEquals(1234.5 to "€", YouTubeChat.money("1.234,50 €"))
        assertEquals(20.0 to "CA$", YouTubeChat.money("CA$20.00"))
        assertEquals(0.0 to "free", YouTubeChat.money("free"))
    }

    @Test
    fun `finds the live video`() {
        assertEquals("abcdefghijk", YouTubePages.liveVideo(resource("live.html")))
        assertNull(YouTubePages.liveVideo(resource("offline.html")))
    }

    @Test
    fun `reads chat settings from the popout`() {
        assertEquals(
            YouTubePages.Chat("test-key", "2.20260924.00.00", "first-page-token"),
            YouTubePages.chat(resource("popout.html")),
        )
    }

    @Test
    fun `builds the right page for each kind of target`() {
        assertEquals("https://www.youtube.com/@LofiGirl/live", YouTubePages.livePage("@LofiGirl"))
        assertEquals("https://www.youtube.com/@LofiGirl/live", YouTubePages.livePage("LofiGirl"))
        assertEquals("https://www.youtube.com/channel/UCSJ4gkVC6NrvII8umztf0Ow/live", YouTubePages.livePage("UCSJ4gkVC6NrvII8umztf0Ow"))
        assertEquals("https://www.youtube.com/@LofiGirl/live", YouTubePages.livePage("https://www.youtube.com/@LofiGirl"))
        assertEquals("jfKfPfyJRdk", YouTubePages.directVideo("https://www.youtube.com/watch?v=jfKfPfyJRdk&t=5"))
        assertEquals("jfKfPfyJRdk", YouTubePages.directVideo("https://youtube.com/live/jfKfPfyJRdk"))
        assertEquals("jfKfPfyJRdk", YouTubePages.directVideo("jfKfPfyJRdk"))
        assertNull(YouTubePages.directVideo("@LofiGirl"))
    }
}
