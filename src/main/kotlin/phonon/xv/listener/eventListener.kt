/**
 * Main event listener.
 */

package phonon.xv.listener

import java.time.LocalDateTime
import java.text.MessageFormat
import org.bukkit.ChatColor
import org.bukkit.attribute.Attribute
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
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.persistence.PersistentDataType
import org.spigotmc.event.entity.EntityDismountEvent
import phonon.xv.XV
import phonon.xv.ITEM_KEY_PROTOTYPE
import phonon.xv.system.CreateVehicleRequest
import phonon.xv.system.CreateVehicleReason
import phonon.xv.system.MountVehicleRequest
import phonon.xv.system.DismountVehicleRequest


public class EventListener(val xv: XV): Listener {
    @EventHandler(priority = EventPriority.NORMAL)
    public fun onPlayerEntityInteract(event: PlayerInteractAtEntityEvent) {
        val player = event.getPlayer()
        val entity = event.getRightClicked()
        val uuid = entity.getUniqueId()
        val vehicleData = xv.entityVehicleData[uuid]
        if ( vehicleData !== null ) {
            xv.mountRequests.add(MountVehicleRequest(
                player = player,
                elementId = vehicleData.elementId,
                layout = vehicleData.layout,
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

        if ( 
            action == Action.RIGHT_CLICK_AIR ||
            action == Action.RIGHT_CLICK_BLOCK
        ) {
            // if player item is spawn vehicle item, handle spawning vehicle
            val itemInHand = player.getInventory().getItemInMainHand()
            if ( itemInHand.type == xv.config.materialVehicle ) { // TODO: replace getItemMeta check with raw nms for performance
                val itemData = itemInHand.getItemMeta().getPersistentDataContainer()
                val prototypeName = itemData.get(ITEM_KEY_PROTOTYPE, PersistentDataType.STRING)
                if ( prototypeName !== null ) {
                    val vehiclePrototype = xv.vehiclePrototypes[prototypeName]
                    if ( vehiclePrototype !== null ) {
                        println("SPAWN VEHICLE REQUEST: ${vehiclePrototype.name}")
                        // TODO: add SPAWN vehicle request not CREATE
                        xv.createRequests.add(
                            CreateVehicleRequest(
                                vehiclePrototype,
                                CreateVehicleReason.NEW,
                                location = player.location,
                                player = player,
                            )
                        )
                    }
                }
            }
            else {
                // else, try mount vehicle request
                xv.mountRequests.add(MountVehicleRequest(
                    player = player,
                    doRaycast = true,
                ))
            }
        }
        else if (
            action == Action.LEFT_CLICK_AIR ||
            action == Action.LEFT_CLICK_BLOCK
        ) {
            xv.mountRequests.add(MountVehicleRequest(
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
            val vehicleData = xv.entityVehicleData[uuid]
            if ( vehicleData !== null ) {
                xv.dismountRequests.add(DismountVehicleRequest(
                    player = player,
                    elementId = vehicleData.elementId,
                    componentType = vehicleData.componentType,
                ))
            }
        }
    }
}