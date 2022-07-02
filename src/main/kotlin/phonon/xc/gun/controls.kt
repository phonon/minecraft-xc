/**
 * Contain all player gun shooting controls systems and
 * support classes.
 * 
 * TODO: reload cleanup edge cases?
 * - player move item thats reloading by clicking it or using shift key
 */

package phonon.xc.gun

import java.util.UUID
import kotlin.math.max
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.entity.Player
import org.bukkit.entity.Item
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import phonon.xc.XC
import phonon.xc.utils.Message
import phonon.xc.utils.sound.SoundPacket
import phonon.xc.utils.recoil.RecoilPacket


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
    // total length of time player has been firing
    val totalTime: Double,
    // tick counter since last fired, used for timing firing rate in projectiles/tick
    val ticksSinceFired: Int,
    // number of shots remaining in this burst fire packet
    val remainingCount: Int,
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
    // total length of time player has been firing
    val totalTime: Double,
    // tick counter since last fired, used for timing firing rate in projectiles/tick
    val ticksSinceFired: Int,
    // tick counter since last auto fire request packet
    val ticksSinceLastRequest: Int,
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
 * Data for request to cleanup a player.
 */
@JvmInline
internal value class PlayerGunCleanupRequest(
    val player: Player,
)

/**
 * Data for request to cleanup an item stack.
 */
internal data class ItemGunCleanupRequest(
    val itemEntity: Item,
    val onDrop: Boolean,
)


/**
 * Data for task that runs after player finishes reloading.
 */
internal data class PlayerReloadTask(
    val player: Player,
    val gun: Gun,
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
private fun useAimDownSights(player: Player): Boolean {
    return player.isSneaking() && !XC.dontUseAimDownSights.contains(player.getUniqueId())
}

/**
 * Cleans up gun item metadata if it contains reload flags.
 * Note: THIS MUTATES THE ITEM STACK.
 * Returns:
 *  - True if item was reloading and if flags were removed.
 *  - False if item was not reloading
 */
private fun cleanupGun(item: ItemStack, gun: Gun, aimdownsights: Boolean) {
    var itemMeta = item.getItemMeta()
    val itemData = itemMeta.getPersistentDataContainer()

    val isReloading = itemData.get(XC.namespaceKeyItemReloading!!, PersistentDataType.INTEGER) ?: 0
    if ( isReloading == TRUE ) {
        // println("CLEANED UP RELOAD FLAGS!!!")
        
        // clean up reloading flags
        itemData.remove(XC.namespaceKeyItemReloading!!)
        itemData.remove(XC.namespaceKeyItemReloadId!!)
        itemData.remove(XC.namespaceKeyItemReloadTimestamp!!)
    }

    // reset model cleanup
    val ammoCurrent = itemData.get(XC.namespaceKeyItemAmmo!!, PersistentDataType.INTEGER) ?: 0
    itemMeta = setGunItemMetaModel(itemMeta, gun, ammoCurrent, aimdownsights)
    
    item.setItemMeta(itemMeta)
}


/**
 * System to aim down sights. This modifies player's gun item model if they
 * have aim down sights enabled.
 */
internal fun gunAimDownSightsSystem(requests: ArrayList<PlayerAimDownSightsRequest>) {
    for ( request in requests ) {
        val player = request.player

        if ( XC.dontUseAimDownSights.contains(player.getUniqueId()) ) {
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
            val isReloading = itemData.get(XC.namespaceKeyItemReloading!!, PersistentDataType.INTEGER) ?: 0
            if ( isReloading == TRUE ) {
                continue
            }

            // only ads if ammo > 0
            val ammo = itemData.get(XC.namespaceKeyItemAmmo!!, PersistentDataType.INTEGER) ?: 0
            if ( ammo > 0 ) {
                val isShift = player.isSneaking()
    
                if ( isShift ) {
                    itemMeta.setCustomModelData(gun.itemModelAimDownSights)
                    XC.createAimDownSightsOffhandModel(gun.itemModelAimDownSights, player)
                } else {
                    itemMeta.setCustomModelData(gun.itemModelDefault)
                    XC.removeAimDownSightsOffhandModel(player)
                }
    
                item.setItemMeta(itemMeta)
                equipment.setItem(inventorySlot, item)
            }
        }
    }
}

/**
 * System to cleanup gun items when player logs out, dies, etc.
 * - reload flags
 * - aim down sights models
 */
internal fun playerGunCleanupSystem(requests: ArrayList<PlayerGunCleanupRequest>) {
    for ( request in requests ) {
        val player = request.player

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

        // clean up aim down sights model
        XC.removeAimDownSightsOffhandModel(player)

        // clean up gun item metadata
        cleanupGun(item, gun, false)
        equipment.setItem(inventorySlot, item)
    }
}

/**
 * System to cleanup reload flags when item is dropped.
 */
internal fun gunItemCleanupSystem(requests: ArrayList<ItemGunCleanupRequest>) {
    for ( request in requests ) {
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
                    XC.removeAimDownSightsOffhandModel(player)
                }
            }
        }

        // clean up gun item reload metadata
        cleanupGun(item, gun, false)
        itemEntity.setItemStack(item)
    }
}

