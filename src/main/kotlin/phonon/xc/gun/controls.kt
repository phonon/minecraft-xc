/**
 * Contain all player gun shooting controls systems and
 * support classes.
 * 
 * TODO: reload cleanup edge cases?
 * - player move item thats reloading by clicking it or using shift key
 */

package phonon.xc.gun

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadLocalRandom
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.entity.Item as ItemEntity
import org.bukkit.entity.EntityType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.scheduler.BukkitRunnable
import phonon.xc.XC
import phonon.xc.gun.crawl.CrawlToShootRequest
import phonon.xc.gun.item.setGunItemMetaAmmo
import phonon.xc.gun.item.setGunItemMetaAmmoAndModel
import phonon.xc.gun.item.setGunItemMetaModel
import phonon.xc.gun.item.setGunItemMetaReloadModel
import phonon.xc.item.getGunFromItem
import phonon.xc.item.checkHandMaterialAndGetNbtIntKey
import phonon.xc.util.Message
import phonon.xc.util.sound.SoundPacket
import phonon.xc.util.recoil.RecoilPacket
import phonon.xc.util.progressBar10
import phonon.xc.util.entityMountEyeHeightOffset


/**
 * Async request for sending an ammo message packet to player.
 */
internal data class AmmoInfoMessagePacket(
    val player: Player,
    val ammo: Int,
    val maxAmmo: Int,
)

/**
 * Hold state for shooting delay
 */
internal data class ShootDelay(
    val timestampDelayAfterShoot: Long, // = time shoot + delay
    val timestampCanShoot: Long, // = time player can actually shoot again
)

/**
 * Holds player burst firing state for a gun.
 */
internal data class BurstFire(
    // id (to track same gun is being used)
    val id: Int,
    // player firing
    val player: Player,
    // gun
    val gun: Gun,
    // original ammo, used for auto-reload
    val originalAmmo: Int,
    // current ammo
    val ammo: Int,
    // re-used item stack
    val item: ItemStack,
    // re-used item meta
    val itemMeta: ItemMeta,
    // re-used persistent data container
    val itemData: PersistentDataContainer,
    // inventory slot, used for cleanup if needed
    val inventorySlot: Int,
    // total length of time player has been firing
    val totalTime: Double,
    // tick counter since last fired, used for timing firing rate in projectiles/tick
    val ticksCooldown: Int,
    // number of shots remaining in this burst fire packet
    val remainingCount: Int,
    // next index in gun delay pattern array (for guns that use delay patterns)
    val delayPatternIndex: Int,
)

/**
 * Holds player automatic firing state for a gun.
 */
internal data class AutoFire(
    // id (to track same gun is being used)
    val id: Int,
    // player firing
    val player: Player,
    // gun
    val gun: Gun,
    // current ammo
    val ammo: Int,
    // re-used item stack
    val item: ItemStack,
    // re-used item meta
    val itemMeta: ItemMeta,
    // re-used persistent data container
    val itemData: PersistentDataContainer,
    // inventory slot, used for cleanup if needed
    val inventorySlot: Int,
    // total length of time player has been firing
    val totalTime: Double,
    // tick counter since last fired, used for timing firing rate in projectiles/tick
    val ticksCooldown: Int,
    // tick counter since last auto fire request packet
    val ticksSinceLastRequest: Int,
    // next index in gun delay pattern array (for guns that use delay patterns)
    val delayPatternIndex: Int,
    // ticks to keep try firing before starting auto reload
    val ticksBeforeReload: Int,
)

/**
 * Data for controls request to select gun.
 */
@JvmInline
internal value class PlayerGunSelectRequest(
    val player: Player,
)

/**
 * Data for controls request to shoot gun.
 */
@JvmInline
internal value class PlayerGunShootRequest(
    val player: Player,
)

/**
 * Data for controls request to shoot gun.
 */
@JvmInline
internal value class PlayerAutoFireRequest(
    val player: Player,
)

/**
 * Data for controls request to reload gun.
 */
@JvmInline
internal value class PlayerGunReloadRequest(
    val player: Player,
)


/**
 * Data for request to aim down sights.
 */
@JvmInline
internal value class PlayerAimDownSightsRequest(
    val player: Player,
)

/**
 * Data for request to cleanup a player's gun.
 */
internal data class PlayerGunCleanupRequest(
    val player: Player,
    val inventorySlot: Int = -1, // if -1, get item in main hand. else, get item in slot
)

/**
 * Data for request to cleanup an item stack.
 */
internal data class ItemGunCleanupRequest(
    val itemEntity: ItemEntity,
    val onDrop: Boolean,
)


/**
 * Data for task that runs after player finishes reloading.
 */
internal data class PlayerReloadTask(
    val player: Player,
    val gun: Gun,
    val ammoCurrent: Int,
    val item: ItemStack,
    val reloadId: Int,
    val inventorySlot: Int,
)

/**
 * Data for task that runs when reload gets cancelled.
 * (e.g. if player swaps items, or logs off or dies)
 */
internal data class PlayerReloadCancelledTask(
    val player: Player,
    val gun: Gun,
    val reloadId: Int,
    val item: ItemStack,
    val inventorySlot: Int,
    val playerDied: Boolean,
)

/**
 * Helper function to determine if model should be aim down sights.
 */
internal fun XC.useAimDownSights(player: Player): Boolean {
    return (player.isSneaking() || player.isSwimming()) && !this.dontUseAimDownSights.contains(player.getUniqueId())
}

/**
 * Cleans up gun item metadata if it contains reload flags.
 * Note: THIS MUTATES THE ITEM STACK.
 * Returns:
 *  - True if item was reloading and if flags were removed.
 *  - False if item was not reloading
 */
private fun XC.cleanupGunMeta(
    itemMeta: ItemMeta,
    itemData: PersistentDataContainer,
    gun: Gun,
    aimdownsights: Boolean,
): ItemMeta {
    val isReloading = itemData.get(this.namespaceKeyItemReloading, PersistentDataType.INTEGER) ?: 0
    if ( isReloading == TRUE ) {
        // println("CLEANED UP RELOAD FLAGS!!!")
        
        // clean up reloading flags
        itemData.remove(this.namespaceKeyItemReloading)
        itemData.remove(this.namespaceKeyItemReloadId)
        itemData.remove(this.namespaceKeyItemReloadTimestamp)
    }

    // RESET MODEL TO PROPER STATE:

    // current ammo
    val ammoCurrent = itemData.get(this.namespaceKeyItemAmmo, PersistentDataType.INTEGER) ?: 0

    // if this has an auto fire id or burst fire id
    if ( itemData.has(this.namespaceKeyItemAutoFireId, PersistentDataType.INTEGER) ) {
        itemData.remove(this.namespaceKeyItemAutoFireId)
        itemMeta.setLore(gun.getItemDescriptionForAmmo(ammoCurrent))
    }
    if ( itemData.has(this.namespaceKeyItemBurstFireId, PersistentDataType.INTEGER) ) {
        itemData.remove(this.namespaceKeyItemBurstFireId)
        itemMeta.setLore(gun.getItemDescriptionForAmmo(ammoCurrent))
    }

    // SET GUN ATTACK SPEED SETTING
    // (to match gun shoot delay)
    itemMeta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED)
    itemMeta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, gun.attackSpeedAttributeModifier)

    return setGunItemMetaModel(itemMeta, gun, ammoCurrent, aimdownsights)
}


/**
 * Common function to run a single gun shot for single, burst, and auto
 * firing modes.
 */
