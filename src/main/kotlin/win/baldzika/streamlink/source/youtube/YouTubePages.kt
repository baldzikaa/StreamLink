package win.baldzika.streamlink.source.youtube

/**
 * pulls what we need out of youtube's html pages.
 */
object YouTubePages {

    data class Chat(val apiKey: String, val clientVersion: String, val continuation: String)

    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val LIVE_VIDEO = Regex("\"updatedMetadataEndpoint\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"")
    private val API_KEY = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")
    private val CLIENT_VERSION = Regex("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"")
    private val CONTINUATION = Regex("\"(?:invalidation|timed|reload)ContinuationData\":\\{[^}]*?\"continuation\":\"([^\"]+)\"")

    /**
     * the page to load for a channel handle, channel id, video id or link.
     */
    fun livePage(target: String): String {
        val value = target.trim()
        return when {
            value.startsWith("http") -> value.substringBefore('#').let { url ->
                if ("watch?v=" in url || "/live/" in url || url.endsWith("/live")) url else "${url.trimEnd('/')}/live"
            }
            value.startsWith("@") -> "https://www.youtube.com/$value/live"
            value.startsWith("UC") && value.length == 24 -> "https://www.youtube.com/channel/$value/live"
            VIDEO_ID.matches(value) -> "https://www.youtube.com/watch?v=$value"
            else -> "https://www.youtube.com/@$value/live"
        }
    }

    fun directVideo(target: String): String? {
        val value = target.trim()
        if (VIDEO_ID.matches(value)) return value
        return Regex("(?:v=|/live/)([A-Za-z0-9_-]{11})").find(value)?.groupValues?.get(1)
    }

    /**
     * the id of the stream that is live right now, null if the channel is offline.
     */
    fun liveVideo(html: String): String? = LIVE_VIDEO.find(html)?.groupValues?.get(1)

    fun chat(html: String): Chat? {
        val key = API_KEY.find(html)?.groupValues?.get(1) ?: return null
        val version = CLIENT_VERSION.find(html)?.groupValues?.get(1) ?: return null
        val continuation = CONTINUATION.find(html)?.groupValues?.get(1) ?: return null
        return Chat(key, version, continuation)
    }
}
