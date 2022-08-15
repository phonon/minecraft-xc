/**
 * Main event listener.
 */

package phonon.xc.listeners

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
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent 
import phonon.xc.XC
import phonon.xc.armor.PlayerWearHatRequest
import phonon.xc.gun.PlayerAimDownSightsRequest
import phonon.xc.gun.PlayerGunSelectRequest
import phonon.xc.gun.PlayerGunReloadRequest
import phonon.xc.gun.PlayerGunShootRequest
import phonon.xc.gun.PlayerAutoFireRequest
import phonon.xc.gun.PlayerGunCleanupRequest
import phonon.xc.gun.ItemGunCleanupRequest
import phonon.xc.gun.AmmoInfoMessagePacket
import phonon.xc.landmine.LandmineActivationRequest
import phonon.xc.throwable.ReadyThrowableRequest
import phonon.xc.throwable.ThrowThrowableRequest
import phonon.xc.throwable.DroppedThrowable
import phonon.xc.utils.Message
import phonon.xc.utils.death.PlayerDeathRecord
import phonon.xc.utils.death.XcPlayerDeathEvent
import phonon.xc.utils.damage.damageAfterArmorAndResistance

// TODO: in future need to select NMS version
import phonon.xc.compatibility.v1_16_R3.gun.crawl.*
import phonon.xc.compatibility.v1_16_R3.gun.item.*
import phonon.xc.compatibility.v1_16_R3.armor.item.*
import phonon.xc.compatibility.v1_16_R3.throwable.item.*
import phonon.xc.compatibility.v1_16_R3.melee.item.getMeleeInHandUnchecked
import phonon.xc.compatibility.v1_16_R3.item.getItemTypeInHand
import phonon.xc.compatibility.v1_16_R3.item.setItemArmorNMS


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
            XC.playerGunCleanupRequests.add(PlayerGunCleanupRequest(
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
            XC.playerGunCleanupRequests.add(PlayerGunCleanupRequest(
                player = player,
            ))
        }

        // ==========================================================
        // CUSTOM DEATH MESSAGE HANDLING
        // ==========================================================
        // check if custom death message exists
        val playerId = player.getUniqueId()

        val customDeathEvent = XC.deathEvents.remove(playerId)
        if ( customDeathEvent != null ) {
            // unpack death event from a weapon
            val (
                player,
                killer,
                weaponType,
                weaponId,
                weaponMaterial,
            ) = customDeathEvent

            // println("CUSTOM DEATH EVENT:")
            // println("player: $player")
            // println("killer: $killer")
            // println("weaponType: $weaponType")
            // println("weaponId: $weaponId")

            // try to get weapon death message
            val playerName = player.getName()
            val killerName = killer?.getName() ?: "None"
            val killerUUID = killer?.getUniqueId().toString() ?: "None"
            var deathMessage = ""
            var deathCause = ""

            try {
                when ( weaponType ) {
                    XC.ITEM_TYPE_GUN -> {
                        XC.guns[weaponId]?.let { weapon -> 
                            deathCause = weapon.itemName
                            deathMessage = MessageFormat.format(
                                weapon.deathMessage,
                                playerName,
                                killerName,
                                weapon.itemName,
                            )
                            e.setDeathMessage(deathMessage)
                        }
                    }
                    
                    XC.ITEM_TYPE_THROWABLE -> {
                        XC.throwable[weaponId]?.let { weapon -> 
                            deathCause = weapon.itemName
                            deathMessage = MessageFormat.format(
                                weapon.deathMessage,
                                playerName,
                                killerName,
                                weapon.itemName,
                            )
                            e.setDeathMessage(deathMessage)
                        }
                    }

                    XC.ITEM_TYPE_LANDMINE -> {
                        XC.landmines[weaponMaterial]?.let { weapon -> 
                            deathCause = weapon.itemName
                            deathMessage = MessageFormat.format(
                                weapon.deathMessage,
                                playerName,
                                weapon.itemName,
                            )
                            e.setDeathMessage(deathMessage)
                        }
                    }
                }
            } catch ( err: Exception ) {
                err.printStackTrace()
                XC.logger?.severe("Failed to get death message for weapon: type=$weaponType id=$weaponId")
            }

            // create death record
            XC.playerDeathRecords.add(PlayerDeathRecord(
                timestamp = LocalDateTime.now(),
                playerName = playerName,
                playerUUID = playerId.toString(),
                killerName = killerName,
                killerUUID = killerUUID,
                deathCause = deathCause,
                deathMessage = deathMessage,
            ))

        }
        else {
            val playerName = player.getName()
            var deathCause = "unknown"
            var deathMessage = ""
            // TODO: can we even find these?
            val killerName = ""
            val killerUUID = ""

            // try to get death cause. for explosions + wither poison, do special messages
            val lastDamageEvent = player.getLastDamageCause()
            if ( lastDamageEvent != null ) {
                val damageCause = lastDamageEvent.getCause()

                if ( damageCause == DamageCause.BLOCK_EXPLOSION || damageCause == DamageCause.ENTITY_EXPLOSION ) {
                    deathMessage = MessageFormat.format(
                        XC.config.deathMessageExplosion,
                        playerName,
                    )
                    e.setDeathMessage(deathMessage)
                }
                else if ( damageCause == DamageCause.WITHER ) {
                    deathMessage = MessageFormat.format(
                        XC.config.deathMessageWither,
                        playerName,
                    )
                    e.setDeathMessage(deathMessage)
                } else {
                    deathMessage = e.getDeathMessage() ?: ""
                }
            } else {
                deathMessage = e.getDeathMessage() ?: ""
            }

            XC.playerDeathRecords.add(PlayerDeathRecord(
                timestamp = LocalDateTime.now(),
                playerName = playerName,
                playerUUID = playerId.toString(),
                killerName = killerName,
                killerUUID = killerUUID,
                deathCause = deathCause,
                deathMessage = deathMessage,
            ))
        }
    }

    @EventHandler
    public fun onPlayerRespawn(e: PlayerRespawnEvent) {
        // clears any redundant death event messages that may have
        // accumulated on the player
        val playerId = e.player.getUniqueId()
        XC.deathEvents.remove(playerId)
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
        } else {
            when ( item.type ) {
                // gun drop item: cleanup
                XC.config.materialGun -> {
                    getGunFromItem(item)?.let {
                        XC.itemGunCleanupRequests.add(ItemGunCleanupRequest(
                            itemEntity = itemEntity,
                            onDrop = true,
                        ))
                    }
                }

                // throwable drop item: cancel and do throw request
                XC.config.materialThrowable -> {
                    getThrowableFromItem(item)?.let {
                        XC.droppedThrowables.add(DroppedThrowable(
                            player = e.player,
                            itemEntity = itemEntity,
                        ))
                    }
                }

                else -> {}
            }
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
            XC.itemGunCleanupRequests.add(ItemGunCleanupRequest(
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
            XC.playerGunCleanupRequests.add(PlayerGunCleanupRequest(
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

        // println("ON PLAYER INTERACT EVENT: ${action} (hand=${e.getHand()}) (use=${e.useInteractedBlock()})")

        // ignores off hand event, physical events, or cancelled block interact event
        // this event runs twice, 2nd main hand event is cancelled block interact event
        // ISSUE: when crawling, having the interacted block is glitchy???
        // TODO: INVESTIGATE DEAD ZONES
        if ( e.getHand() != EquipmentSlot.HAND || action == Action.PHYSICAL ) {
            return
        }

        if ( action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK ) {
            when ( getItemTypeInHand(player) ) {
                // gun left click: single fire or burst
                XC.ITEM_TYPE_GUN -> {
                    getGunInHandUnchecked(player)?.let { gun -> 
                        // Message.print(player, "Trying to shoot")
                        XC.playerShootRequests.add(PlayerGunShootRequest(
                            player = player,
                        ))

                        // ignore block interact event
                        e.setUseInteractedBlock(Event.Result.DENY)
                    }
                }

                // throwable left click: readies throwable
                XC.ITEM_TYPE_THROWABLE -> {
                    getThrowableInHandUnchecked(player)?.let {
                        XC.readyThrowableRequests.add(ReadyThrowableRequest(
                            player = player,
                        ))

                        // ignore block interact event
                        e.setUseInteractedBlock(Event.Result.DENY)
                    }
                }
            }
        }
        else if ( action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK ) {
            when ( getItemTypeInHand(player) ) {
                // gun right click: auto fire
                XC.ITEM_TYPE_GUN -> {
                    getGunInHandUnchecked(player)?.let { gun -> 
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
                
                // throwable right click: throw item
                XC.ITEM_TYPE_THROWABLE -> {
                    getThrowableInHandUnchecked(player)?.let {
                        XC.throwThrowableRequests.add(ThrowThrowableRequest(
                            player = player,
                        ))

                        // ignore block interact event
                        e.setUseInteractedBlock(Event.Result.DENY)
                    }
                }

                // hat right click: wear
                XC.ITEM_TYPE_HAT -> {
                    getHatInHandUnchecked(player)?.let {
                        XC.wearHatRequests.add(PlayerWearHatRequest(
                            player = player,
                        ))

                        // ignore block interact event
                        e.setUseInteractedBlock(Event.Result.DENY)
                    }
                }
            }
        }
    }

    /**
     * Certain entities do not trigger a PlayerInteractEvent when right clicking them.
     * These are entities with special handling for right click (not exhaustive):
     * - ArmorStand
     * - Villager
     * - Horse
     * 
     * These specific entities require PlayerInteractAtEntityEvent handling for
     * right clicks.
     */
    @EventHandler
    public fun onInteractAt(e: PlayerInteractAtEntityEvent) {
        // println("onInteractAtEntityEvent ${e.getRightClicked()}")

        // only main hand right click
        if ( e.getHand() != EquipmentSlot.HAND ) {
            return
        }

        val clickedEntityType = e.getRightClicked().type
        if ( clickedEntityType == EntityType.ARMOR_STAND ||
            clickedEntityType == EntityType.HORSE ||
            clickedEntityType == EntityType.VILLAGER
        ) {
            val player = e.getPlayer()
            
            when ( getItemTypeInHand(player) ) {
                // gun right click: auto fire
                XC.ITEM_TYPE_GUN -> {
                    getGunInHandUnchecked(player)?.let { gun -> 
                        // Message.print(player, "auto firing request")
                        if ( gun.autoFire ) {
                            XC.playerAutoFireRequests.add(PlayerAutoFireRequest(
                                player = player,
                            ))

                            // ignore interact event
                            e.setCancelled(true)
                        }
                    }
                }
                
                // throwable right click: throw item
                XC.ITEM_TYPE_THROWABLE -> {
                    getThrowableInHandUnchecked(player)?.let {
                        XC.throwThrowableRequests.add(ThrowThrowableRequest(
                            player = player,
                        ))

                        // ignore interact event
                        e.setCancelled(true)
                    }
                }

                // hat right click: wear
                XC.ITEM_TYPE_HAT -> {
                    getHatInHandUnchecked(player)?.let {
                        XC.wearHatRequests.add(PlayerWearHatRequest(
                            player = player,
                        ))

                        // ignore interact event
                        e.setCancelled(true)
                    }
                }
            }
        }
    }
    
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
            
            when ( getItemTypeInHand(player) ) {
                // gun left click: single fire or burst
                XC.ITEM_TYPE_GUN -> {
                    getGunInHandUnchecked(player)?.let { gun -> 
                        XC.playerShootRequests.add(PlayerGunShootRequest(
                            player = player,
                        ))
                    }
                }

                // throwable left click: readies throwable
                XC.ITEM_TYPE_THROWABLE -> {
                    getThrowableInHandUnchecked(player)?.let {
                        XC.readyThrowableRequests.add(ReadyThrowableRequest(
                            player = player,
                        ))
                    }
                }

                // melee weapon damage adjustment
                XC.ITEM_TYPE_MELEE -> {
                    getMeleeInHandUnchecked(player)?.let { weapon ->
                        val target = e.getEntity()
                        if ( target is LivingEntity && target is Damageable ) {
                            // melee damage handler
                            val damage = damageAfterArmorAndResistance(
                                weapon.damage,
                                target,
                                weapon.damageArmorReduction,
                                weapon.damageResistanceReduction,
                            )
                            
                            // println("NEW MELEE DAMAGE base=${weapon.damage} final=$damage")

                            if ( target is Player && target.getHealth() > 0.0 && damage >= target.getHealth() ) {
                                XC.deathEvents[target.getUniqueId()] = XcPlayerDeathEvent(
                                    player = target,
                                    killer = player,
                                    weaponType = XC.ITEM_TYPE_MELEE,
                                    weaponId = weapon.itemModelDefault,
                                    weaponMaterial = XC.config.materialMelee,
                                )
                            }

                            e.setDamage(damage)
                        }
                    }
                }
            }
        }
    }

    /**
     * Disable snowball throwing because snowballs used as main
     * gun ammo.
     */
    @EventHandler
    public fun onProjectileThrownEvent(e: ProjectileLaunchEvent) {
        if ( e.getEntity().getType() == EntityType.SNOWBALL ) {
            e.setCancelled(true)
        }
    }

    /**
     * Handler for when player changes armor.
     * Enforce server-side armor values (so we can re-balance armors
     * on server-side).
     */
    @EventHandler
    public fun onPlayerChangeArmor(e: PlayerArmorChangeEvent) {
        if ( XC.config.armorEnforce ) {
            val item = e.getNewItem()
            if ( item != null ) {
                val player = e.getPlayer()
                val armorValue = XC.config.armorValues[item.type]
                if ( armorValue != null ) {
                    when ( e.getSlotType() ) {
                        PlayerArmorChangeEvent.SlotType.CHEST -> {
                            val itemModified = setItemArmorNMS(item, armorValue, "chest", 1, 1)
                            player.getInventory().setChestplate(itemModified)
                        }
                        PlayerArmorChangeEvent.SlotType.FEET -> {
                            val itemModified = setItemArmorNMS(item, armorValue, "feet", 1, 2)
                            player.getInventory().setBoots(itemModified)
                        }
                        PlayerArmorChangeEvent.SlotType.HEAD -> {
                            val itemModified = setItemArmorNMS(item, armorValue, "head", 2, 1)
                            player.getInventory().setHelmet(itemModified)
                        }
                        PlayerArmorChangeEvent.SlotType.LEGS -> {
                            val itemModified = setItemArmorNMS(item, armorValue, "legs", 2, 2)
                            player.getInventory().setLeggings(itemModified)
                        }
                    }
                }
            }
        }
    }

    /**
     * Entity damage event.
     * For cancelling fall damage when jumping then activating crawl.
     * Minecraft bug? When jump then crawl, it pushes players to ground
     * causing massive fall damage. With NO JUMP potion effect, fall damage
     * is amplified to >100 so player instantly dies.
     * This event handler detects and cancels this fall damage event when
     * player is trying to crawl after jumping or falling.
     */
    @EventHandler
    public fun onDamage(e: EntityDamageEvent) {
        if ( e.getCause() == DamageCause.FALL ) {
            val player = e.getEntity()
            if ( player is Player ) {
                // println("FALL DAMAGE: isSwimming=${player.isSwimming()}, damage=${e.getDamage()}")

                // if player swimming and has NO JUMP effect, assume this is a
                // crawl fall damage event bug, and cancel damage
                if ( player.isSwimming() ) {
                    val noJumpEffect = player.getPotionEffect(PotionEffectType.JUMP)
                    if ( noJumpEffect != null && noJumpEffect.getAmplifier() == -128 ) {
                        // println("CANCEL FALL DAMAGE")
                        e.setCancelled(true)
                    }
                }
            }
        }
    }

    /**
     * Block activation for landmine event handling.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public fun onLandmineBlockActivation(event: BlockRedstoneEvent) {
        val block = event.block

        // pressure plate land mine, queue landmine activation handling
        XC.landmines[block.type]?.let { landmine ->
            if ( event.getNewCurrent() > XC.config.landmineMinRedstoneCurrent ) {
                XC.landmineActivationRequests.add(LandmineActivationRequest(
                    block = block,
                    landmine = landmine,
                ))
            }
        }
    }
}