private fun XC.doSingleShot(
    player: Player,
    loc: Location,
    gun: Gun,
    newAmmo: Int,
    projectileSystem: ProjectileSystem,
    random: ThreadLocalRandom,
) {
    // if newAmmo is 0, remove any aim down sights model and also play empty sound
    // case for -1: if using consumeOnUse guns, newAmmo set to -1 to mark gun
    if ( newAmmo <= 0 ) {
        this.removeAimDownSightsOffhandModel(player)

        this.soundQueue.add(SoundPacket(
            sound = gun.soundEmpty,
            world = loc.world,
            location = loc,
            volume = gun.soundEmptyVolume,
            pitch = gun.soundEmptyPitch,
        ))
    }
    // send ammo if gun is still relevent (ammo >= 0, -1 if gun item removed)
    if ( newAmmo >= 0 ) {
        this.gunAmmoInfoMessageQueue.add(AmmoInfoMessagePacket(player, newAmmo, gun.ammoMax))
    }

    val shootPosition = if ( player.isSwimming() ) {
        loc.clone().add(0.0, 0.4, 0.0)
    } else {
        loc.clone().add(0.0, player.eyeHeight, 0.0)
    }
    val shootDirection = loc.direction.clone()

    // adjust shoot position if player is on a vehicle
    player.getVehicle()?.let { v -> shootPosition.y += entityMountEyeHeightOffset(v.type) }

    val sway = calculateSway(player, gun, this.playerSpeed[player.getUniqueId()] ?: 0.0)
    // println("sway = $sway")

    for ( _i in 0 until gun.projectileCount ) {
        var shootDirX = shootDirection.x
        var shootDirY = shootDirection.y
        var shootDirZ = shootDirection.z
        if ( sway > 0.0 ) {
            shootDirX += random.nextDouble(-sway, sway)
            shootDirY += random.nextDouble(-sway, sway)
            shootDirZ += random.nextDouble(-sway, sway)
        }

        // creating projectile here manually since shoot direction
        // is modulated by random sway
        val projectile = Projectile(
            gun = gun,
            source = player,
            x = shootPosition.x.toFloat(),
            y = shootPosition.y.toFloat(),
            z = shootPosition.z.toFloat(),
            dirX = shootDirX.toFloat(),
            dirY = shootDirY.toFloat(),
            dirZ = shootDirZ.toFloat(),
            speed = gun.projectileVelocity,
            gravity = gun.projectileGravity,
            maxLifetime = gun.projectileLifetime,
            maxDistance = gun.projectileMaxDistance,
            proximity = gun.projectileProximity,
            passthroughDoors = gun.projectilePassthroughDoors,
        )

        projectileSystem.addProjectile(projectile)
    }

    // shoot sound
    this.soundQueue.add(SoundPacket(
        sound = gun.soundShoot,
        world = loc.world,
        location = loc,
        volume = gun.soundShootVolume,
        pitch = gun.soundShootPitch,
    ))
}

/**
 * System to aim down sights. This modifies player's gun item model if they
 * have aim down sights enabled.
 */
internal fun XC.gunAimDownSightsSystem(
    playerAimDownSightsRequests: List<PlayerAimDownSightsRequest>,
) {
    for ( request in playerAimDownSightsRequests ) {
        try {
            val player = request.player

            if ( this.dontUseAimDownSights.contains(player.getUniqueId()) ) {
                continue
            }

            // Do redundant player main hand is gun check here
            // since events could override the first shoot event, causing
            // inventory slot or item to change
            val equipment = player.getInventory()
            val inventorySlot = equipment.getHeldItemSlot()
            val item = equipment.getItem(inventorySlot)
            if ( item == null ) {
                continue
            }

            val gun = getGunFromItem(item)
            if ( gun == null ) {
                continue
            }
            
            if ( gun.itemModelAimDownSights > 0 ) {
                var itemMeta = item.getItemMeta()
                val itemData = itemMeta.getPersistentDataContainer()

                // skip if reloading
                val isReloading = itemData.get(this.namespaceKeyItemReloading, PersistentDataType.INTEGER) ?: 0
                if ( isReloading == TRUE ) {
                    continue
                }

                // only ads if ammo > 0
                val ammo = itemData.get(this.namespaceKeyItemAmmo, PersistentDataType.INTEGER) ?: 0
                if ( ammo > 0 ) {
                    val isShift = player.isSneaking()
        
                    if ( isShift ) {
                        itemMeta.setCustomModelData(gun.itemModelAimDownSights)
                        this.createAimDownSightsOffhandModel(gun, player)
                    } else {
                        itemMeta.setCustomModelData(gun.itemModelDefault)
                        this.removeAimDownSightsOffhandModel(player)
                    }
        
                    item.setItemMeta(itemMeta)
                    equipment.setItem(inventorySlot, item)
                }
            }
        }
        catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed to create aimdownsights model for player ${request.player.getName()}")
        }
    }

    // clear request queue for next update tick
    this.playerAimDownSightsRequests = ArrayList()
}

/**
 * System to cleanup gun items when player logs out, dies, etc.
 * - reload flags
 * - aim down sights models
 */
internal fun XC.playerGunCleanupSystem(
    playerGunCleanupRequests: List<PlayerGunCleanupRequest>,
) {
    for ( request in playerGunCleanupRequests ) {
        try {
            val (player, inventorySlotToCleanup) = request

            // Do redundant player main hand is gun check here
            // since events could override the first shoot event, causing
            // inventory slot or item to change
            val equipment = player.getInventory()
            val inventorySlot = if ( inventorySlotToCleanup == -1 ) {
                equipment.getHeldItemSlot()
            } else {
                inventorySlotToCleanup
            }

            val item = equipment.getItem(inventorySlot)
            if ( item == null ) {
                continue
            }

            val gun = getGunFromItem(item)
            if ( gun == null ) {
                continue
            }

            // clean up aim down sights model
            this.removeAimDownSightsOffhandModel(player)

            // clean up gun item metadata
            val itemMeta = item.getItemMeta()
            val itemData = itemMeta.getPersistentDataContainer()
            val newItemMeta = cleanupGunMeta(itemMeta, itemData, gun, false)
            item.setItemMeta(newItemMeta)
            equipment.setItem(inventorySlot, item)
        }
        catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed to cleanup gun item for player ${request.player.getName()}")
        }
    }

    // clear request queue for next update tick
    this.playerGunCleanupRequests = ArrayList()
}

/**
 * System to cleanup reload flags when item is dropped.
 */
internal fun XC.gunItemCleanupSystem(
    itemGunCleanupRequests: List<ItemGunCleanupRequest>,
) {
    for ( request in itemGunCleanupRequests ) {
        try {
            val (itemEntity, onDrop) = request
            val item = itemEntity.getItemStack()

            val gun = getGunFromItem(item)
            if ( gun == null ) {
                continue
            }

            // if item was thrown by a player, make sure to cleanup the
            // player's aim down sights model
            if ( onDrop ) {
                val throwerUUID = itemEntity.getThrower()
                if ( throwerUUID != null ) {
                    Bukkit.getPlayer(throwerUUID)?.let { player -> 
                        this.removeAimDownSightsOffhandModel(player)
                    }
                }
            }

            // clean up gun item reload metadata
            val itemMeta = item.getItemMeta()
            val itemData = itemMeta.getPersistentDataContainer()
            val newItemMeta = cleanupGunMeta(itemMeta, itemData, gun, false)
            item.setItemMeta(newItemMeta)
            itemEntity.setItemStack(item)
        }
        catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed to cleanup dropped gun item entity ${request.itemEntity}")
        }
    }

    // clear request queue for next update tick
    this.itemGunCleanupRequests = ArrayList()
}

/**
 * Player swap to gun system:
 * 1. Handle cleaning up reload if gun still has reloading flags.
 * 2. Add shooting delay if swapping from another gun.
 * 3. Send ammo info to player.
 */
