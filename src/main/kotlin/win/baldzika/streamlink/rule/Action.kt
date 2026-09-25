package win.baldzika.streamlink.rule

sealed interface Action {

    data class Message(val text: String) : Action

    data class Broadcast(val text: String) : Action

    data class Title(val title: String, val subtitle: String) : Action

    data class ActionBar(val text: String) : Action

    data class Sound(val sound: String, val volume: Float, val pitch: Float) : Action

    data class Spawn(val entity: String, val amount: String, val radius: Double) : Action

    data class Effect(val effect: String, val seconds: Int, val level: Int) : Action

    data class Give(val item: String, val amount: String) : Action

    data class Lightning(val harmless: Boolean) : Action

    data class Command(val command: String) : Action

    companion object {

        fun parse(raw: Map<*, *>): Action {
            require(raw.size == 1) { "each action needs exactly one type, got ${raw.keys}" }
            val (key, value) = raw.entries.first()
            val options = value as? Map<*, *> ?: emptyMap<Any, Any>()
            fun text(name: String, fallback: String = "") = (options[name] ?: fallback).toString()
            fun number(name: String, fallback: Double) = options[name]?.toString()?.toDoubleOrNull() ?: fallback

            return when (key.toString()) {
                "message" -> Message(value.toString())
                "broadcast" -> Broadcast(value.toString())
                "actionbar" -> ActionBar(value.toString())
                "command" -> Command(value.toString().removePrefix("/"))
                "title" -> if (value is Map<*, *>) Title(text("title"), text("subtitle")) else Title(value.toString(), "")
                "sound" -> if (value is Map<*, *>) {
                    Sound(text("sound"), number("volume", 1.0).toFloat(), number("pitch", 1.0).toFloat())
                } else {
                    Sound(value.toString(), 1f, 1f)
                }
                "spawn" -> if (value is Map<*, *>) {
                    Spawn(text("entity"), text("amount", "1"), number("radius", 4.0))
                } else {
                    Spawn(value.toString(), "1", 4.0)
                }
                "effect" -> Effect(text("type"), number("seconds", 10.0).toInt(), number("level", 1.0).toInt())
                "give" -> if (value is Map<*, *>) Give(text("item"), text("amount", "1")) else Give(value.toString(), "1")
                "lightning" -> Lightning(options["harmless"]?.toString()?.toBooleanStrictOrNull() ?: true)
                else -> throw IllegalArgumentException("unknown action '$key'")
            }
        }
    }
}
