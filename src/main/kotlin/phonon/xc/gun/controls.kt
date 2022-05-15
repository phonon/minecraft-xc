/**
 * Contain all player gun shooting controls systems and
 * support classes.
 */

package phonon.xc.gun

import kotlin.math.max
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import phonon.xc.XC
import phonon.xc.utils.Message
import phonon.xc.utils.sound.SoundPacket


/**
 * Async request for sending an ammo message packet to player.
 */
internal data class AmmoInfoMessagePacket(
    val player: Player,
    val ammo: Int,
    val maxAmmo: Int,
)

/**
 * Holds player automatic firing state for a gun.
 */
internal data class AutomaticFiring(
    // player firing
    val player: Player,
    // gun
    val gun: Gun,
    // total length of time player has been firing
    val totalTime: Double,
    // tick counter since last fired, used for timing firing rate in projectiles/tick
    val ticksSinceFired: Int,
)

internal data class PlayerGunShootRequest(
    val player: Player,
    val gun: Gun,
    val item: ItemStack,
    val inventorySlot: Int,
)

internal data class PlayerGunReloadRequest(
    val player: Player,
    val gun: Gun,
    val item: ItemStack,
    val inventorySlot: Int,
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
    val item: ItemStack,
    val playerDied: Boolean,
)


/**
 * Player shooting system
 */
internal fun gunPlayerShootSystem(requests: ArrayList<PlayerGunShootRequest>) {
    for ( request in requests ) {
        val (player, gun, item, inventorySlot) = request
        
        val loc = player.location
        val world = loc.world
        val projectileSystem = XC.projectileSystems[world.getUID()]
        if ( projectileSystem == null ) return

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
        var itemMeta = item.getItemMeta()
        itemMeta = setGunItemMetaAmmo(itemMeta, gun, newAmmo)
        itemMeta = setGunItemMetaModel(itemMeta, gun, newAmmo)
        item.setItemMeta(itemMeta)
        player.getInventory().setItem(inventorySlot, item)

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

        XC.soundQueue.add(SoundPacket(
            sound = gun.soundShoot,
            world = world,
            location = loc,
            volume = gun.soundShootVolume,
            pitch = gun.soundShootPitch,
        ))

        // println("Shooting: ${gun}")
    }
}


/**
 * Player reload request system
 */
internal fun gunPlayerReloadSystem(requests: ArrayList<PlayerGunReloadRequest>) {
    for ( request in requests ) {
        val (player, gun, item, inventorySlot) = request
        
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
        val reloadId = XC.getReloadId()
        itemData.set(XC.namespaceKeyItemReloading!!, PersistentDataType.INTEGER, TRUE)
        itemData.set(XC.namespaceKeyItemReloadId!!, PersistentDataType.INTEGER, reloadId)

        // set timestamp when reload started.
        val currentTimeMillis = System.currentTimeMillis()
        itemData.set(XC.namespaceKeyItemReloadTimestamp!!, PersistentDataType.LONG, currentTimeMillis)
        
        // set reloading model
        itemMeta = setGunItemMetaReloadModel(itemMeta, gun)

        // update item meta with new data
        item.setItemMeta(itemMeta)
        player.getInventory().setItem(inventorySlot, item)

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
                reloadCancelledTaskQueue.add(PlayerReloadCancelledTask(player, gun, item, playerDied))
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

        // clear item reload data and set ammo
        var itemMeta = item.getItemMeta()
        itemMeta = setGunItemMetaAmmo(itemMeta, gun, newAmmo)
        itemMeta = setGunItemMetaModel(itemMeta, gun, newAmmo)
        val itemData = itemMeta.getPersistentDataContainer()
        itemData.remove(XC.namespaceKeyItemReloading!!)
        itemData.remove(XC.namespaceKeyItemReloadId!!)
        itemData.remove(XC.namespaceKeyItemReloadTimestamp!!)
        item.setItemMeta(itemMeta)

        inventory.setItem(inventorySlot, item)

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
        val (player, gun, item, playerDied) = task
        
        // clear item reload data
        val itemMeta = item.getItemMeta()
        val itemData = itemMeta.getPersistentDataContainer()
        itemData.remove(XC.namespaceKeyItemReloading!!)
        itemData.remove(XC.namespaceKeyItemReloadId!!)
        itemData.remove(XC.namespaceKeyItemReloadTimestamp!!)

        // set model to either regular or empty model
        val ammo = itemData.get(XC.namespaceKeyItemAmmo!!, PersistentDataType.INTEGER) ?: 0
        item.setItemMeta(setGunItemMetaModel(itemMeta, gun, ammo))
        
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