internal fun XC.gunSelectSystem(
    playerGunSelectRequests: List<PlayerGunSelectRequest>,
    autoFiringPackets: Map<UUID, AutoFire>,
    playerShootDelay: HashMap<UUID, ShootDelay>,
    timestamp: Long
) {
    for ( request in playerGunSelectRequests ) {
        try {
            val player = request.player
            val playerId = player.getUniqueId()

            // Do redundant player main hand is gun check here
            // since events could override the first shoot event, causing
            // inventory slot or item to change
            val equipment = player.getInventory()
            val inventorySlot = equipment.getHeldItemSlot()
            val item = equipment.getItem(inventorySlot)
            if ( item == null ) {
                continue
            }

            val gun = getGunFromItem(item)
            if ( gun == null ) {
                continue
            }

            // gun swap delay:
            // -> goal is to block players fast swapping between guns
            //    (e.g. using macros) to bypass gun shooting delays
            //    or to swap between different types (e.g. rifle -> shotgun)
            // See `docs/delay.md` for details.
            if ( autoFiringPackets.contains(playerId) ) {
                // must have swapped here while still auto firing: add a new shoot delay
                val timestampShootDelay = timestamp + gun.equipDelayMillis

                playerShootDelay[playerId] = ShootDelay(
                    timestampDelayAfterShoot = timestampShootDelay,
                    timestampCanShoot = timestampShootDelay,
                )
            } else {
                // swapped from single or burst fire. add shoot delay
                playerShootDelay[playerId]?.let { shootDelay ->
                    playerShootDelay[playerId] = shootDelay.copy(
                        timestampCanShoot = shootDelay.timestampDelayAfterShoot + gun.equipDelayMillis,
                    )
                }
            }

            var itemMeta = item.getItemMeta()
            val itemData = itemMeta.getPersistentDataContainer()

            // check if playing is aim down sights
            val aimDownSights = useAimDownSights(player)

            // send ammo info to player
            val ammo = itemData.get(this.namespaceKeyItemAmmo, PersistentDataType.INTEGER) ?: 0
            this.gunAmmoInfoMessageQueue.add(AmmoInfoMessagePacket(player, ammo, gun.ammoMax))

            // clean up gun item metadata
            val newItemMeta = cleanupGunMeta(itemMeta, itemData, gun, aimDownSights)
            item.setItemMeta(newItemMeta)
            equipment.setItem(inventorySlot, item)

            // if player is aim down sights, add offhand model
            if ( ammo > 0 && aimDownSights && gun.itemModelAimDownSights > 0 ) {
                this.createAimDownSightsOffhandModel(gun, player)
            }
        }
        catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed to do gun item select handling for player ${request.player.getName()}")
        }
    }

    // clear request queue for next update tick
    this.playerGunSelectRequests = ArrayList()
}


/**
 * Player single/burst fire shooting system. (No auto fire here)
 * Handles [LEFT MOUSE] click firing.
 */
internal fun XC.gunPlayerShootSystem(
    playerShootRequests: List<PlayerGunShootRequest>,
    autoFiringPackets: Map<UUID, AutoFire>,
    burstFiringPackets: HashMap<UUID, BurstFire>,
    playerShootDelay: HashMap<UUID, ShootDelay>,
    playerReloadRequests: ArrayList<PlayerGunReloadRequest>,
    crawlingAndReadyToShoot: Map<UUID, Boolean>,
    crawlToShootRequestQueue: ArrayList<CrawlToShootRequest>,
    projectileSystems: Map<UUID, ProjectileSystem>,
    timestamp: Long,
) {
    val random = ThreadLocalRandom.current()
    val playerHandled = HashSet<UUID>() // players ids already handled to avoid redundant requests

    for ( request in playerShootRequests ) {
        try {
            val player = request.player
            val playerId = player.getUniqueId()
            
            if ( playerHandled.add(playerId) == false ) {
                // false if already contained in set
                continue
            }

            // Do redundant player main hand is gun check here
            // since events could override the first shoot event, causing
            // inventory slot or item to change
            val equipment = player.getInventory()
            val inventorySlot = equipment.getHeldItemSlot()
            val item = equipment.getItem(inventorySlot)
            if ( item == null ) {
                continue
            }

            val gun = getGunFromItem(item)
            if ( gun == null ) {
                continue
            }

            // skip single shoot while auto firing
            if ( autoFiringPackets.contains(playerId) ) {
                continue
            }

            val loc = player.location
            val world = loc.world
            val projectileSystem = projectileSystems[world.getUID()]
            if ( projectileSystem == null ) {
                continue
            }

            var itemMeta = item.getItemMeta()
            val itemData = itemMeta.getPersistentDataContainer()
            
            // check gun is reloading
            // if reloading is past time + some margin, cancel reload and clear flags
            val reloadTimeMillis = gun.reloadTimeMillis
            val isReloading = itemData.get(this.namespaceKeyItemReloading, PersistentDataType.INTEGER) ?: 0
            if ( isReloading == TRUE ) {
                // Check reload timestamp, if current time > timestamp0 + reloadTime + 1000 ms,
                // assume this gun's reload was broken somehow and continue with shoot.
                // Else, this is still in a reload process: skip shooting
                val reloadTimestamp = itemData.get(this.namespaceKeyItemReloadTimestamp, PersistentDataType.LONG)
                if ( reloadTimestamp != null && timestamp < reloadTimestamp + reloadTimeMillis + 1000L ) {
                    continue
                }

                // clean up reloading flags
                itemData.remove(this.namespaceKeyItemReloading)
                itemData.remove(this.namespaceKeyItemReloadId)
                itemData.remove(this.namespaceKeyItemReloadTimestamp)
            }

            // if crawling required, request crawl to shoot
            if ( gun.crawlRequired && crawlingAndReadyToShoot[playerId] != true ) {
                crawlToShootRequestQueue.add(CrawlToShootRequest(player))
                continue
            }

            // check if still under shoot delay
            val shootDelay = playerShootDelay[playerId]
            if ( shootDelay != null && timestamp < shootDelay.timestampCanShoot ) {
                continue
            }

            val fireMode = gun.singleFireMode

            if ( fireMode == GunSingleFireMode.SINGLE ) {
                
                val newAmmo = if ( gun.shootConsumeOnUse ) { // removes gun item when used
                    equipment.setItem(inventorySlot, ItemStack(Material.AIR, 1))
                    -1
                } else {
                    // check ammo and send ammo info message to player
                    val ammo = itemData.get(this.namespaceKeyItemAmmo, PersistentDataType.INTEGER) ?: 0
                    if ( ammo <= 0 ) {
                        this.gunAmmoInfoMessageQueue.add(AmmoInfoMessagePacket(player, ammo, gun.ammoMax))
                        
                        // play gun empty sound effect
                        this.soundQueue.add(SoundPacket(
                            sound = gun.soundEmpty,
                            world = world,
                            location = loc,
                            volume = gun.soundEmptyVolume,
                            pitch = gun.soundEmptyPitch,
                        ))
        
                        if ( !gun.ammoIgnore ) {
                            if ( this.config.autoReloadGuns ) {
                                playerReloadRequests.add(PlayerGunReloadRequest(player))
                            }
                            continue
                        }
                    }
        
                    val newAmmo = max(0, ammo - 1)
        
                    // update player item: set new ammo and set model
                    itemMeta = setGunItemMetaAmmo(itemMeta, gun, newAmmo)
                    itemMeta = setGunItemMetaModel(itemMeta, gun, newAmmo, useAimDownSights(player))
                    item.setItemMeta(itemMeta)
                    equipment.setItem(inventorySlot, item)

                    newAmmo
                }

                // fires projectiles
                doSingleShot(
                    player,
                    loc,
                    gun,
                    newAmmo,
                    projectileSystem,
                    random,
                )

                // recoil handling:
                doRecoil(player, gun.recoilSingleHorizontal, gun.recoilSingleVertical, gun.recoilSingleFireRamp)

                // shoot delay
                val timestampShootDelay = timestamp + gun.shootDelayMillis
                playerShootDelay[playerId] = ShootDelay(
                    timestampDelayAfterShoot = timestampShootDelay,
                    timestampCanShoot = timestampShootDelay,
                )
                
            } else if ( fireMode == GunSingleFireMode.BURST ) {
                // add burst packet if not already burst firing
                if ( !burstFiringPackets.contains(playerId) ) {
                    val ammo = itemData.get(this.namespaceKeyItemAmmo, PersistentDataType.INTEGER) ?: 0
                    
                    // // auto-reload instead of firing when empty
                    // // MOVED to inside burstFireSystem so that
                    // // players can hear the burst clicks before reload
                    // if ( ammo <= 0 ) {
                    //     if ( !gun.ammoIgnore ) {
                    //         if ( this.config.autoReloadGuns ) {
                    //             this.playerReloadRequests.add(PlayerGunReloadRequest(player))
                    //         }
                    //         continue
                    //     }
                    // }
                    
                    val burstFireId = this.newBurstFireId()

                    burstFiringPackets[playerId] = BurstFire(
                        id = burstFireId,
                        player = player,
                        gun = gun,
                        originalAmmo = ammo,
                        ammo = ammo,
                        item = item,
                        itemMeta = itemMeta,
                        itemData = itemData,
                        inventorySlot = inventorySlot,
                        totalTime = 0.0,
                        ticksCooldown = 0,
                        remainingCount = gun.burstFireCount,
                        delayPatternIndex = 0,
                    )

                    // set item in hand's burst fire id
                    itemData.set(this.namespaceKeyItemBurstFireId, PersistentDataType.INTEGER, burstFireId)
                    item.setItemMeta(itemMeta)
                    equipment.setItem(inventorySlot, item)
                }
            } else { // fireMode == GunSingleFireMode.NONE
                // no-op
                continue
            }
        }
        catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed to process gun shoot request by player ${request.player.getName()}")
        }
    }

    // clear request queue for next tick
    this.playerShootRequests = ArrayList()
}


