package win.baldzika.streamlink.rule

/**
 * who a rule's player actions hit. the linked streamer, everyone online, or a list of names.
 */
data class Targets(val everyone: Boolean, val streamer: Boolean, val players: Set<String>) {

    /**
     * the online names this should hit, compared without case.
     */
    fun resolve(streamer: String, online: Collection<String>): List<String> {
        if (everyone) return online.toList()
        val wanted = players.mapTo(HashSet()) { it.lowercase() }
        if (this.streamer) wanted += streamer.lowercase()
        return online.filter { it.lowercase() in wanted }
    }

    companion object {

        val STREAMER = Targets(everyone = false, streamer = true, players = emptySet())
        val EVERYONE = Targets(everyone = true, streamer = false, players = emptySet())

        private val NAME = Regex("[A-Za-z0-9_]{1,16}")

        /**
         * "streamer", "all", or names split by commas like "Steve, Alex". streamer can be mixed into the list.
         */
        fun parse(text: String): Targets {
            val parts = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            require(parts.isNotEmpty()) { "targets is empty, use streamer, all or player names" }
            if (parts.any { it.equals("all", ignoreCase = true) }) {
                require(parts.size == 1) { "'all' already means everyone, drop the other names" }
                return EVERYONE
            }
            val names = parts.filterNot { it.equals("streamer", ignoreCase = true) }
            names.firstOrNull { !NAME.matches(it) }?.let { throw IllegalArgumentException("'$it' isn't a valid player name") }
            return Targets(everyone = false, streamer = names.size != parts.size, players = names.toSet())
        }
    }
}