/**
 * Player swap to gun system:
 * 1. Handle cleaning up reload if gun still has reloading flags.
 * 2. Add shooting delay if swapping from another gun.
 * 3. Send ammo info to player.
 */
internal fun gunSelectSystem(requests: ArrayList<PlayerGunSelectRequest>) {
    for ( request in requests ) {
        val player = request.player

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

        // check if playing is aim down sights
        val aimDownSights = useAimDownSights(player)

        // clean up gun item metadata
        cleanupGun(item, gun, aimDownSights)
        equipment.setItem(inventorySlot, item)

        // gun swap delay:
        // -> goal is to block players fast swapping between guns
        //    (e.g. using macros) to bypass gun shooting delays
        //    or to swap between different types (e.g. rifle -> shotgun)
        // See `docs/delay.md` for details.
        XC.playerShootDelay[player.getUniqueId()]?.let { shootDelay ->
            XC.playerShootDelay[player.getUniqueId()] = shootDelay.copy(
                timestampCanShoot = shootDelay.timestampDelayAfterShoot + gun.equipDelayMillis,
            )
        }

        // send ammo info to player
        val ammo = getAmmoFromItem(item)
        if ( ammo != null ) {
            XC.gunAmmoInfoMessageQueue.add(AmmoInfoMessagePacket(player, ammo, gun.ammoMax))
            val itemNewModel = setGunItemStackModel(item, gun, ammo, aimDownSights)
            equipment.setItem(inventorySlot, itemNewModel)

            // if player is aim down sights, add offhand model
            if ( ammo > 0 && aimDownSights ) {
                XC.createAimDownSightsOffhandModel(gun.itemModelAimDownSights, player)
            }
        }
    }
}


/**
 * Player shooting system
 */