/**
 * Player burst fire shooting system. Return a new hashmap
 * of burst fire packets for next tick.
 */
internal fun XC.burstFireSystem(
    burstFiringPackets: HashMap<UUID, BurstFire>,
    playerShootDelay: HashMap<UUID, ShootDelay>,
    playerReloadRequests: ArrayList<PlayerGunReloadRequest>,
    projectileSystems: Map<UUID, ProjectileSystem>,
    timestamp: Long,
) {
    val nextTickRequests = HashMap<UUID, BurstFire>()
    val random = ThreadLocalRandom.current()

    for ( (playerId, request) in burstFiringPackets ) {
        try {
            val (
                id,
                player,
                gun,
                originalAmmo,
                ammo,
                item,
                itemMeta,
                itemData,
                inventorySlot,
                totalTime,
                ticksCooldown,
                remainingCount,
                delayPatternIndex,
            ) = request

            val equipment = player.getInventory()
            val currInventorySlot = equipment.getHeldItemSlot()

            // check if player main hand item auto fire id matches
            if ( player.isDead() ||
                !player.isOnline() ||
                currInventorySlot != inventorySlot ||
                id != checkHandMaterialAndGetNbtIntKey(player, this.config.materialGun, this.nbtKeyItemBurstFireId)
            ) {
                // no cleanup here: event handlers should pick up this event and clean gun if possible
                continue
            }

            // decrement burst delay
            if ( ticksCooldown > 0 ) {
                nextTickRequests[playerId] = BurstFire(
                    id = id,
                    player = player,
                    gun = gun,
                    originalAmmo = originalAmmo,
                    ammo = ammo,
                    item = item,
                    itemMeta = itemMeta,
                    itemData = itemData,
                    inventorySlot = inventorySlot,
                    totalTime = totalTime + 1,
                    ticksCooldown = ticksCooldown - 1,
                    remainingCount = remainingCount,
                    delayPatternIndex = delayPatternIndex,
                )
                continue
            }

            val loc = player.location
            val world = loc.world
            val projectileSystem = projectileSystems[world.getUID()]
            if ( projectileSystem == null ) continue

            // check ammo and send ammo info message to player
            if ( ammo <= 0 ) {
                this.gunAmmoInfoMessageQueue.add(AmmoInfoMessagePacket(player, ammo, gun.ammoMax))
                
                // play gun empty sound effect
                this.soundQueue.add(SoundPacket(
                    sound = gun.soundEmpty,
                    world = world,
                    location = loc,
                    volume = gun.soundEmptyVolume,
                    pitch = gun.soundEmptyPitch,
                ))

                if ( !gun.ammoIgnore ) {
                    // finish sequence before stopping
                    if ( remainingCount > 0 ) {
                        // next firing cooldown and delay pattern index
                        var nextDelayPatternIndex = delayPatternIndex
                        val cooldown = if ( gun.useBurstFireDelayTickPattern ) {
                            nextDelayPatternIndex = (delayPatternIndex + 1).mod(gun.burstFireDelayTickPattern.size) // required here to avoid modulo 0
                            gun.burstFireDelayTickPattern[delayPatternIndex]
                        } else {
                            gun.burstFireDelayTicks
                        }

                        nextTickRequests[playerId] = BurstFire(
                            id = id,
                            player = player,
                            gun = gun,
                            originalAmmo = originalAmmo,
                            ammo = 0,
                            item = item,
                            itemMeta = itemMeta,
                            itemData = itemData,
                            inventorySlot = inventorySlot,
                            totalTime = totalTime + 1,
                            ticksCooldown = cooldown,
                            remainingCount = remainingCount - 1,
                            delayPatternIndex = nextDelayPatternIndex,
                        )
                    } else {
                        // clean up item
                        itemData.remove(this.namespaceKeyItemBurstFireId)
                        val newItemMeta = setGunItemMetaAmmoAndModel(itemMeta, itemData, gun, ammo, useAimDownSights(player))        
                        item.setItemMeta(newItemMeta)
                        equipment.setItem(currInventorySlot, item)

                        // queue auto-reload (only if ammo = 0 when fired)
                        if ( this.config.autoReloadGuns && originalAmmo <= 0 ) {
                            playerReloadRequests.add(PlayerGunReloadRequest(player))
                        }
                    }

                    continue
                }
            }

            val newAmmo = max(0, ammo - 1)

            // fires projectiles
            doSingleShot(
                player,
                loc,
                gun,
                newAmmo,
                projectileSystem,
                random,
            )

            // recoil packet
            doRecoil(player, gun.recoilSingleHorizontal, gun.recoilSingleVertical, gun.recoilSingleFireRamp)

            // continue sequence if burst has remaining shots
            if ( remainingCount > 1 ) {
                // just update ammo data
                itemData.set(this.namespaceKeyItemAmmo, PersistentDataType.INTEGER, newAmmo)
                item.setItemMeta(itemMeta)
                equipment.setItem(inventorySlot, item)

                // next firing cooldown and delay pattern index
                var nextDelayPatternIndex = delayPatternIndex
                val cooldown = if ( gun.useBurstFireDelayTickPattern ) {
                    nextDelayPatternIndex = (delayPatternIndex + 1).mod(gun.burstFireDelayTickPattern.size) // required here to avoid modulo 0
                    gun.burstFireDelayTickPattern[delayPatternIndex]
                } else {
                    gun.burstFireDelayTicks
                }

                nextTickRequests[playerId] = BurstFire(
                    id = id,
                    player = player,
                    gun = gun,
                    originalAmmo = originalAmmo,
                    ammo = newAmmo,
                    item = item,
                    itemMeta = itemMeta,
                    itemData = itemData,
                    inventorySlot = inventorySlot,
                    totalTime = totalTime + 1,
                    ticksCooldown = cooldown,
                    remainingCount = remainingCount - 1,
                    delayPatternIndex = nextDelayPatternIndex,
                )
            } else {
                // burst done.

                // clean up item
                itemData.remove(this.namespaceKeyItemBurstFireId)
                val newItemMeta = setGunItemMetaAmmoAndModel(itemMeta, itemData, gun, newAmmo, useAimDownSights(player))        
                item.setItemMeta(newItemMeta)
                equipment.setItem(currInventorySlot, item)

                // add shooting delay
                val timestampShootDelay = timestamp + gun.shootDelayMillis
                playerShootDelay[playerId] = ShootDelay(
                    timestampDelayAfterShoot = timestampShootDelay,
                    timestampCanShoot = timestampShootDelay,
                )
            }
        }
        catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed to process burst firing packet for player ${request.player.name}")
        }
    }

    // update active burst firing for next tick
    this.burstFiringPackets = nextTickRequests
}

