/**
 * Contain all player gun shooting controls systems and
 * support classes.
 */

package phonon.xc.gun

import kotlin.math.max
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import phonon.xc.XC
import phonon.xc.utils.Message


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
 * Holds player automatic firing state for a gun.
 */
internal data class AutomaticFiring(
    // player firing
    val player: Player,
    // gun
    val gun: Gun,
    // total length of time player has been firing
    val totalTime: Double,
    // tick counter since last fired, used for timing firing rate in bullets/tick
    val ticksSinceFired: Int,
)

internal data class PlayerGunShootRequest(
    val player: Player,
    val gun: Gun,
    val item: ItemStack,
)

internal data class PlayerGunReloadRequest(
    val player: Player,
    val gun: Gun,
    val item: ItemStack,
)

/**
 * Data for task that runs after player finishes reloading.
 */
internal data class PlayerReloadTask(
    val player: Player,
    val gun: Gun,
    val item: ItemStack,
    val reloadId: Int,
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
        val (player, gun, item) = request
        
        val loc = player.location
        val world = loc.world
        val projectileSystem = XC.projectileSystems[world.getUID()]
        if ( projectileSystem == null ) return

        // check ammo and update text
        val ammo = getAmmoFromItem(item) ?: 0
        if ( ammo <= 0 ) {
            Message.announcement(player, "${ChatColor.DARK_RED}[OUT OF AMMO]")
        }
        val newAmmo = max(0, ammo - 1)
        updateGunItemAmmo(item, gun, newAmmo)
        if ( newAmmo > 0 ) {
            Message.announcement(player, "Ammo [${newAmmo}/${gun.ammoMax}]")
        } else {
            Message.announcement(player, "${ChatColor.DARK_RED}[OUT OF AMMO]")
        }

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
            speed = gun.bulletVelocity,
            gravity = gun.bulletGravity,
            maxLifetime = gun.bulletLifetime,
            maxDistance = gun.bulletMaxDistance,
        )

        projectileSystem.addProjectile(projectile)

        Message.print(player, "Firing")
        println("Shooting: ${gun}")
    }
}


/**
 * Player reload request system
 */
internal fun gunPlayerReloadSystem(requests: ArrayList<PlayerGunReloadRequest>) {
    for ( request in requests ) {
        val (player, gun, item) = request

        println("Reload: ${gun}")
        
        val itemMeta = item.getItemMeta()
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
            return
        }

        // check that player has enough ammo in inventory
        // TODO

        // set reload flag and reloading id: this ensures this is same item
        // being reloaded when reload task finishes
        val reloadId = XC.getReloadId()
        itemData.set(XC.namespaceKeyItemReloading!!, PersistentDataType.INTEGER, TRUE)
        itemData.set(XC.namespaceKeyItemReloadId!!, PersistentDataType.INTEGER, reloadId)

        // set timestamp when reload started.
        val currentTimeMillis = System.currentTimeMillis()
        itemData.set(XC.namespaceKeyItemReloadTimestamp!!, PersistentDataType.LONG, currentTimeMillis)

        // update item meta with new data
        item.setItemMeta(itemMeta)

        // launch reload task
        val reloadTask = object: BukkitRunnable() {
            private val player = player
            private val gun = gun
            private val reloadId = reloadId
            private val reloadTime = reloadTimeMillis.toDouble()
            private val startTime = currentTimeMillis.toDouble()
            private val itemGunMaterial = XC.config.materialGun
            private val itemDataKeyReloadId = XC.namespaceKeyItemReloadId!!
            private val reloadFinishTaskQueue = XC.playerReloadTaskQueue
            private val reloadCancelledTaskQueue = XC.playerReloadCancelledTaskQueue

            private fun cancelReload(playerDied: Boolean) {
                println("RELOAD TASK CANCALLED!!! playerDied=${playerDied}")
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
                    println("ITEM DOES NOT MATCH, CANCELLING (itemReloadId=${itemReloadId}, original=${reloadId}")
                    this.cancelReload(false)
                    return
                }

                val timeElapsedMillis = System.currentTimeMillis().toDouble() - startTime
                if ( timeElapsedMillis > reloadTime ) {
                    println("RELOAD TASK FINISHED!!!")
                    // reload done: add reload finish task to queue
                    reloadFinishTaskQueue.add(PlayerReloadTask(player, gun, item, reloadId))
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
 * Finish reload task after async reload wait time is
 */
internal fun doGunReload(tasks: ArrayList<PlayerReloadTask>) {
    for ( task in tasks ) {
        val (player, gun, item, reloadId) = task

        // new ammo amount
        // TODO: adjustable reloading, either load to max or add # of bullets
        val newAmmo = gun.ammoMax

        // clear item reload data and set ammo
        val itemMeta = item.getItemMeta()
        updateGunItemMetaAmmo(itemMeta, gun, newAmmo)
        val itemData = itemMeta.getPersistentDataContainer()
        itemData.remove(XC.namespaceKeyItemReloading!!)
        itemData.remove(XC.namespaceKeyItemReloadId!!)
        itemData.remove(XC.namespaceKeyItemReloadTimestamp!!)
        item.setItemMeta(itemMeta)

        Message.announcement(player, "${ChatColor.WHITE} Ammo [${newAmmo}/${gun.ammoMax}]")

        println("FINISHING RELOAD FOR ${player} ${gun}")
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
        item.setItemMeta(itemMeta)
        
        // send player message
        if ( !playerDied ) {
            Message.announcement(player, "${ChatColor.DARK_RED}Reload cancelled...")
        }
        println("CANCELLED RELOAD FOR ${player} ${gun}")
    }
}