internal fun gunPlayerShootSystem(requests: ArrayList<PlayerGunShootRequest>, time: Long) {
    for ( request in requests ) {
        val player = request.player
        
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

        val loc = player.location
        val world = loc.world
        val projectileSystem = XC.projectileSystems[world.getUID()]
        if ( projectileSystem == null ) return

        var itemMeta = item.getItemMeta()
        val itemData = itemMeta.getPersistentDataContainer()
        
        // check gun is reloading
        // if reloading is past time + some margin, cancel reload and clear flags
        val reloadTimeMillis = gun.reloadTimeMillis
        val isReloading = itemData.get(XC.namespaceKeyItemReloading!!, PersistentDataType.INTEGER) ?: 0
        if ( isReloading == TRUE ) {
            // Check reload timestamp, if current time > timestamp0 + reloadTime + 1000 ms,
            // assume this gun's reload was broken somehow and continue with shoot.
            // Else, this is still in a reload process: skip shooting
            val reloadTimestamp = itemData.get(XC.namespaceKeyItemReloadTimestamp!!, PersistentDataType.LONG)
            if ( reloadTimestamp != null && System.currentTimeMillis() < reloadTimestamp + reloadTimeMillis + 1000L ) {
                continue
            }

            // clean up reloading flags
            itemData.remove(XC.namespaceKeyItemReloading!!)
            itemData.remove(XC.namespaceKeyItemReloadId!!)
            itemData.remove(XC.namespaceKeyItemReloadTimestamp!!)
        }

        // check if still under shoot delay
        val shootDelay = XC.playerShootDelay[player.getUniqueId()]
        if ( shootDelay != null && time < shootDelay.timestampCanShoot ) {
            continue
        }

        val fireMode = gun.singleFireMode

        if ( fireMode == GunSingleFireMode.SINGLE ) {
            
            // check ammo and send ammo info message to player
            val ammo = getAmmoFromItem(item) ?: 0
            if ( ammo <= 0 ) {
                XC.gunAmmoInfoMessageQueue.add(AmmoInfoMessagePacket(player, ammo, gun.ammoMax))
                
                // play gun empty sound effect
                XC.soundQueue.add(SoundPacket(
                    sound = gun.soundEmpty,
                    world = world,
                    location = loc,
                    volume = gun.soundEmptyVolume,
                    pitch = gun.soundEmptyPitch,
                ))

                if ( !gun.ammoIgnore ) {
                    continue
                }
            }

            val newAmmo = max(0, ammo - 1)

            // update player item: set new ammo and set model
            itemMeta = setGunItemMetaAmmo(itemMeta, gun, newAmmo)
            itemMeta = setGunItemMetaModel(itemMeta, gun, newAmmo, useAimDownSights(player))
            item.setItemMeta(itemMeta)
            equipment.setItem(inventorySlot, item)

            // if newAmmo is 0, remove any aim down sights model
            if ( newAmmo == 0 ) {
                XC.removeAimDownSightsOffhandModel(player)
            }

            XC.gunAmmoInfoMessageQueue.add(AmmoInfoMessagePacket(player, newAmmo, gun.ammoMax))
            
            val eyeHeight = player.eyeHeight
            val shootPosition = loc.clone().add(0.0, eyeHeight, 0.0)
            val shootDirection = loc.direction.clone()
    
            val projectile = Projectile(
                gun = gun,
                source = player,
                x = shootPosition.x.toFloat(),
                y = shootPosition.y.toFloat(),
                z = shootPosition.z.toFloat(),
                dirX = shootDirection.x.toFloat(),
                dirY = shootDirection.y.toFloat(),
                dirZ = shootDirection.z.toFloat(),
                speed = gun.projectileVelocity,
                gravity = gun.projectileGravity,
                maxLifetime = gun.projectileLifetime,
                maxDistance = gun.projectileMaxDistance,
            )
    
            projectileSystem.addProjectile(projectile)
            
            // shoot sound
            XC.soundQueue.add(SoundPacket(
                sound = gun.soundShoot,
                world = world,
                location = loc,
                volume = gun.soundShootVolume,
                pitch = gun.soundShootPitch,
            ))

            // recoil packet
            XC.recoilQueue.add(RecoilPacket(
                player = player,
                gun = gun,
            ))

            // shoot delay
            val timestampShootDelay = time + gun.shootDelayMillis
            XC.playerShootDelay[player.getUniqueId()] = ShootDelay(
                timestampDelayAfterShoot = timestampShootDelay,
                timestampCanShoot = timestampShootDelay,
            )
    
            // println("Shooting: ${gun}")
        } else if ( fireMode == GunSingleFireMode.BURST ) {
            // add burst packet if not already burst firing
            if ( !XC.burstFiringPackets.contains(player.getUniqueId()) ) {
                val burstFireId = XC.newBurstFireId()

                XC.burstFiringPackets[player.getUniqueId()] = BurstFire(
                    id = burstFireId,
                    player = player,
                    gun = gun,
                    totalTime = 0.0,
                    ticksSinceFired = 0,
                    remainingCount = gun.burstFireCount,
                )

                // set item in hand's burst fire id
                itemData.set(XC.namespaceKeyItemBurstFireId!!, PersistentDataType.INTEGER, burstFireId)
                item.setItemMeta(itemMeta)
                equipment.setItem(inventorySlot, item)
            }
        } else { // fireMode == GunSingleFireMode.NONE
            // no-op
            return
        }

    }
}


/**
 * Player burst fire shooting system. Return a new hashmap
 * of burst fire packets for next tick.
 */