/**
 * Automatic fire request system. Initiate or refresh an auto fire sequence.
 * Handles [RIGHT MOUSE] hold firing system.
 */
internal fun XC.autoFireRequestSystem(
    playerAutoFireRequests: List<PlayerAutoFireRequest>,
    autoFiring: HashMap<UUID, AutoFire>,
    playerShootDelay: Map<UUID, ShootDelay>,
    crawlingAndReadyToShoot: Map<UUID, Boolean>,
    crawlToShootRequestQueue: ArrayList<CrawlToShootRequest>,
    timestamp: Long,
) {
    val playerHandled = HashSet<UUID>() // players ids already handled to avoid redundant requests

    for ( request in playerAutoFireRequests ) {
        try {
            val player = request.player
            val playerId = player.getUniqueId()

            if ( playerHandled.add(playerId) == false ) {
                // false if already contained in set
                continue
            }

            // check if still under shoot delay
            val shootDelay = playerShootDelay[playerId]
            if ( shootDelay != null && timestamp < shootDelay.timestampCanShoot ) {
                continue
            }

            val currentAutoFire = autoFiring[playerId]
            if ( currentAutoFire != null ) { // refresh current autofire request
                autoFiring[playerId] = currentAutoFire.copy(
                    ticksSinceLastRequest = 0,
                )
            } else { // new auto fire request

                // Do redundant player main hand is gun check here
                // since events could override the first shoot event, causing
                // inventory slot or item to change
                val equipment = player.getInventory()
                val inventorySlot = equipment.getHeldItemSlot()
                val item = equipment.getItem(inventorySlot)
                if ( item == null ) {
                    continue
                }

                val gun = getGunFromItem(item)
                if ( gun == null ) {
                    continue
                }

                var itemMeta = item.getItemMeta()
                val itemData = itemMeta.getPersistentDataContainer()

                // check gun is reloading
                // if reloading is past time + some margin, cancel reload and clear flags
                val reloadTimeMillis = gun.reloadTimeMillis
                val isReloading = itemData.get(this.namespaceKeyItemReloading, PersistentDataType.INTEGER) ?: 0
                if ( isReloading == TRUE ) {
                    // Check reload timestamp, if current time > timestamp0 + reloadTime + 1000 ms,
                    // assume this gun's reload was broken somehow and continue with shoot.
                    // Else, this is still in a reload process: skip shooting
                    val reloadTimestamp = itemData.get(this.namespaceKeyItemReloadTimestamp, PersistentDataType.LONG)
                    if ( reloadTimestamp != null && timestamp < reloadTimestamp + reloadTimeMillis + 1000L ) {
                        continue
                    }

                    // clean up reloading flags
                    itemData.remove(this.namespaceKeyItemReloading)
                    itemData.remove(this.namespaceKeyItemReloadId)
                    itemData.remove(this.namespaceKeyItemReloadTimestamp)
                }

                // if crawling required, request crawl to shoot
                if ( gun.crawlRequired && crawlingAndReadyToShoot[playerId] != true ) {
                    crawlToShootRequestQueue.add(CrawlToShootRequest(player))
                    continue
                }

                val ammo = itemData.get(this.namespaceKeyItemAmmo, PersistentDataType.INTEGER) ?: 0

                val autoFireId = this.newAutoFireId()

                autoFiring[playerId] = AutoFire(
                    id = autoFireId,
                    player = player,
                    gun = gun,
                    ammo = ammo,
                    item = item,
                    itemMeta = itemMeta,
                    itemData = itemData,
                    inventorySlot = inventorySlot,
                    totalTime = 0.0,
                    ticksCooldown = 0,
                    ticksSinceLastRequest = 0,
                    delayPatternIndex = 0,
                    ticksBeforeReload = this.config.autoFireTicksBeforeReload,
                )
                
                // set item in hand's auto fire id
                itemData.set(this.namespaceKeyItemAutoFireId, PersistentDataType.INTEGER, autoFireId)
                item.setItemMeta(itemMeta)
                equipment.setItem(inventorySlot, item)
            }
        }
        catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed to handle auto fire request for player ${request.player.name}")
        }
    }

    // clear request queue for next update tick
    this.playerAutoFireRequests = ArrayList()
    // update auto firing players
    this.autoFiringPackets = autoFiring
}

/**
 * Player automatic fire shooting system. Return a new hashmap
 * of auto fire packets for next tick.
 * 
 * From benchmarking with spark, biggest time is spent setting
 * item lore. Since we except this to be running every tick for
 * many players, we will defer setting item lore until
 * player stops auto firing or is cancelled.
 */
