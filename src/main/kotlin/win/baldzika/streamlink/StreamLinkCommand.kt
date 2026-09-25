package win.baldzika.streamlink

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import win.baldzika.streamlink.event.EventType
import win.baldzika.streamlink.event.Platform
import win.baldzika.streamlink.event.StreamEvent
import win.baldzika.streamlink.source.Status

class StreamLinkCommand(private val plugin: StreamLinkPlugin) {

    fun build(): LiteralCommandNode<CommandSourceStack> = Commands.literal("streamlink")
        .executes { status(it.source.sender) }
        .then(admin(Commands.literal("status")).executes { status(it.source.sender) })
        .then(admin(Commands.literal("reload")).executes { reload(it.source.sender) })
        .then(
            Commands.literal("pause")
                .requires { it.executor is Player && it.sender.hasPermission("streamlink.pause") }
                .executes { pause(it.source.executor as Player) },
        )
        .then(
            admin(Commands.literal("test")).then(
                Commands.argument("player", StringArgumentType.word())
                    .suggests { _, builder -> plugin.streams.settings.links.forEach { builder.suggest(it.player) }; builder.buildFuture() }
                    .then(
                        Commands.argument("platform", StringArgumentType.word())
                            .suggests { _, builder -> Platform.entries.forEach { builder.suggest(it.key) }; builder.buildFuture() }
                            .then(
                                Commands.argument("event", StringArgumentType.word())
                                    .suggests { _, builder -> EventType.entries.forEach { builder.suggest(it.key) }; builder.buildFuture() }
                                    .executes { test(it, null, null) }
                                    .then(
                                        Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                            .executes { test(it, DoubleArgumentType.getDouble(it, "amount"), null) }
                                            .then(
                                                Commands.argument("message", StringArgumentType.greedyString())
                                                    .executes { test(it, DoubleArgumentType.getDouble(it, "amount"), StringArgumentType.getString(it, "message")) },
                                            ),
                                    ),
                            ),
                    ),
            ),
        )
        .build()

    private fun <T : ArgumentBuilder<CommandSourceStack, T>> admin(node: T): T =
        node.requires { it.sender.hasPermission("streamlink.admin") }

    private fun status(sender: CommandSender): Int {
        if (!sender.hasPermission("streamlink.admin")) {
            return if (sender is Player) pause(sender) else 0
        }
        val connections = plugin.streams.connections
        sender.sendMessage(Component.text("StreamLink", NamedTextColor.LIGHT_PURPLE).append(Component.text(" ${plugin.streams.settings.rules.size} rules loaded", NamedTextColor.GRAY)))
        if (connections.isEmpty()) {
            sender.sendMessage(Component.text("no links set up, add one under links in config.yml", NamedTextColor.GRAY))
            return 1
        }
        connections.groupBy { it.link.player }.forEach { (player, list) ->
            sender.sendMessage(Component.text(player, NamedTextColor.WHITE))
            list.forEach { (_, source) ->
                val color = when (source.status) {
                    Status.LIVE -> NamedTextColor.GREEN
                    Status.OFFLINE, Status.CONNECTING -> NamedTextColor.GRAY
                    Status.RETRYING -> NamedTextColor.GOLD
                    Status.STOPPED -> NamedTextColor.DARK_GRAY
                }
                val detail = if (source.detail.isBlank()) "" else " (${source.detail})"
                sender.sendMessage(
                    Component.text("  ")
                        .append(Component.text(source.platform.display, TextColor.fromHexString(source.platform.color)))
                        .append(Component.text(" ${source.channel} ", NamedTextColor.GRAY))
                        .append(Component.text(source.status.name.lowercase() + detail, color)),
                )
            }
        }
        return 1
    }

    private fun reload(sender: CommandSender): Int {
        plugin.reload()
        sender.sendMessage(Component.text("reloaded, ${plugin.streams.connections.size} connections starting", NamedTextColor.GREEN))
        return 1
    }

    private fun pause(player: Player): Int {
        if (plugin.streams.link(player.name) == null) {
            player.sendMessage(Component.text("your account isn't linked to a stream", NamedTextColor.GRAY))
            return 0
        }
        val paused = plugin.streams.togglePause(player.name)
        player.sendMessage(
            if (paused) Component.text("stream events paused, chat still shows", NamedTextColor.GOLD)
            else Component.text("stream events are back on", NamedTextColor.GREEN),
        )
        return 1
    }

    private fun test(context: CommandContext<CommandSourceStack>, amount: Double?, message: String?): Int {
        val sender = context.source.sender
        val player = StringArgumentType.getString(context, "player")
        val platform = Platform.of(StringArgumentType.getString(context, "platform"))
        val type = EventType.of(StringArgumentType.getString(context, "event"))
        if (platform == null || type == null) {
            sender.sendMessage(Component.text("unknown platform or event", NamedTextColor.RED))
            return 0
        }
        val event = fake(platform, type, amount, message)
        plugin.streams.handle(player, event)
        sender.sendMessage(Component.text("sent a fake ${type.key} from ${platform.display} to $player", NamedTextColor.GREEN))
        return 1
    }

    private fun fake(platform: Platform, type: EventType, amount: Double?, message: String?): StreamEvent {
        val user = "tester"
        return when (type) {
            EventType.CHAT -> StreamEvent.Chat(platform, user, message ?: "hello from chat")
            EventType.FOLLOW -> StreamEvent.Follow(platform, user)
            EventType.SUB -> StreamEvent.Sub(platform, user, amount?.toInt() ?: 1)
            EventType.GIFT_SUBS -> StreamEvent.GiftSubs(platform, user, amount?.toInt() ?: 5)
            EventType.DONATION -> StreamEvent.Donation(platform, user, amount ?: 100.0, if (platform == Platform.TWITCH) "bits" else "USD", message.orEmpty())
            EventType.GIFT -> StreamEvent.Gift(platform, user, message ?: "Rose", 1, amount?.toInt() ?: 1)
            EventType.RAID -> StreamEvent.Raid(platform, user, amount?.toInt() ?: 25)
            EventType.LIKE -> StreamEvent.Like(platform, user, amount?.toInt() ?: 15)
            EventType.SHARE -> StreamEvent.Share(platform, user)
        }
    }
}
