package win.baldzika.streamlink.source.twitch

import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TwitchEventsTest {

    private fun event(line: String) = TwitchEvents.from(IrcMessage.parse(line)!!)

    @Test
    fun `parses tags prefix and trailing`() {
        val message = IrcMessage.parse("@badges=;color=#FF0000;display-name=Some\\sName;emotes= :somename!somename@somename.tmi.twitch.tv PRIVMSG #chan :hello : there")!!

        assertEquals("PRIVMSG", message.command)
        assertEquals("Some Name", message.tags["display-name"])
        assertEquals("", message.tags["emotes"])
        assertEquals("somename", message.nick)
        assertEquals(listOf("#chan", "hello : there"), message.params)
    }

    @Test
    fun `unescapes tag values`() {
        val message = IrcMessage.parse("@system-msg=a\\sb\\:c\\\\d :tmi.twitch.tv USERNOTICE #chan")!!
        assertEquals("a b;c\\d", message.tags["system-msg"])
        assertNull(message.nick)
    }

    @Test
    fun `ping and empty lines`() {
        assertEquals("tmi.twitch.tv", IrcMessage.parse("PING :tmi.twitch.tv")!!.trailing)
        assertNull(IrcMessage.parse(""))
    }

    @Test
    fun chat() {
        assertEquals(
            StreamEvent.Chat(Platform.TWITCH, "Viewer", "!tnt"),
            event("@display-name=Viewer :viewer!viewer@viewer.tmi.twitch.tv PRIVMSG #chan :!tnt"),
        )
    }

    @Test
    fun `me messages drop the action wrapper`() {
        assertEquals(
            StreamEvent.Chat(Platform.TWITCH, "Viewer", "waves"),
            event("@display-name=Viewer :viewer!viewer@viewer.tmi.twitch.tv PRIVMSG #chan :\u0001ACTION waves\u0001"),
        )
    }

    @Test
    fun `bits become donations without the cheer words`() {
        assertEquals(
            StreamEvent.Donation(Platform.TWITCH, "Viewer", 250.0, "bits", "nice build"),
            event("@bits=250;display-name=Viewer :viewer!viewer@viewer.tmi.twitch.tv PRIVMSG #chan :Cheer200 nice build Cheer50"),
        )
    }

    @Test
    fun resub() {
        assertEquals(
            StreamEvent.Sub(Platform.TWITCH, "Viewer", 7),
            event("@display-name=Viewer;msg-id=resub;msg-param-cumulative-months=7;msg-param-sub-plan=1000 :tmi.twitch.tv USERNOTICE #chan :7 months!"),
        )
    }

    @Test
    fun `gift bomb counts once`() {
        assertEquals(
            StreamEvent.GiftSubs(Platform.TWITCH, "Gifter", 10),
            event("@display-name=Gifter;msg-id=submysterygift;msg-param-mass-gift-count=10 :tmi.twitch.tv USERNOTICE #chan"),
        )
        assertNull(event("@display-name=Gifter;msg-id=subgift;msg-param-community-gift-id=123;msg-param-recipient-display-name=A :tmi.twitch.tv USERNOTICE #chan"))
        assertEquals(
            StreamEvent.GiftSubs(Platform.TWITCH, "Gifter", 1),
            event("@display-name=Gifter;msg-id=subgift;msg-param-recipient-display-name=A :tmi.twitch.tv USERNOTICE #chan"),
        )
    }

    @Test
    fun raid() {
        assertEquals(
            StreamEvent.Raid(Platform.TWITCH, "BigStreamer", 420),
            event("@display-name=bigstreamer;msg-id=raid;msg-param-displayName=BigStreamer;msg-param-viewerCount=420 :tmi.twitch.tv USERNOTICE #chan"),
        )
    }

    @Test
    fun `ignores other notices`() {
        assertNull(event("@display-name=Viewer;msg-id=announcement :tmi.twitch.tv USERNOTICE #chan :hi"))
        assertNull(event("@emote-only=0 :tmi.twitch.tv ROOMSTATE #chan"))
    }
}