internal fun XC.autoFireSystem(
    autoFiringPackets: Map<UUID, AutoFire>,
    playerReloadRequests: ArrayList<PlayerGunReloadRequest>,
    projectileSystems: Map<UUID, ProjectileSystem>,
) {
    val nextTickRequests = HashMap<UUID, AutoFire>()
    val random = ThreadLocalRandom.current()
    
    for ( (playerId, request) in autoFiringPackets ) {
        try {
            val (
                id,
                player,
                gun,
                ammo,
                item,
                itemMeta,
                itemData,
                inventorySlot,
                totalTime,
                ticksCooldown,
                ticksSinceLastRequest,
                delayPatternIndex,
                ticksBeforeReload,
            ) = request

            val equipment = player.getInventory()
            val currInventorySlot = equipment.getHeldItemSlot()

            // check if player main hand item auto fire id matches
            if ( player.isDead() ||
                !player.isOnline() ||
                currInventorySlot != inventorySlot ||
                id != checkHandMaterialAndGetNbtIntKey(player, this.config.materialGun, this.nbtKeyItemAutoFireId)
            ) {
                // no cleanup here: event handlers should pick up this event and clean gun if possible
                continue
            }
            
            if ( ticksSinceLastRequest >= this.config.autoFireMaxTicksSinceLastRequest ) {
                // clean up item
                itemData.remove(this.namespaceKeyItemAutoFireId)
                val newItemMeta = setGunItemMetaAmmoAndModel(itemMeta, itemData, gun, ammo, useAimDownSights(player))        
                item.setItemMeta(newItemMeta)
                equipment.setItem(currInventorySlot, item)
                continue
            }

            // decrement auto fire delay
            if ( ticksCooldown > 0 ) {
                nextTickRequests[playerId] = AutoFire(
                    id = id,
                    player = player,
                    gun = gun,
                    ammo = ammo,
                    item = item,
                    itemMeta = itemMeta,
                    itemData = itemData,
                    inventorySlot = inventorySlot,
                    totalTime = totalTime + 1,
                    ticksCooldown = ticksCooldown - 1,
                    ticksSinceLastRequest = ticksSinceLastRequest + 1,
                    delayPatternIndex = delayPatternIndex,
                    ticksBeforeReload = ticksBeforeReload,
                )
                continue
            }

            val loc = player.location
            val world = loc.world
            val projectileSystem = projectileSystems[world.getUID()]
            if ( projectileSystem == null ) continue

            // check ammo and send ammo info message to player
            if ( ammo <= 0 ) {
                this.gunAmmoInfoMessageQueue.add(AmmoInfoMessagePacket(player, ammo, gun.ammoMax))
                
                // play gun empty sound effect
                this.soundQueue.add(SoundPacket(
                    sound = gun.soundEmpty,
                    world = world,
                    location = loc,
                    volume = gun.soundEmptyVolume,
                    pitch = gun.soundEmptyPitch,
                ))

                if ( !gun.ammoIgnore ) {
                    if ( ticksBeforeReload > 0 ) { // decrement ticks first
                        // next firing cooldown
                        var nextDelayPatternIndex = delayPatternIndex
                        val cooldown = if ( gun.useAutoFireDelayTickPattern ) {
                            nextDelayPatternIndex = (delayPatternIndex + 1).mod(gun.autoFireDelayTickPattern.size) // required here to avoid modulo 0
                            gun.autoFireDelayTickPattern[delayPatternIndex]
                        } else {
                            gun.autoFireDelayTicks
                        }

                        nextTickRequests[playerId] = AutoFire(
                            id = id,
                            player = player,
                            gun = gun,
                            ammo = 0,
                            item = item,
                            itemMeta = itemMeta,
                            itemData = itemData,
                            inventorySlot = inventorySlot,
                            totalTime = totalTime + 1,
                            ticksCooldown = cooldown,
                            ticksSinceLastRequest = ticksSinceLastRequest + 1,
                            delayPatternIndex = nextDelayPatternIndex,
                            ticksBeforeReload = ticksBeforeReload - 1,
                        )
                    } else {
                        // clean up item
                        itemData.remove(this.namespaceKeyItemAutoFireId)
                        val newItemMeta = setGunItemMetaAmmoAndModel(itemMeta, itemData, gun, ammo, useAimDownSights(player))        
                        item.setItemMeta(newItemMeta)
                        equipment.setItem(currInventorySlot, item)
                        
                        // queue auto-reload
                        if ( this.config.autoReloadGuns ) {
                            playerReloadRequests.add(PlayerGunReloadRequest(player))
                        }
                    }

                    continue
                }
            }

            val newAmmo = max(0, ammo - 1)

            // fires projectiles
            doSingleShot(
                player,
                loc,
                gun,
                newAmmo,
                projectileSystem,
                random,
            )

            // recoil packet
            doRecoil(player, gun.recoilAutoHorizontal, gun.recoilAutoVertical, gun.recoilAutoFireRamp)

            // continue sequence if have ammo and burst has remaining shots
            if ( gun.ammoIgnore || newAmmo > 0 ) {
                // update ammo data
                itemData.set(this.namespaceKeyItemAmmo, PersistentDataType.INTEGER, newAmmo)
                item.setItemMeta(itemMeta)
                equipment.setItem(inventorySlot, item)

                // next firing cooldown
                var nextDelayPatternIndex = delayPatternIndex
                val cooldown = if ( gun.useAutoFireDelayTickPattern ) {
                    nextDelayPatternIndex = (delayPatternIndex + 1).mod(gun.autoFireDelayTickPattern.size) // required here to avoid modulo 0
                    gun.autoFireDelayTickPattern[delayPatternIndex]
                } else {
                    gun.autoFireDelayTicks
                }

                nextTickRequests[playerId] = AutoFire(
                    id = id,
                    player = player,
                    gun = gun,
                    ammo = newAmmo,
                    item = item,
                    itemMeta = itemMeta,
                    itemData = itemData,
                    inventorySlot = inventorySlot,
                    totalTime = totalTime + 1,
                    ticksCooldown = cooldown,
                    ticksSinceLastRequest = ticksSinceLastRequest + 1,
                    delayPatternIndex = nextDelayPatternIndex,
                    ticksBeforeReload = ticksBeforeReload,
                )
            } else {
                // clean up item
                itemData.remove(this.namespaceKeyItemAutoFireId)
                val newItemMeta = setGunItemMetaAmmoAndModel(itemMeta, itemData, gun, newAmmo, useAimDownSights(player))        
                item.setItemMeta(newItemMeta)
                equipment.setItem(currInventorySlot, item)
            }
        }
        catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed to update auto firing for player ${request.player.name}")
        }
    }

    // update active auto firing for next tick 
    this.autoFiringPackets = nextTickRequests
}


/**
 * Player recoil recovery. Return a new hashmap with new player
 * recoil multipliers.
 */
internal fun XC.recoilRecoverySystem(
    playerRecoil: Map<UUID, Double>,
    autoFiringPackets: Map<UUID, AutoFire>,
    burstFiringPackets: Map<UUID, BurstFire>,
) {
    val newPlayerRecoil: HashMap<UUID, Double> = HashMap()

    for ( (playerId, recoil) in playerRecoil ) {
        // recoil recovery is player is not burst or auto firing
        if ( !burstFiringPackets.contains(playerId) && !autoFiringPackets.contains(playerId) ) {
            val newRecoil = recoil - this.config.recoilRecoveryRate
            if ( newRecoil > 0.0 ) {
                newPlayerRecoil[playerId] = newRecoil
            }
        } else { // keep current recoil
            newPlayerRecoil[playerId] = recoil
        }
    }

    // update player recoil for next tick
    this.playerRecoil = newPlayerRecoil
}

/**
 * Player reload request system
 */