internal fun burstFireSystem(requests: HashMap<UUID, BurstFire>, time: Long): HashMap<UUID, BurstFire> {
    val nextTickRequests = HashMap<UUID, BurstFire>()

    for ( (playerId, request) in requests ) {
        val (id, player, gun, totalTime, ticksSinceFired, remainingCount) = request

        // Do redundant player main hand is gun check here
        // since events could override the first shoot event, causing
        // inventory slot or item to change
        val equipment = player.getInventory()
        val inventorySlot = equipment.getHeldItemSlot()
        val item = equipment.getItem(inventorySlot)
        if ( item == null ) {
            continue
        }

        var itemMeta = item.getItemMeta()
        val itemData = itemMeta.getPersistentDataContainer()

        // check if item burst fire id matches
        val itemBurstFireId = itemData?.get(XC.namespaceKeyItemBurstFireId!!, PersistentDataType.INTEGER) ?: -1
        if ( itemBurstFireId != id ) {
            continue
        }

        // decrement burst delay
        if ( ticksSinceFired > 0 ) {
            nextTickRequests[playerId] = BurstFire(
                id = id,
                player = player,
                gun = gun,
                totalTime = totalTime + 1,
                ticksSinceFired = ticksSinceFired - 1,
                remainingCount = remainingCount,
            )
            continue
        }

        val loc = player.location
        val world = loc.world
        val projectileSystem = XC.projectileSystems[world.getUID()]
        if ( projectileSystem == null ) continue

        // check ammo and send ammo info message to player
        val ammo = getAmmoFromItem(item) ?: 0
        if ( ammo <= 0 ) {
            XC.gunAmmoInfoMessageQueue.add(AmmoInfoMessagePacket(player, ammo, gun.ammoMax))
            
            // play gun empty sound effect
            XC.soundQueue.add(SoundPacket(
                sound = gun.soundEmpty,
                world = world,
                location = loc,
                volume = gun.soundEmptyVolume,
                pitch = gun.soundEmptyPitch,
            ))

            if ( !gun.ammoIgnore ) {
                continue
            }
        }

        val newAmmo = max(0, ammo - 1)

        // update player item: set new ammo and set model
        itemMeta = setGunItemMetaAmmo(itemMeta, gun, newAmmo)
        itemMeta = setGunItemMetaModel(itemMeta, gun, newAmmo, useAimDownSights(player))
        item.setItemMeta(itemMeta)
        equipment.setItem(inventorySlot, item)

        // if newAmmo is 0, remove any aim down sights model and also play empty sound
        if ( newAmmo == 0 ) {
            XC.removeAimDownSightsOffhandModel(player)

            XC.soundQueue.add(SoundPacket(
                sound = gun.soundEmpty,
                world = world,
                location = loc,
                volume = gun.soundEmptyVolume,
                pitch = gun.soundEmptyPitch,
            ))
        }

        XC.gunAmmoInfoMessageQueue.add(AmmoInfoMessagePacket(player, newAmmo, gun.ammoMax))
        
        val eyeHeight = player.eyeHeight
        val shootPosition = loc.clone().add(0.0, eyeHeight, 0.0)
        val shootDirection = loc.direction.clone()

        val projectile = Projectile(
            gun = gun,
            source = player,
            x = shootPosition.x.toFloat(),
            y = shootPosition.y.toFloat(),
            z = shootPosition.z.toFloat(),
            dirX = shootDirection.x.toFloat(),
            dirY = shootDirection.y.toFloat(),
            dirZ = shootDirection.z.toFloat(),
            speed = gun.projectileVelocity,
            gravity = gun.projectileGravity,
            maxLifetime = gun.projectileLifetime,
            maxDistance = gun.projectileMaxDistance,
        )

        projectileSystem.addProjectile(projectile)

        // shoot sound
        XC.soundQueue.add(SoundPacket(
            sound = gun.soundShoot,
            world = world,
            location = loc,
            volume = gun.soundShootVolume,
            pitch = gun.soundShootPitch,
        ))

        // recoil packet
        XC.recoilQueue.add(RecoilPacket(
            player = player,
            gun = gun,
        ))

        // continue sequence if have ammo and burst has remaining shots
        if ( remainingCount > 1 && ( newAmmo > 0 || gun.ammoIgnore ) ) {
            nextTickRequests[playerId] = BurstFire(
                id = id,
                player = player,
                gun = gun,
                totalTime = totalTime + 1,
                ticksSinceFired = gun.burstFireDelayTicks,
                remainingCount = remainingCount - 1,
            )
        } else {
            // burst done.

            // add shooting delay
            val timestampShootDelay = time + gun.shootDelayMillis
            XC.playerShootDelay[playerId] = ShootDelay(
                timestampDelayAfterShoot = timestampShootDelay,
                timestampCanShoot = timestampShootDelay,
            )
        }
    }

    return nextTickRequests
}

