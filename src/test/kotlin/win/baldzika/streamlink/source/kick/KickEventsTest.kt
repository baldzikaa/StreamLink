package win.baldzika.streamlink.source.kick

import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KickEventsTest {

    private fun frame(event: String, data: String, channel: String = "chatrooms.1.v2"): String {
        fun json(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"event":"${json(event)}","data":"${json(data)}","channel":"$channel"}"""
    }

    private fun event(text: String) = KickEvents.from(KickEvents.frame(text)!!)

    @Test
    fun chat() {
        val text = frame("App\\Events\\ChatMessageEvent", """{"id":"x","content":"!tnt","type":"message","sender":{"id":5,"username":"Viewer","slug":"viewer"}}""")
        assertEquals(StreamEvent.Chat(Platform.KICK, "Viewer", "!tnt"), event(text))
    }

    @Test
    fun `emotes become their names`() {
        val text = frame("App\\Events\\ChatMessageEvent", """{"content":"gg [emote:37226:KEKW] [emote:1:emojiFire]","sender":{"username":"Viewer"}}""")
        assertEquals(StreamEvent.Chat(Platform.KICK, "Viewer", "gg KEKW emojiFire"), event(text))
    }

    @Test
    fun subscription() {
        val text = frame("App\\Events\\SubscriptionEvent", """{"chatroom_id":1,"username":"Viewer","months":3}""")
        assertEquals(StreamEvent.Sub(Platform.KICK, "Viewer", 3), event(text))
    }

    @Test
    fun giftedSubs() {
        val text = frame("App\\Events\\GiftedSubscriptionsEvent", """{"chatroom_id":1,"gifted_usernames":["a","b","c"],"gifter_username":"Gifter"}""")
        assertEquals(StreamEvent.GiftSubs(Platform.KICK, "Gifter", 3), event(text))
    }

    @Test
    fun host() {
        val text = frame("App\\Events\\StreamHostEvent", """{"chatroom_id":1,"optional_message":"","number_viewers":42,"host_username":"Big"}""")
        assertEquals(StreamEvent.Raid(Platform.KICK, "Big", 42), event(text))
    }

    @Test
    fun follow() {
        val text = frame("App\\Events\\FollowersUpdated", """{"followersCount":10,"channel_id":2,"username":"Fan","followed":true}""", "channel.2")
        assertEquals(StreamEvent.Follow(Platform.KICK, "Fan"), event(text))
        val unfollow = frame("App\\Events\\FollowersUpdated", """{"followersCount":9,"channel_id":2,"username":"Fan","followed":false}""", "channel.2")
        assertNull(event(unfollow))
    }

    @Test
    fun kicks() {
        val text = frame("KicksGifted", """{"message":"","sender":{"id":1,"username":"Rich"},"gift":{"gift_id":"hype","name":"Hype","amount":500}}""", "channel_2")
        assertEquals(StreamEvent.Gift(Platform.KICK, "Rich", "Hype", 1, 500), event(text))
    }

    @Test
    fun `pusher control frames`() {
        val established = KickEvents.frame("""{"event":"pusher:connection_established","data":"{\"socket_id\":\"1.2\",\"activity_timeout\":120}"}""")!!
        assertEquals("pusher:connection_established", established.event)
        assertEquals("1.2", established.data!!.get("socket_id").asString)
        assertNull(KickEvents.from(established))
        assertNull(KickEvents.frame("[1,2]"))
    }
}