internal fun XC.gunPlayerReloadSystem(
    playerReloadRequests: List<PlayerGunReloadRequest>,
    autoFiringPackets: Map<UUID, AutoFire>,
    burstFiringPackets: Map<UUID, BurstFire>,
    playerReloadTaskQueue: LinkedBlockingQueue<PlayerReloadTask>,
    playerReloadCancelledTaskQueue: LinkedBlockingQueue<PlayerReloadCancelledTask>,
    timestamp: Long,
) {
    for ( request in playerReloadRequests ) {
        try {
            val player = request.player
            val playerId = player.getUniqueId()

            // Do redundant player main hand is gun check here
            // since events could override the first shoot event, causing
            // inventory slot or item to change
            val equipment = player.getInventory()
            val inventorySlot = equipment.getHeldItemSlot()
            val item = equipment.getItem(inventorySlot)
            if ( item == null ) {
                continue
            }

            val gun = getGunFromItem(item)
            if ( gun == null ) {
                continue
            }

            // skip if player is burst firing or auto firing
            if ( burstFiringPackets[playerId] != null || autoFiringPackets[playerId] != null ) {
                continue
            }

            var itemMeta = item.getItemMeta()
            val itemData = itemMeta.getPersistentDataContainer()
            
            // do reload if gun not reloading already and ammo less than max
            val reloadTimeMillis = gun.reloadTimeMillis
            val isReloading = itemData.get(this.namespaceKeyItemReloading, PersistentDataType.INTEGER) ?: 0
            
            if ( isReloading == TRUE ) {
                // Check reload timestamp, if current time > timestamp0 + reloadTime + 1000 ms,
                // assume this gun's reload was broken somehow and continue starting
                // a new reload task. Otherwise, we are still reloading, so exit.
                val reloadTimestamp = itemData.get(this.namespaceKeyItemReloadTimestamp, PersistentDataType.LONG)
                if ( reloadTimestamp != null && timestamp < reloadTimestamp + reloadTimeMillis + 1000L ) {
                    continue
                }
            }

            val ammoCurrent = itemData.get(this.namespaceKeyItemAmmo, PersistentDataType.INTEGER) ?: 0
            val ammoMax = gun.ammoMax
            if ( ammoCurrent >= ammoMax ) {
                continue
            }

            // check that player has enough ammo in inventory
            val ammoId = gun.ammoId
            if ( !inventoryContainsItem(player.getInventory(), this.config.materialAmmo, ammoId, 1) ) {
                Message.announcement(player, "${ChatColor.DARK_RED}[No ammo in inventory]")
                continue
            }

            // set reload flag and reloading id: this ensures this is same item
            // being reloaded when reload task finishes
            val reloadId = this.newReloadId()
            itemData.set(this.namespaceKeyItemReloading, PersistentDataType.INTEGER, TRUE)
            itemData.set(this.namespaceKeyItemReloadId, PersistentDataType.INTEGER, reloadId)

            // set timestamp when reload started.
            itemData.set(this.namespaceKeyItemReloadTimestamp, PersistentDataType.LONG, timestamp)
            
            // set reloading model
            itemMeta = setGunItemMetaReloadModel(itemMeta, gun)

            // update item meta with new data
            item.setItemMeta(itemMeta)
            equipment.setItem(inventorySlot, item)

            // remove any aim down sights model
            this.removeAimDownSightsOffhandModel(player)

            // play reload start sound
            val location = player.location
            val world = location.world
            if ( world != null ) {
                this.soundQueue.add(SoundPacket(
                    sound = gun.soundReloadStart,
                    world = world,
                    location = location,
                    volume = gun.soundReloadStartVolume,
                    pitch = gun.soundReloadStartPitch,
                ))
            }

            // launch reload task
            val reloadTask = GunReloadingTask(
                player = player,
                item = item,
                gun = gun,
                ammoCurrent = ammoCurrent,
                reloadId = reloadId,
                inventorySlot = inventorySlot,
                reloadTimeMillis = reloadTimeMillis.toDouble(),
                startTimeMillis = timestamp,
                itemGunMaterial = this.config.materialGun,
                itemDataKeyReloadId = this.namespaceKeyItemReloadId,
                reloadFinishTaskQueue = playerReloadTaskQueue,
                reloadCancelledTaskQueue = playerReloadCancelledTaskQueue,
            )
            // runs every 2 ticks = 100 ms
            reloadTask.runTaskTimerAsynchronously(this.plugin, 0L, 1L)
        }
        catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed to start gun reloading task for player ${request.player.name}")
        }
    }

    // clear request queue for next update tick
    this.playerReloadRequests = ArrayList()
}


/**
 * Async running task that handles gun reloading progress.
 * Does repeated checks that player is holding the same gun item,
 * and sends player reload progress bar, handle reloading sounds (TODO).
 */
internal class GunReloadingTask(
    private val player: Player,
    private val item: ItemStack,
    private val gun: Gun,
    private val ammoCurrent: Int,
    private val reloadId: Int,
    private val inventorySlot: Int,
    private val reloadTimeMillis: Double, // how long gun takes to reload in millis
    private val startTimeMillis: Long,    // timestamp when reloading started in millis
    private val itemGunMaterial: Material,
    private val itemDataKeyReloadId: NamespacedKey,
    private val reloadFinishTaskQueue: BlockingQueue<PlayerReloadTask>,
    private val reloadCancelledTaskQueue: BlockingQueue<PlayerReloadCancelledTask>,
): BukkitRunnable() {

    private fun cancelReload(playerDied: Boolean) {
        reloadCancelledTaskQueue.add(PlayerReloadCancelledTask(player, gun, reloadId, item, inventorySlot, playerDied))
        this.cancel()
    }

    override fun run() {
        // check if player log off or died
        if ( !player.isOnline() || player.isDead() ) {
            this.cancelReload(true)
            return
        }
        
        // check if item swapped
        val itemInHand = player.getInventory().getItemInMainHand()
        if ( itemInHand.getType() != itemGunMaterial ) {
            this.cancelReload(false)
            return
        }
        val itemCurrData = itemInHand.getItemMeta().getPersistentDataContainer()
        val itemReloadId = itemCurrData.get(itemDataKeyReloadId, PersistentDataType.INTEGER) ?: -1
        if ( itemReloadId != reloadId ) {
            this.cancelReload(false)
            return
        }

        val timeElapsedMillis = (System.currentTimeMillis() - startTimeMillis).toDouble()
        if ( timeElapsedMillis > reloadTimeMillis ) {
            // reload done: add reload finish task to queue
            reloadFinishTaskQueue.add(PlayerReloadTask(player, gun, ammoCurrent, item, reloadId, inventorySlot))
            this.cancel()
        } else {
            val progress = timeElapsedMillis / reloadTimeMillis
            val strRemainingTime = formatRemainingTimeString(reloadTimeMillis - timeElapsedMillis)
            Message.announcement(player, "Reloading ${progressBar10(progress)} ${strRemainingTime}")
        }
    }
}

/**
 * Finish reload task after async reload wait time finishes.
 */
internal fun XC.doGunReload(tasks: List<PlayerReloadTask>) {
    for ( task in tasks ) {
        try {
            val (player, gun, ammoCurrent, item, reloadId, inventorySlot) = task

            // remove ammo item from player inventory
            if ( !inventoryRemoveItem(player.getInventory(), this.config.materialAmmo, gun.ammoId, 1) ) {
                Message.announcement(player, "${ChatColor.DARK_RED}[No ammo in inventory]")
                continue
            }
            
            // check player main hand same and item same
            val inventory = player.getInventory()
            val currentMainHandSlot = inventory.getHeldItemSlot()
            val itemInHandReloadId = checkHandMaterialAndGetNbtIntKey(player, this.config.materialGun, this.nbtKeyItemReloadId) 
            if ( currentMainHandSlot != inventorySlot || itemInHandReloadId != reloadId ) {
                // this should never actually happen...
                Message.announcement(player, "${ChatColor.DARK_RED}[Item changed, reload cancelled]")
                continue
            }

            // new ammo amount
            // TODO: adjustable reloading, either load to max or add # of projectiles
            val newAmmo = if ( gun.ammoPerReload > 0 ) {
                min(gun.ammoMax, ammoCurrent + gun.ammoPerReload)
            } else {
                gun.ammoMax
            }

            // use ads flag
            val aimDownSights = useAimDownSights(player)

            // clear item reload data and set ammo
            var itemMeta = item.getItemMeta()
            val itemData = itemMeta.getPersistentDataContainer()
            val newItemMeta = setGunItemMetaAmmoAndModel(itemMeta, itemData, gun, newAmmo, aimDownSights)        
            itemData.remove(this.namespaceKeyItemReloading)
            itemData.remove(this.namespaceKeyItemReloadId)
            itemData.remove(this.namespaceKeyItemReloadTimestamp)
            item.setItemMeta(newItemMeta)

            inventory.setItem(inventorySlot, item)
            
            // if player is aim down sights, add offhand model
            if ( aimDownSights && gun.itemModelAimDownSights > 0 ) {
                this.createAimDownSightsOffhandModel(gun, player)
            }

            // send ammo reloaded message
            this.gunAmmoInfoMessageQueue.add(AmmoInfoMessagePacket(player, newAmmo, gun.ammoMax))

            // play reload finish sound
            val location = player.location
            val world = location.world
            if ( world != null ) {
                this.soundQueue.add(SoundPacket(
                    sound = gun.soundReloadFinish,
                    world = world,
                    location = location,
                    volume = gun.soundReloadFinishVolume,
                    pitch = gun.soundReloadFinishPitch,
                ))
            }
        }
        catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed to finish gun reloading task for player ${task.player.name}")
        }
    }
}