/**
 * Automatic fire request system. Initiate or refresh an auto fire sequence.
 */
internal fun autoFireRequestSystem(requests: ArrayList<PlayerAutoFireRequest>, autoFiring: HashMap<UUID, AutoFire>, time: Long): HashMap<UUID, AutoFire> {
    for ( request in requests ) {
        val player = request.player

        val playerId = player.getUniqueId()

        // check if still under shoot delay
        val shootDelay = XC.playerShootDelay[playerId]
        if ( shootDelay != null && time < shootDelay.timestampCanShoot ) {
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
            val isReloading = itemData.get(XC.namespaceKeyItemReloading!!, PersistentDataType.INTEGER) ?: 0
            if ( isReloading == TRUE ) {
                // Check reload timestamp, if current time > timestamp0 + reloadTime + 1000 ms,
                // assume this gun's reload was broken somehow and continue with shoot.
                // Else, this is still in a reload process: skip shooting
                val reloadTimestamp = itemData.get(XC.namespaceKeyItemReloadTimestamp!!, PersistentDataType.LONG)
                if ( reloadTimestamp != null && System.currentTimeMillis() < reloadTimestamp + reloadTimeMillis + 1000L ) {
                    continue
                }

                // clean up reloading flags
                itemData.remove(XC.namespaceKeyItemReloading!!)
                itemData.remove(XC.namespaceKeyItemReloadId!!)
                itemData.remove(XC.namespaceKeyItemReloadTimestamp!!)
            }

            val autoFireId = XC.newAutoFireId()

            autoFiring[playerId] = AutoFire(
                id = autoFireId,
                player = player,
                gun = gun,
                totalTime = 0.0,
                ticksSinceFired = 0,
                ticksSinceLastRequest = 0,
            )
            
            // set item in hand's auto fire id
            itemData.set(XC.namespaceKeyItemAutoFireId!!, PersistentDataType.INTEGER, autoFireId)
            item.setItemMeta(itemMeta)
            equipment.setItem(inventorySlot, item)
        }
    }

    return autoFiring
}

/**
 * Player automatic fire shooting system. Return a new hashmap
 * of auto fire packets for next tick.
 */
