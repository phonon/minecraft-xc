/**
 * Main event listener.
 */

package phonon.xc.listeners

import org.bukkit.ChatColor
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.entity.Player
import org.bukkit.entity.EntityType
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityToggleSwimEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerChangedMainHandEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.player.PlayerToggleSprintEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerAnimationEvent
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent 
import phonon.xc.XC
import phonon.xc.utils.Message
import phonon.xc.gun.PlayerAimDownSightsRequest
import phonon.xc.gun.PlayerGunSelectRequest
import phonon.xc.gun.PlayerGunReloadRequest
import phonon.xc.gun.PlayerGunShootRequest
import phonon.xc.gun.PlayerAutoFireRequest
import phonon.xc.gun.PlayerGunCleanupRequest
import phonon.xc.gun.ItemGunCleanupRequest
import phonon.xc.gun.AmmoInfoMessagePacket

// TODO: in future need to select NMS version
import phonon.xc.compatibility.v1_16_R3.gun.crawl.*
import phonon.xc.compatibility.v1_16_R3.gun.item.*


public class EventListener(val plugin: JavaPlugin): Listener {
    @EventHandler
    public fun onPlayerJoin(e: PlayerJoinEvent) {
        val player = e.player
        val playerId = player.getUniqueId()

        // stop crawling (cleans up old crawl state)
        XC.crawlStopQueue.add(CrawlStop(player))

        // initialize XC engine player data maps
        XC.playerPreviousLocation[playerId] = player.getLocation()

        // if player joins and is holding a gun or custom wep,
        // do handle selection event
        getGunInHand(player)?.let { gun -> 
            XC.playerGunSelectRequests.add(PlayerGunSelectRequest(
                player = player,
            ))
        }
    }

    @EventHandler
    public fun onPlayerQuit(e: PlayerQuitEvent) {
        val player = e.player
        val playerId = player.getUniqueId()

        // remove player from XC player data maps
        XC.playerShootDelay.remove(playerId)
        XC.playerSpeed.remove(playerId)
        XC.playerPreviousLocation.remove(playerId)
        
        // stop crawling (cleans up old crawl state)
        XC.crawlStopQueue.add(CrawlStop(player))

        // if player leaves and is holding a gun or custom wep,
        // do reload cleanup
        getGunInHand(player)?.let { gun -> 
            XC.PlayerGunCleanupRequests.add(PlayerGunCleanupRequest(
                player = player,
            ))
        }
    }

    @EventHandler
    public fun onPlayerDeath(e: PlayerDeathEvent) {
        val player: Player = e.entity

        // stop crawling
        if ( XC.isCrawling(player) ) {
            XC.crawlStopQueue.add(CrawlStop(player))
        }

        // if player dies and is holding a gun or custom wep,
        // do reload cleanup
        getGunInHand(player)?.let { gun -> 
            XC.PlayerGunCleanupRequests.add(PlayerGunCleanupRequest(
                player = player,
            ))
        }

        // ==========================================================
        // CUSTOM DEATH MESSAGE HANDLING
        // ==========================================================
        // TODO
        // TODO
        // TODO
    }

    @EventHandler
    public fun onPlayerChangeArmor(e: PlayerArmorChangeEvent) {

    }

    @EventHandler(ignoreCancelled = true)
    public fun onToggleSneak(e: PlayerToggleSneakEvent) {
        // println("toggleSneak")
        val player = e.player

        // stop crawling
        if ( XC.isCrawling(player) ) {
            XC.crawlStopQueue.add(CrawlStop(player))
        }
        
        getGunInHand(player)?.let { gun -> 
            // Message.print(player, "Reloading...")
            XC.playerAimDownSightsRequests.add(PlayerAimDownSightsRequest(
                player = player,
            ))
        }
    }

    /**
     * When crawling, need to cancel this swim event. Otherwise other
     * players will not see player in swimming animation mode.
     */
    @EventHandler
    public fun onEntityToggleSwim(e: EntityToggleSwimEvent) {
        // println("onEntityToggleSwim ${e.getEntity()}")
        if ( !e.isSwimming() && e.getEntityType() == EntityType.PLAYER ) {
            if( XC.crawling.contains(e.getEntity().getUniqueId()) ) {
                e.setCancelled(true)
            }
        }
    }

    // @EventHandler(ignoreCancelled = true)
    // public fun onToggleSprint(e: PlayerToggleSprintEvent) {
    //     // println("toggleSprint")

    //     // check if gun cancels sprinting
    //     // setSprinting(false) DOES NOT WORK!!!!
    //     // if ( e.isSprinting() ) {
    //     //     val player = e.player
    //     //     val equipment = player.equipment
    //     //     if ( equipment == null ) return
    
    //     //     // disable sprint if gun is no sprint
    //     //     val itemMainHand = equipment.itemInMainHand
    //     //     if ( itemMainHand.type == XC.config.materialGun ) {
    //     //         getGunFromItem(itemMainHand)?.let { gun -> 
    //     //             if ( gun.equipNoSprint ) {
    //     //                 println("Stop sprint!")
    //     //                 player.setSprinting(false)
    //     //                 e.setCancelled(true)
    //     //             }
    //     //         }
    //     //     }
    //     // }
    // }

    @EventHandler(ignoreCancelled = true)
    public fun onDropItem(e: PlayerDropItemEvent) {
        val itemEntity = e.getItemDrop()
        val item = itemEntity.getItemStack()
        
        // remove invalid aim down sights model item
        if ( XC.isAimDownSightsModel(item) ) {
            itemEntity.remove()
        }

        getGunFromItem(item)?.let { gun -> 
            XC.ItemGunCleanupRequests.add(ItemGunCleanupRequest(
                itemEntity = itemEntity,
                onDrop = true,
            ))
        }
    }
    

