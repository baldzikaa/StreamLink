package win.baldzika.streamlink

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import win.baldzika.streamlink.rule.Action
import win.baldzika.streamlink.rule.Placeholders
import win.baldzika.streamlink.rule.Rule
import kotlin.random.Random

/**
 * runs rule actions. anything touching the player runs on the player's own thread, console commands on the global region.
 */
class ActionRunner(private val plugin: Plugin) {

    private val mini = MiniMessage.miniMessage()

    fun run(player: String, rule: Rule, values: Map<String, String>, settings: Settings) {
        val target = Bukkit.getPlayerExact(player)
        if (target == null && settings.onlyWhenOnline) return
        val (global, personal) = rule.actions.partition { it is Action.Command || it is Action.Broadcast }

        if (global.isNotEmpty()) {
            Bukkit.getGlobalRegionScheduler().execute(plugin) {
                global.forEach { action -> safely(rule, action) { runGlobal(action, values) } }
            }
        }
        if (target != null && personal.isNotEmpty()) {
            target.scheduler.run(plugin, { personal.forEach { action -> safely(rule, action) { runFor(target, action, values, settings) } } }, null)
        }
    }

    // one broken action shouldn't stop the rest of the rule
    private fun safely(rule: Rule, action: Action, block: () -> Unit) {
        runCatching(block).onFailure {
            plugin.logger.warning("rule '${rule.name}' couldn't run ${action.javaClass.simpleName.lowercase()}: ${it.message}")
        }
    }

    fun text(template: String, values: Map<String, String>): Component = mini.deserialize(Placeholders.text(template, values))

    /**
     * names that won't resolve on this server, so typos show up on startup instead of mid stream.
     */
    fun problems(rule: Rule): List<String> = rule.actions.mapNotNull { action ->
        when (action) {
            is Action.Spawn -> if (entity(action.entity) == null) "unknown entity '${action.entity}'" else null
            is Action.Effect -> if (effect(action.effect) == null) "unknown effect '${action.effect}'" else null
            is Action.Give -> if (Material.matchMaterial(action.item)?.isItem != true) "unknown item '${action.item}'" else null
            is Action.Sound -> if (!Key.parseable(action.sound.lowercase())) "bad sound name '${action.sound}', use something like entity.player.levelup" else null
            else -> null
        }
    }

    private fun runGlobal(action: Action, values: Map<String, String>) {
        when (action) {
            is Action.Command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Placeholders.command(action.command, values))
            is Action.Broadcast -> {
                // same path as chat relay, Bukkit.broadcast didn't reach players on folia
                val message = text(action.text, values)
                Bukkit.getConsoleSender().sendMessage(message)
                Bukkit.getOnlinePlayers().forEach { it.sendMessage(message) }
            }
            else -> Unit
        }
    }

    private fun runFor(player: Player, action: Action, values: Map<String, String>, settings: Settings) {
        when (action) {
            is Action.Message -> player.sendMessage(text(action.text, values))
            is Action.ActionBar -> player.sendActionBar(text(action.text, values))
            is Action.Title -> player.showTitle(Title.title(text(action.title, values), text(action.subtitle, values)))
            is Action.Sound -> player.playSound(
                Sound.sound(Key.key(action.sound.lowercase()), Sound.Source.MASTER, action.volume, action.pitch),
                Sound.Emitter.self(),
            )
            is Action.Spawn -> {
                val type = entity(action.entity) ?: return
                repeat(amount(action.amount, values, settings.maxSpawn)) {
                    player.world.spawnEntity(near(player.location, action.radius), type)
                }
            }
            is Action.Effect -> {
                val type = effect(action.effect) ?: return
                player.addPotionEffect(PotionEffect(type, action.seconds * 20, (action.level - 1).coerceAtLeast(0)))
            }
            is Action.Give -> {
                val material = Material.matchMaterial(action.item) ?: return
                val amount = amount(action.amount, values, settings.maxGive)
                if (amount > 0) {
                    player.inventory.addItem(ItemStack.of(material, amount)).values.forEach { leftover ->
                        player.world.dropItemNaturally(player.location, leftover)
                    }
                }
            }
            is Action.Lightning -> if (action.harmless) {
                player.world.strikeLightningEffect(player.location)
            } else {
                player.world.strikeLightning(player.location)
            }
            is Action.Command, is Action.Broadcast -> Unit
        }
    }

    private fun amount(template: String, values: Map<String, String>, max: Int): Int =
        (Placeholders.fill(template, values).trim().toDoubleOrNull() ?: 1.0).toInt().coerceIn(0, max)

    // stays close so it lands in the same folia region as the player
    private fun near(origin: Location, radius: Double): Location {
        val angle = Random.nextDouble(0.0, Math.PI * 2)
        val distance = Random.nextDouble(radius.coerceAtLeast(0.0))
        val spot = origin.clone().add(kotlin.math.cos(angle) * distance, 0.0, kotlin.math.sin(angle) * distance)
        spot.y = maxOf(origin.y, spot.world.getHighestBlockYAt(spot).toDouble() + 1)
        return spot
    }

    private fun entity(name: String): EntityType? =
        NamespacedKey.fromString(name.lowercase())?.let { Registry.ENTITY_TYPE.get(it) }?.takeIf { it.isSpawnable }

    private fun effect(name: String) = NamespacedKey.fromString(name.lowercase())?.let { Registry.MOB_EFFECT.get(it) }
}