internal fun autoFireSystem(requests: HashMap<UUID, AutoFire>): HashMap<UUID, AutoFire> {
    val nextTickRequests = HashMap<UUID, AutoFire>()
    
    for ( (playerId, request) in requests ) {
        val (id, player, gun, totalTime, ticksSinceFired, ticksSinceLastRequest) = request

        if ( ticksSinceLastRequest >= XC.config.autoFireMaxTicksSinceLastRequest ) {
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

        var itemMeta = item.getItemMeta()
        val itemData = itemMeta.getPersistentDataContainer()

        // check if item firing id matches
        val itemFireId = itemData?.get(XC.namespaceKeyItemAutoFireId!!, PersistentDataType.INTEGER) ?: -1
        if ( itemFireId != id ) {
            continue
        }

        // decrement auto fire delay
        if ( ticksSinceFired > 0 ) {
            nextTickRequests[playerId] = AutoFire(
                id = id,
                player = player,
                gun = gun,
                totalTime = totalTime + 1,
                ticksSinceFired = ticksSinceFired - 1,
                ticksSinceLastRequest = ticksSinceLastRequest + 1,
            )
            continue
        }

        val loc = player.location
        val world = loc.world
        val projectileSystem = XC.projectileSystems[world.getUID()]
        if ( projectileSystem == null ) continue

        // check ammo and send ammo info message to player
        val ammo = getAmmoFromItem(item) ?: 0
        if ( ammo <= 0 ) {
            XC.gunAmmoInfoMessageQueue.add(AmmoInfoMessagePacket(player, ammo, gun.ammoMax))
            
            // play gun empty sound effect
            XC.soundQueue.add(SoundPacket(
                sound = gun.soundEmpty,
                world = world,
                location = loc,
                volume = gun.soundEmptyVolume,
                pitch = gun.soundEmptyPitch,
            ))

            if ( !gun.ammoIgnore ) {
                continue
            }
        }

        val newAmmo = max(0, ammo - 1)

        // update player item: set new ammo and set model
        itemMeta = setGunItemMetaAmmo(itemMeta, gun, newAmmo)
        itemMeta = setGunItemMetaModel(itemMeta, gun, newAmmo, useAimDownSights(player))
        item.setItemMeta(itemMeta)
        equipment.setItem(inventorySlot, item)

        // if newAmmo is 0, remove any aim down sights model and also play empty sound
        if ( newAmmo == 0 ) {
            XC.removeAimDownSightsOffhandModel(player)

            XC.soundQueue.add(SoundPacket(
                sound = gun.soundEmpty,
                world = world,
                location = loc,
                volume = gun.soundEmptyVolume,
                pitch = gun.soundEmptyPitch,
            ))
        }

        XC.gunAmmoInfoMessageQueue.add(AmmoInfoMessagePacket(player, newAmmo, gun.ammoMax))
        
        val eyeHeight = player.eyeHeight
        val shootPosition = loc.clone().add(0.0, eyeHeight, 0.0)
        val shootDirection = loc.direction.clone()

        val projectile = Projectile(
            gun = gun,
            source = player,
            x = shootPosition.x.toFloat(),
            y = shootPosition.y.toFloat(),
            z = shootPosition.z.toFloat(),
            dirX = shootDirection.x.toFloat(),
            dirY = shootDirection.y.toFloat(),
            dirZ = shootDirection.z.toFloat(),
            speed = gun.projectileVelocity,
            gravity = gun.projectileGravity,
            maxLifetime = gun.projectileLifetime,
            maxDistance = gun.projectileMaxDistance,
        )

        projectileSystem.addProjectile(projectile)

        // shoot sound
        XC.soundQueue.add(SoundPacket(
            sound = gun.soundShoot,
            world = world,
            location = loc,
            volume = gun.soundShootVolume,
            pitch = gun.soundShootPitch,
        ))

        // recoil packet
        XC.recoilQueue.add(RecoilPacket(
            player = player,
            gun = gun,
        ))

        // continue sequence if have ammo and burst has remaining shots
        if ( gun.ammoIgnore || newAmmo > 0 ) {
            nextTickRequests[playerId] = AutoFire(
                id = id,
                player = player,
                gun = gun,
                totalTime = totalTime + 1,
                ticksSinceFired = gun.autoFireDelayTicks,
                ticksSinceLastRequest = ticksSinceLastRequest + 1,
            )
        }

    }

    return nextTickRequests
}

/**
 * Player reload request system
 */
internal fun gunPlayerReloadSystem(requests: ArrayList<PlayerGunReloadRequest>) {
    for ( request in requests ) {
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
        if ( XC.burstFiringPackets[playerId] != null || XC.autoFiringPackets[playerId] != null ) {
            continue
        }

        var itemMeta = item.getItemMeta()
        val itemData = itemMeta.getPersistentDataContainer()
        
        // do reload if gun not reloading already and ammo less than max
        val reloadTimeMillis = gun.reloadTimeMillis
        val isReloading = itemData.get(XC.namespaceKeyItemReloading!!, PersistentDataType.INTEGER) ?: 0
        
        if ( isReloading == TRUE ) {
            // Check reload timestamp, if current time > timestamp0 + reloadTime + 1000 ms,
            // assume this gun's reload was broken somehow and continue starting
            // a new reload task. Otherwise, we are still reloading, so exit.
            val reloadTimestamp = itemData.get(XC.namespaceKeyItemReloadTimestamp!!, PersistentDataType.LONG)
            if ( reloadTimestamp != null && System.currentTimeMillis() < reloadTimestamp + reloadTimeMillis + 1000L ) {
                continue
            }
        }

        val ammoCurrent = itemData.get(XC.namespaceKeyItemAmmo!!, PersistentDataType.INTEGER) ?: 0
        val ammoMax = gun.ammoMax
        if ( ammoCurrent >= ammoMax ) {
            continue
        }

        // check that player has enough ammo in inventory
        val ammoId = gun.ammoId
        if ( !inventoryContainsItem(player.getInventory(), XC.config.materialAmmo, ammoId, 1) ) {
            Message.announcement(player, "${ChatColor.DARK_RED}[No ammo in inventory]")
            continue
        }

        // set reload flag and reloading id: this ensures this is same item
        // being reloaded when reload task finishes
        val reloadId = XC.newReloadId()
        itemData.set(XC.namespaceKeyItemReloading!!, PersistentDataType.INTEGER, TRUE)
        itemData.set(XC.namespaceKeyItemReloadId!!, PersistentDataType.INTEGER, reloadId)

        // set timestamp when reload started.
        val currentTimeMillis = System.currentTimeMillis()
        itemData.set(XC.namespaceKeyItemReloadTimestamp!!, PersistentDataType.LONG, currentTimeMillis)
        
        // set reloading model
        itemMeta = setGunItemMetaReloadModel(itemMeta, gun)

        // update item meta with new data
        item.setItemMeta(itemMeta)
        equipment.setItem(inventorySlot, item)

        // remove any aim down sights model
        XC.removeAimDownSightsOffhandModel(player)

        // play reload start sound
        val location = player.location
        val world = location.world
        if ( world != null ) {
            XC.soundQueue.add(SoundPacket(
                sound = gun.soundReloadStart,
                world = world,
                location = location,
                volume = gun.soundReloadStartVolume,
                pitch = gun.soundReloadStartPitch,
            ))
        }

        // launch reload task
        val reloadTask = object: BukkitRunnable() {
            private val player = player
            private val gun = gun
            private val reloadId = reloadId
            private val inventorySlot = inventorySlot
            private val reloadTime = reloadTimeMillis.toDouble()
            private val startTime = currentTimeMillis.toDouble()
            private val itemGunMaterial = XC.config.materialGun
            private val itemDataKeyReloadId = XC.namespaceKeyItemReloadId!!
            private val reloadFinishTaskQueue = XC.playerReloadTaskQueue
            private val reloadCancelledTaskQueue = XC.playerReloadCancelledTaskQueue

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

                val timeElapsedMillis = System.currentTimeMillis().toDouble() - startTime
                if ( timeElapsedMillis > reloadTime ) {
                    // reload done: add reload finish task to queue
                    reloadFinishTaskQueue.add(PlayerReloadTask(player, gun, item, reloadId, inventorySlot))
                    this.cancel()
                } else {
                    val progress = timeElapsedMillis / reloadTime
                    val strRemainingTime = formatRemainingTimeString(reloadTime - timeElapsedMillis)
                    Message.announcement(player, "Reloading ${progressBarReload(progress)} ${strRemainingTime}")
                }
            }
        }

        // runs every 2 ticks = 100 ms
        reloadTask.runTaskTimerAsynchronously(XC.plugin!!, 0L, 1L)
    }
}