/**
 * Finish reload task after async reload wait time is
 */
internal fun XC.doGunReloadCancelled(tasks: List<PlayerReloadCancelledTask>) {
    for ( task in tasks ) {
        try {
            val (player, gun, reloadId, item, inventorySlot, playerDied) = task
            
            val inventory = player.getInventory()
            val itemGunSlot = inventory.getItem(inventorySlot)
            val itemGunSlotReloadId = itemGunSlot?.getItemMeta()?.getPersistentDataContainer()?.get(this.namespaceKeyItemReloadId, PersistentDataType.INTEGER) ?: -1

            // clear item reload data
            val itemMeta = item.getItemMeta()
            val itemData = itemMeta.getPersistentDataContainer()
            itemData.remove(this.namespaceKeyItemReloading)
            itemData.remove(this.namespaceKeyItemReloadId)
            itemData.remove(this.namespaceKeyItemReloadTimestamp)

            // set model to either regular or empty model
            val ammo = itemData.get(this.namespaceKeyItemAmmo, PersistentDataType.INTEGER) ?: 0
            item.setItemMeta(setGunItemMetaModel(itemMeta, gun, ammo, useAimDownSights(player)))
            
            // if item in gun's slot is same, then replace that item.
            // case where this is not true: if player picks up item from slot during reload
            // avoid duplicating item in that case...
            if ( itemGunSlotReloadId == reloadId ) {
                inventory.setItem(inventorySlot, item)
            }

            // send player message
            if ( !playerDied ) {
                Message.announcement(player, "${ChatColor.DARK_RED}Reload cancelled...")
            }
        }
        catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed to cancel gun reload task for player ${task.player.name}")
        }
    }
}


/**
 * Runnable task to send gun ammo info messages to players.
 */
internal class TaskAmmoInfoMessages(
    val ammoInfoMessages: ArrayList<AmmoInfoMessagePacket>,
): Runnable {
    override fun run() {
        for ( info in ammoInfoMessages ) {
            val (player, ammo, maxAmmo) = info
            if ( ammo > 0 ) {
                Message.announcement(player, "Ammo [${ammo}/${maxAmmo}]")
            } else {
                Message.announcement(player, "${ChatColor.DARK_RED}[OUT OF AMMO]")
            }
        }
    }
}


// For reload task, need to set integer-typed boolean flag indicating 
// current gun is running reload task.
private const val TRUE: Int = 1

/**
 * Format a double time value in milliseconds into a 
 * 0.x digit string in seconds.
 * e.g. 1525.2 => "1.5s"
 */
private fun formatRemainingTimeString(timeMillis: Double): String {
    val time = timeMillis.toInt()
    val seconds = time / 1000
    val remainder = time - (seconds * 1000)
    val fraction = remainder / 100
    return "${seconds}.${fraction}s"
}

/**
 * Return if inventory contains at least `amount` items with
 * material type and custom model data.
 * 
 * NOTE: item != null is NOT ALWAYS TRUE, ignore compiler
 */
private fun inventoryContainsItem(
    inventory: Inventory,
    material: Material,
    modelData: Int,
    amount: Int,
): Boolean {
    if ( amount <= 0 ) {
        return true
    }

    val items = inventory.getStorageContents()
    if ( items !== null ) {
        for ( item in items ) {
            if ( item != null && item.type == material && (item.getAmount() - amount) >= 0 ) {
                // check custom model data
                val itemMeta = item.getItemMeta()
                if ( itemMeta.hasCustomModelData() && itemMeta.getCustomModelData() == modelData ) {
                    return true
                }
            }
        }
    }

    return false
}

/**
 * Removes first "amount" of item from inventory.
 * If amount == 1, then we can just remove the first item
 * found. Otherwise, if the items are spread into multiple
 * stacks, we first build a transaction queue to make sure
 * full amount can be removed in a single transaction.
 * Returns true if items successfully removed. Otherwise, false.
 * 
 * Use `inventory.setItem(...)` and avoid `inventory.removeItem(...)`
 * Code for removeItem seems to have inefficient inner loop:
 * https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/browse/src/main/java/org/bukkit/craftbukkit/inventory/CraftInventory.java#352
 * https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/browse/src/main/java/org/bukkit/craftbukkit/inventory/CraftInventory.java#99
 * 
 * NOTE: item != null is NOT ALWAYS TRUE, ignore compiler
 */
private fun inventoryRemoveItem(
    inventory: Inventory,
    material: Material,
    modelData: Int,
    amount: Int,
): Boolean {
    if ( amount <= 0 ) {
        return true
    }
    else if ( amount == 1 ) {
        // remove first item found matching
        val items = inventory.getStorageContents()
        if ( items !== null ) {
            for ( i in 0 until items.size ) {
                val item = items[i]
                if ( item != null && item.type == material ) {
                    // check custom model data
                    val itemMeta = item.getItemMeta()
                    if ( itemMeta.hasCustomModelData() && itemMeta.getCustomModelData() == modelData ) {
                        // found matching item: remove from slot
                        if ( item.getAmount() > 1 ) {
                            item.setAmount(item.getAmount() - 1)
                            inventory.setItem(i, item)
                        } else {
                            inventory.setItem(i, null)
                        }
                        return true
                    }
                }
            }
        }
    }
    else {
        // build transaction queue from matching items
        val indicesToRemove = ArrayList<Int>(4)
        var amountLeft = amount

        val items = inventory.getStorageContents()
        if ( items !== null ) {
            for ( i in 0 until items.size ) {
                val item = items[i]
                if ( item != null && item.type == material ) {
                    // check custom model data
                    val itemMeta = item.getItemMeta()
                    if ( itemMeta.hasCustomModelData() && itemMeta.getCustomModelData() == modelData ) {
                        // found matching item: remove from slot
                        indicesToRemove.add(i)
                        amountLeft -= item.getAmount()
                        if ( amountLeft <= 0 ) {
                            break
                        }
                    }
                }
            }

            // only remove items if enough amount found across all item stacks
            if ( amountLeft <= 0 ) {
                var amountToRemove = amount

                for ( i in indicesToRemove ) {
                    val item = items[i]
                    val itemAmount = item.getAmount()           
                    if ( itemAmount > amountToRemove ) {
                        item.setAmount(itemAmount - amountToRemove)
                        inventory.setItem(i, item)
                        break
                    } else {
                        inventory.setItem(i, null)
                        amountToRemove -= itemAmount
                        if ( amountToRemove <= 0 ) {
                            break
                        }
                    }
                }

                return true
            }
        }
    }

    return false
}

/**
 * Handle recoil: ramp player recoil multiplier,
 * and queue recoil packet.
 */
private fun XC.doRecoil(
    player: Player,
    recoilHorizontal: Double,
    recoilVertical: Double,
    recoilRamp: Double,
) {
    val playerId = player.getUniqueId()

    // ramp player recoil rate
    val currRecoilMultiplier = this.playerRecoil[playerId] ?: 0.0
    val newRecoilMultiplier = min(1.0, currRecoilMultiplier + recoilRamp)
    this.playerRecoil[playerId] = newRecoilMultiplier

    // needed for adjusting recoil when player riding an entity
    // these are experimentally measured in game
    var isInVehicle = false
    var mountOffsetY = 0.0
    player.getVehicle()?.let { v ->
        isInVehicle = true
        mountOffsetY = entityMountEyeHeightOffset(v.type)
    }

    // recoil handling:
    this.recoilQueue.add(RecoilPacket(
        player = player,
        isInVehicle = isInVehicle,
        mountOffsetY = mountOffsetY,
        recoilVertical = recoilVertical,
        recoilHorizontal = recoilHorizontal,
        multiplier = newRecoilMultiplier,
    ))
}