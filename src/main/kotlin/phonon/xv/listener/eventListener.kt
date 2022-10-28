/**
 * Main event listener.
 */

package phonon.xv.listener

import java.time.LocalDateTime
import java.text.MessageFormat
import org.bukkit.ChatColor
import org.bukkit.attribute.Attribute
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.entity.Player
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Damageable
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockRedstoneEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityToggleSwimEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerChangedMainHandEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.player.PlayerToggleSprintEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.potion.PotionEffectType
import org.spigotmc.event.entity.EntityDismountEvent
import phonon.xv.XV
import phonon.xv.system.MountVehicleRequest
import phonon.xv.system.DismountVehicleRequest

public class EventListener(val plugin: JavaPlugin): Listener {
    @EventHandler(priority = EventPriority.NORMAL)
    public fun onPlayerEntityInteract(event: PlayerInteractAtEntityEvent) {
        val player = event.getPlayer()
        val entity = event.getRightClicked()
        val uuid = entity.getUniqueId()
        val vehicleData = XV.entityVehicleData[uuid]
        if ( vehicleData !== null ) {
            XV.mountRequests.add(MountVehicleRequest(
                player = player,
                elementId = vehicleData.elementId,
                componentType = vehicleData.componentType,
            ))
        }
    }

    /**
     * Notes:
     * - when right clicking, PlayerInteractEvent runs for both hands.
     * - client generates false LEFT_CLICK_AIR events, see
     * https://hub.spigotmc.org/jira/browse/SPIGOT-1955
     * https://hub.spigotmc.org/jira/browse/SPIGOT-5435?focusedCommentId=35082&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel
     * https://hub.spigotmc.org/jira/browse/SPIGOT-3049
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public fun onInteract(e: PlayerInteractEvent) {
        // println("onInteract")
        val player = e.getPlayer()
        val action = e.getAction()

        // println("ON PLAYER INTERACT EVENT: ${action} (hand=${e.getHand()}) (use=${e.useInteractedBlock()})")

        // ignores off hand event
        if ( e.getHand() != EquipmentSlot.HAND ) {
            return
        }

        if ( action == Action.LEFT_CLICK_AIR ||
            action == Action.LEFT_CLICK_BLOCK ||
            action == Action.RIGHT_CLICK_AIR ||
            action == Action.RIGHT_CLICK_BLOCK
        ) {
            XV.mountRequests.add(MountVehicleRequest(
                player = player,
                doRaycast = true,
            ))
        }
    }

    /**
     * Note: this fires when player logs off while riding a vehicle
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public fun onEntityDismount(event: EntityDismountEvent) {
        val player = event.getEntity()
        if ( player is Player ) {
            val entity = event.getDismounted()
            val uuid = entity.getUniqueId()
            val vehicleData = XV.entityVehicleData[uuid]
            if ( vehicleData !== null ) {
                XV.dismountRequests.add(DismountVehicleRequest(
                    player = player,
                    elementId = vehicleData.elementId,
                    componentType = vehicleData.componentType,
                ))
            }
        }
    }
}