/**
 * Finish reload task after async reload wait time finishes.
 */
internal fun doGunReload(tasks: ArrayList<PlayerReloadTask>) {
    for ( task in tasks ) {
        val (player, gun, item, reloadId, inventorySlot) = task

        // remove ammo item from player inventory
        if ( !inventoryRemoveItem(player.getInventory(), XC.config.materialAmmo, gun.ammoId, 1) ) {
            Message.announcement(player, "${ChatColor.DARK_RED}[No ammo in inventory]")
            continue
        }

        // check player main hand same and item same
        val inventory = player.getInventory()
        val currentMainHandSlot = inventory.getHeldItemSlot()
        val itemInHand = inventory.getItemInMainHand()
        val itemInHandReloadId = itemInHand.getItemMeta().getPersistentDataContainer().get(XC.namespaceKeyItemReloadId!!, PersistentDataType.INTEGER) ?: -1
        if ( currentMainHandSlot != inventorySlot || itemInHandReloadId != reloadId ) {
            // this should never actually happen...
            Message.announcement(player, "${ChatColor.DARK_RED}[Item changed, reload cancelled]")
            continue
        }

        // new ammo amount
        // TODO: adjustable reloading, either load to max or add # of projectiles
        val newAmmo = gun.ammoMax

        // use ads flag
        val aimDownSights = useAimDownSights(player)

        // clear item reload data and set ammo
        var itemMeta = item.getItemMeta()
        itemMeta = setGunItemMetaAmmo(itemMeta, gun, newAmmo)
        itemMeta = setGunItemMetaModel(itemMeta, gun, newAmmo, aimDownSights)
        val itemData = itemMeta.getPersistentDataContainer()
        itemData.remove(XC.namespaceKeyItemReloading!!)
        itemData.remove(XC.namespaceKeyItemReloadId!!)
        itemData.remove(XC.namespaceKeyItemReloadTimestamp!!)
        item.setItemMeta(itemMeta)

        inventory.setItem(inventorySlot, item)
        
        // if player is aim down sights, add offhand model
        if ( aimDownSights ) {
            XC.createAimDownSightsOffhandModel(gun.itemModelAimDownSights, player)
        }

        // send ammo reloaded message
        XC.gunAmmoInfoMessageQueue.add(AmmoInfoMessagePacket(player, newAmmo, gun.ammoMax))

        // play reload finish sound
        val location = player.location
        val world = location.world
        if ( world != null ) {
            XC.soundQueue.add(SoundPacket(
                sound = gun.soundReloadFinish,
                world = world,
                location = location,
                volume = gun.soundReloadFinishVolume,
                pitch = gun.soundReloadFinishPitch,
            ))
        }
    }
}