    @EventHandler(ignoreCancelled = true)
    public fun onPickupItem(e: EntityPickupItemEvent) {
        val itemEntity = e.getItem()
        val item = itemEntity.getItemStack()

        // remove invalid aim down sights model item
        if ( XC.isAimDownSightsModel(item) ) {
            itemEntity.remove()
        }
        
        getGunFromItem(item)?.let { gun -> 
            XC.ItemGunCleanupRequests.add(ItemGunCleanupRequest(
                itemEntity = itemEntity,
                onDrop = false,
            ))
        }
    }
    
    /**
     * When player swaps item in main hand (item selected in hotbar)
     */
    @EventHandler(ignoreCancelled = true)
    public fun onItemSelect(e: PlayerItemHeldEvent) {
        val player = e.player
        val inventory = player.getInventory()

        // check if previous slot was a gun
        val previousSlot = e.getPreviousSlot()
        getGunInSlot(player, previousSlot)?.let { gun -> 
            XC.PlayerGunCleanupRequests.add(PlayerGunCleanupRequest(
                player = player,
                inventorySlot = previousSlot,
            ))

            // remove any aim down sights models
            XC.removeAimDownSightsOffhandModel(player)
        }

        val mainHandSlot = e.getNewSlot()
        getGunInSlot(player, mainHandSlot)?.let { gun -> 
            XC.playerGunSelectRequests.add(PlayerGunSelectRequest(
                player = player,
            ))
        }
    }

    /**
     * When player swaps item between main/offhand.
     */
    @EventHandler(ignoreCancelled = true)
    public fun onItemSwapHand(e: PlayerSwapHandItemsEvent) {
        val player = e.player
        
        getGunInHand(player)?.let { gun -> 
            // Message.print(player, "Reloading...")
            XC.playerReloadRequests.add(PlayerGunReloadRequest(
                player = player,
            ))
            e.setCancelled(true)
        }
    }
    
    /**
     * Note when right clicking, PlayerInteractEvent runs for both hands.
     * This can cause a condition where:
     * - right hand runs first -> gun iron sight on
     * - offhand runs immediately after -> gun iron sight off
     * 
     * Client generates false LEFT_CLICK_AIR events, see
     * https://hub.spigotmc.org/jira/browse/SPIGOT-1955
     * https://hub.spigotmc.org/jira/browse/SPIGOT-5435?focusedCommentId=35082&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel
     * https://hub.spigotmc.org/jira/browse/SPIGOT-3049
     * theres nothing i can do right now, at best can delay firing by 1 tick which catches
     * SOME of the issues
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public fun onInteract(e: PlayerInteractEvent) {
        // println("onInteract")
        val player = e.getPlayer()
        val action = e.getAction()

        // println("ON PLAYER INTERACT EVENT: ${action}")

        // ignores off hand event, physical events, or cancelled block interact event
        // this event runs twice, 2nd main hand event is cancelled block interact event
        // ISSUE: when crawling, having the interacted block is glitchy???
        // TODO: INVESTIGATE DEAD ZONES
        if ( e.getHand() != EquipmentSlot.HAND || action == Action.PHYSICAL || ( action == Action.RIGHT_CLICK_BLOCK && e.useInteractedBlock() == Event.Result.DENY ) ) {
        // if ( e.getHand() != EquipmentSlot.HAND || action == Action.PHYSICAL ) {
            return
        }

        // left click: single fire or burst
        if ( action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK ) {
            val equipment = player.equipment
            if ( equipment == null ) return

            val itemMainHand = equipment.itemInMainHand
            if ( itemMainHand.type == XC.config.materialGun ) {
                getGunFromItem(itemMainHand)?.let { gun -> 
                    // Message.print(player, "Trying to shoot")
                    XC.playerShootRequests.add(PlayerGunShootRequest(
                        player = player,
                    ))

                    // ignore block interact event
                    e.setUseInteractedBlock(Event.Result.DENY)
                }
            }
            else if ( itemMainHand.type == XC.config.materialMisc ) {
                // TODO: misc weapon behavior (grenade, etc.)
            }
            else if ( itemMainHand.type == XC.config.materialArmor ) {
                // TODO: put armor (helmet) on
            }
        }
        // right click: auto fire
        else if ( action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK ) {
            getGunInHand(player)?.let { gun -> 
                // Message.print(player, "auto firing request")
                if ( gun.autoFire ) {
                    XC.playerAutoFireRequests.add(PlayerAutoFireRequest(
                        player = player,
                    ))

                    // ignore block interact event
                    e.setUseInteractedBlock(Event.Result.DENY)
                }
            }
        }
    }

    // @EventHandler
    // public fun onInteractAt(e: PlayerInteractAtEntityEvent) {
    //     // println("onInteractAtEntityEvent")
    // }
    
    // @EventHandler
    // public fun onAnimation(e: PlayerAnimationEvent) {
    //     // println("onAnimation")
    //     // val player = e.player
    //     // val equipment = player.equipment
    //     // if ( equipment == null ) return

    //     // val itemMainHand = equipment.itemInMainHand
    //     // if ( itemMainHand.type == XC.config.materialGun ) {
    //     //     Message.print(player, "Firing")
    //     //     XC.shootGun(player, XC.gunDebug)
    //     // }
    // }

	/**
	 * Required for handling right click attacking entity, route to gun handler event
	 */
	@EventHandler
	public fun onHit(e: EntityDamageByEntityEvent) {
        // println("onHit")
        val damager = e.getDamager()
		if ( damager is Player ) {
			val player: Player = damager
            
            getGunInHand(player)?.let { gun -> 
                XC.playerShootRequests.add(PlayerGunShootRequest(
                    player = player,
                ))
            }

            // TODO: melee weapon damage adjust
        }
    }
}