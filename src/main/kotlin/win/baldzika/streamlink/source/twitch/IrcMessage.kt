package win.baldzika.streamlink.source.twitch

data class IrcMessage(
    val tags: Map<String, String>,
    val prefix: String?,
    val command: String,
    val params: List<String>,
) {

    val nick: String? get() = prefix?.takeIf { '!' in it }?.substringBefore('!')

    val trailing: String get() = params.lastOrNull().orEmpty()

    companion object {

        fun parse(line: String): IrcMessage? {
            var rest = line.trimEnd('\r', '\n')
            if (rest.isEmpty()) return null

            var tags = emptyMap<String, String>()
            if (rest.startsWith('@')) {
                val end = rest.indexOf(' ')
                if (end < 0) return null
                tags = rest.substring(1, end).split(';').associate { pair ->
                    val key = pair.substringBefore('=')
                    key to unescape(pair.substringAfter('=', ""))
                }
                rest = rest.substring(end + 1).trimStart()
            }

            var prefix: String? = null
            if (rest.startsWith(':')) {
                val end = rest.indexOf(' ')
                if (end < 0) return null
                prefix = rest.substring(1, end)
                rest = rest.substring(end + 1).trimStart()
            }

            val params = mutableListOf<String>()
            val trailingAt = rest.indexOf(" :")
            val head = if (trailingAt >= 0) rest.substring(0, trailingAt) else rest
            val words = head.split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty()) return null
            params += words.drop(1)
            if (trailingAt >= 0) params += rest.substring(trailingAt + 2)
            return IrcMessage(tags, prefix, words.first(), params)
        }

        // https://ircv3.net/specs/extensions/message-tags#escaping-values
        private fun unescape(value: String): String {
            if ('\\' !in value) return value
            val out = StringBuilder(value.length)
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    out.append(
                        when (value[i + 1]) {
                            's' -> ' '
                            ':' -> ';'
                            'r' -> '\r'
                            'n' -> '\n'
                            else -> value[i + 1]
                        },
                    )
                    i += 2
                } else {
                    if (c != '\\') out.append(c)
                    i++
                }
            }
            return out.toString()
        }
    }
}