/**
 * Finish reload task after async reload wait time is
 */
internal fun doGunReloadCancelled(tasks: ArrayList<PlayerReloadCancelledTask>) {
    for ( task in tasks ) {
        val (player, gun, reloadId, item, inventorySlot, playerDied) = task
        
        val inventory = player.getInventory()
        val itemGunSlot = inventory.getItem(inventorySlot)
        val itemGunSlotReloadId = itemGunSlot?.getItemMeta()?.getPersistentDataContainer()?.get(XC.namespaceKeyItemReloadId!!, PersistentDataType.INTEGER) ?: -1

        // clear item reload data
        val itemMeta = item.getItemMeta()
        val itemData = itemMeta.getPersistentDataContainer()
        itemData.remove(XC.namespaceKeyItemReloading!!)
        itemData.remove(XC.namespaceKeyItemReloadId!!)
        itemData.remove(XC.namespaceKeyItemReloadTimestamp!!)

        // set model to either regular or empty model
        val ammo = itemData.get(XC.namespaceKeyItemAmmo!!, PersistentDataType.INTEGER) ?: 0
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
 * Create progress bar string. Input should be double
 * in range [0.0, 1.0] marking progress.
 */
private fun progressBarReload(progress: Double): String {
    // available shades
    // https://en.wikipedia.org/wiki/Box-drawing_character
    // val SOLID = 2588     // full solid block
    // val SHADE0 = 2592    // medium shade
    // val SHADE1 = 2593    // dark shade

    return when ( Math.round(progress * 10.0).toInt() ) {
        0 ->  "\u2503${ChatColor.DARK_GRAY}\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592${ChatColor.WHITE}\u2503"
        1 ->  "\u2503\u2588${ChatColor.DARK_GRAY}\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592${ChatColor.WHITE}\u2503"
        2 ->  "\u2503\u2588\u2588${ChatColor.DARK_GRAY}\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592${ChatColor.WHITE}\u2503"
        3 ->  "\u2503\u2588\u2588\u2588${ChatColor.DARK_GRAY}\u2592\u2592\u2592\u2592\u2592\u2592\u2592${ChatColor.WHITE}\u2503"
        4 ->  "\u2503\u2588\u2588\u2588\u2588${ChatColor.DARK_GRAY}\u2592\u2592\u2592\u2592\u2592\u2592${ChatColor.WHITE}\u2503"
        5 ->  "\u2503\u2588\u2588\u2588\u2588\u2588${ChatColor.DARK_GRAY}\u2592\u2592\u2592\u2592\u2592${ChatColor.WHITE}\u2503"
        6 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588${ChatColor.DARK_GRAY}\u2592\u2592\u2592\u2592${ChatColor.WHITE}\u2503"
        7 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588${ChatColor.DARK_GRAY}\u2592\u2592\u2592${ChatColor.WHITE}\u2503"
        8 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588${ChatColor.DARK_GRAY}\u2592\u2592${ChatColor.WHITE}\u2503"
        9 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588${ChatColor.DARK_GRAY}\u2592${ChatColor.WHITE}\u2503"
        10 -> "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2503"
        else -> ""
    }
}

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

    for ( item in inventory.getStorageContents() ) {
        if ( item != null && item.type == material && (item.getAmount() - amount) >= 0 ) {
            // check custom model data
            val itemMeta = item.getItemMeta()
            if ( itemMeta.hasCustomModelData() && itemMeta.getCustomModelData() == modelData ) {
                return true
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
    else {
        // build transaction queue from matching items
        val indicesToRemove = ArrayList<Int>(4)
        var amountLeft = amount

        val items = inventory.getStorageContents()
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

    return false
}
