/**
 * Utility to make player crawl for shooting.
 * Packaged with `gun` since this automatically handles gun ads
 * models when starting/stopping crawling.
 * 
 * Based on GSit method:
 * - If block above is air, put a fake barrier above player
 * - Else uses a fake shulker entity above player which forces player into
 * crawling position (since shulker is like a block)
 *   HOWEVER, shulker HEAD will NEVER BE INVISIBLE so looks ugly...
 * https://github.com/Gecolay/GSit/blob/main/v1_18_R2/src/main/java/dev/geco/gsit/mcv/v1_18_R2/objects/GCrawl.java
 * https://github.com/Gecolay/GSit/blob/main/v1_18_R2/src/main/java/dev/geco/gsit/mcv/v1_18_R2/objects/BoxEntity.java
 * 
 * NOTE: instantly kills player if jumping...need to cancel jump damage.
 */

package phonon.xc.gun.crawl

import kotlin.math.floor
import java.util.UUID
import java.util.concurrent.BlockingQueue
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.block.data.BlockData
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionEffect
import org.bukkit.persistence.PersistentDataType
import phonon.xc.XC
import phonon.xc.util.Message
import phonon.xc.gun.useAimDownSights
import phonon.xc.gun.AmmoInfoMessagePacket
import phonon.xc.gun.item.setGunItemMetaModel
import phonon.xc.item.getGunInHand
import phonon.xc.item.getGunFromItem
import phonon.xc.util.progressBar10
// nms version specific imports
import phonon.xc.nms.gun.crawl.BoxEntity


/**
 * Block data for a fake barrier block to place above player to
 * force player into crawl ("swimming") position.
 */
private val FAKE_BLOCK_DATA = Material.BARRIER.createBlockData()

/**
 * Send fake block material packet to player.
 * https://github.com/aadnk/PacketWrapper/blob/master/PacketWrapper/src/main/java/com/comphenix/packetwrapper/WrapperPlayServerBlockChange.java
 */
internal fun Player.sendFakeBlockPacket(x: Int, y: Int, z: Int, blockData: BlockData) {
    this.sendBlockChange(Location(this.getWorld(), x.toDouble(), y.toDouble(), z.toDouble(), 0f, 0f), blockData)
}

/**
 * Crawl position state for a player.
 */
public data class Crawling(
    val tickId: Int,               // tick id this crawling is assigned to
    val player: Player,
    val initialLocation: Location, // initial location
    val prevLocationX: Double,     // location on previous tick
    val prevLocationY: Double,     // location on previous tick
    val prevLocationZ: Double,     // location on previous tick
    val blAboveX: Int,             // block (x, y, z) above player
    val blAboveY: Int,
    val blAboveZ: Int,
    val blAboveMaterial: Material,
    val useBarrierBlock: Boolean,  // whether to use a barrier block
    val boxEntity: BoxEntity?,
) {
    /**
     * Update current crawl state to a new location.
     */
    public fun update(newLocation: Location, forceUpdate: Boolean = false): Crawling {        
        val newBlAboveX = newLocation.getBlockX()
        val newBlAboveY = newLocation.getBlockY() + 1
        val newBlAboveZ = newLocation.getBlockZ()
        
        // if block location changed and previous material was air, cleanup barrier
        if ( this.blAboveMaterial == Material.AIR ) {
            if ( newBlAboveX != blAboveX || newBlAboveY != blAboveY || newBlAboveZ != blAboveZ ) {
                // if material still air, send packet that previous block is air
                val blAbove = player.getWorld().getBlockAt(this.blAboveX, this.blAboveY, this.blAboveZ)
                if ( blAbove.getType() == Material.AIR ) {
                    player.sendFakeBlockPacket(this.blAboveX, this.blAboveY, this.blAboveZ, blAbove.getBlockData())
                }
            }
        }

        val yHeightInBlock = newLocation.getY() - floor(newLocation.getY())
        
        val newBlAboveMaterial = player.getWorld().getBlockAt(newBlAboveX, newBlAboveY, newBlAboveZ).getType()
        
        // only use barrier block if player < 0.5 height in block and block above is air
        val useBarrierBlock = yHeightInBlock < 0.5 && newBlAboveMaterial == Material.AIR

        val boxEntityOrNull = if ( useBarrierBlock ) {
            // block above can be set to a fake barrier
            player.sendFakeBlockPacket(newBlAboveX, newBlAboveY, newBlAboveZ, FAKE_BLOCK_DATA)

            // remove shulker for player
            this.boxEntity?.let { box -> box.sendDestroyPacket(this.player) }

            // return current box entity
            this.boxEntity
        } else {
            val boxEntity = if ( this.boxEntity != null ) {
                // re-use same box entity, with updated location
                boxEntity.moveAboveLocation(newLocation)

                this.boxEntity.sendMovePacket(this.player)
                
                // if y location changed, must also update peek using metadata packet
                if ( newLocation.y != prevLocationY || forceUpdate ) {
                    this.boxEntity.sendPeekMetadataPacket(this.player)
                }

                this.boxEntity
            } else {
                val newBoxEntity = BoxEntity(newLocation)
                newBoxEntity.moveAboveLocation(newLocation)
                newBoxEntity.sendCreatePacket(player)

                newBoxEntity
            }

            boxEntity
        }
    
        return Crawling(
            tickId = this.tickId,
            player = this.player,
            initialLocation = this.initialLocation,
            prevLocationX = newLocation.getX(),
            prevLocationY = newLocation.getY(),
            prevLocationZ = newLocation.getZ(),
            blAboveX = newBlAboveX,
            blAboveY = newBlAboveY,
            blAboveZ = newBlAboveZ,
            blAboveMaterial = newBlAboveMaterial,
            useBarrierBlock = useBarrierBlock,
            boxEntity = boxEntityOrNull,
        )
    }

    public fun refreshBlock() {
        // re-send barrier block to player
        // block above can be set to a fake barrier
        if ( this.useBarrierBlock ) {
            player.sendFakeBlockPacket(this.blAboveX, this.blAboveY, this.blAboveZ, FAKE_BLOCK_DATA)
        }
    }

    public fun cleanup() {
        // if block above is air and material is still air, cleanup barrier
        if ( this.blAboveMaterial == Material.AIR ) {
            val blAbove = player.getWorld().getBlockAt(this.blAboveX, this.blAboveY, this.blAboveZ)
            player.sendFakeBlockPacket(this.blAboveX, this.blAboveY, this.blAboveZ, blAbove.getBlockData())
        }
        // remove box entity for player if it exists
        this.boxEntity?.let { box -> box.sendDestroyPacket(this.player) }
    }
}

// /**
//  * Cancel's player fov change with server client abilities packet?
//  * https://github.com/aadnk/PacketWrapper/blob/master/PacketWrapper/src/main/java/com/comphenix/packetwrapper/WrapperPlayServerAbilities.java
//  */
//  DOES NOT WORK!!!
// internal fun resetFovPacket(player: Player) {
//     val protocolManager = ProtocolLibrary.getProtocolManager()
//     val packet = protocolManager.createPacket(PacketType.Play.Server.ABILITIES)
    
//     // is creative
//     packet.getBooleans().write(0, false)
//     // is flying
//     packet.getBooleans().write(1, false)
//     // is flying allowed
//     packet.getBooleans().write(2, false)
//     // is godmode
//     packet.getBooleans().write(3, false)
//     // flyspeed
//     packet.getFloat().write(0, 0.2f)
//     // walkspeed
//     packet.getFloat().write(1, 0.2f)

//     protocolManager.sendServerPacket(player, packet)
// }


/**
 * Initializes a crawl state for a player 
 */
public fun XC.forceCrawl(player: Player): Crawling {
    val playerLocation = player.getLocation()
    val blAboveX = playerLocation.getBlockX()
    val blAboveY = playerLocation.getBlockY() + 1
    val blAboveZ = playerLocation.getBlockZ()
    
    val yHeightInBlock = playerLocation.getY() - floor(playerLocation.getY())
    
    val blAboveMaterial = player.getWorld().getBlockAt(blAboveX, blAboveY, blAboveZ).getType()

    // only use barrier block if player < 0.5 height in block and block above is air
    val useBarrierBlock = yHeightInBlock < 0.5 && blAboveMaterial == Material.AIR

    val boxEntityOrNull = if ( useBarrierBlock ) {
        // block above can be set to a fake barrier
        player.sendFakeBlockPacket(blAboveX, blAboveY, blAboveZ, FAKE_BLOCK_DATA)

        // null box entity
        null
    } else { // must create a fake shulker entity
        val boxEntity = BoxEntity(playerLocation)
        boxEntity.moveAboveLocation(playerLocation)
        boxEntity.sendCreatePacket(player)

        boxEntity
    }

    return Crawling(
        tickId = this.newCrawlRefreshId(),
        player = player,
        initialLocation = playerLocation,
        prevLocationX = playerLocation.getX(),
        prevLocationY = playerLocation.getY(),
        prevLocationZ = playerLocation.getZ(),
        blAboveX = blAboveX,
        blAboveY = blAboveY,
        blAboveZ = blAboveZ,
        blAboveMaterial = blAboveMaterial,
        useBarrierBlock = useBarrierBlock,
        boxEntity = boxEntityOrNull,
    )
}

/**
 * Request to start crawling.
 */
@JvmInline
public value class CrawlStart(
    val player: Player,
)

/**
 * Request to stop crawling.
 */
@JvmInline
public value class CrawlStop(
    val player: Player,
)

// Slowness effect while crawling. Max potion amplifier should be 255 i think... (ambient = true, particles = false)
private val SLOWNESS_EFFECT: PotionEffect = PotionEffect(PotionEffectType.SLOW, Int.MAX_VALUE, 255, true, false)
// No jump effect: when jump negative, prevents player from jumping (ambient = true, particles = false)
private val NO_JUMP_EFFECT: PotionEffect = PotionEffect(PotionEffectType.JUMP, Int.MAX_VALUE, -128, true, false)

/**
 * Process start crawl requests for players. Returns new queue for next tick.
 */
internal fun XC.startCrawlSystem(
    crawlStartQueue: List<CrawlStart>,
    crawling: HashMap<UUID, Crawling>,
) {
    for ( r in crawlStartQueue ) {
        try {
            val player = r.player

            // make sure we do not double start crawl
            if ( crawling.contains(player.getUniqueId()) ) {
                continue
            }

            // unmount if player inside a vehicle
            if ( player.isInsideVehicle() ) {
                player.leaveVehicle()
            }

            // println("START CRAWL $player")
            // println("CURRENT WALK SPEED: ${player.getWalkSpeed()}")

            // send packet that cancels slowness fov change
            // NOT NEEDED WHEN USING player.setWalkSpeed(0.0f)
            // resetFovPacket(player)

            crawling[player.getUniqueId()] = this.forceCrawl(player)

            // start fake swimming motion
            player.setSwimming(true)

            // NOTE: adding no jump effect causes damage to player when they are forced down
            // there is an event that must detect and cancel this damage.
            // use the player isSwimming() check to proceed with that cancel
            // SLOWNESS_EFFECT.apply(player)
            NO_JUMP_EFFECT.apply(player)
            player.setWalkSpeed(0.0f)
        }
        catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed starting crawl for player ${r.player.getName()}")
        }
    }

    this.crawlStartQueue = ArrayList(4)
}

/**
 * Process stop crawl requests for players. Returns new queue for next tick.
 * 
 * NOTE BUG: when player toggles shift, it will cancel crawl
 * However, if async crawl-to-shoot request task is still running, it could
 * complete after a stop request added to this system. Occurs if player stop
 * crawling immediately as crawl timer is finishing. This would be extremely
 * rare to occur but should be handled at some point.
 */
internal fun XC.stopCrawlSystem(
    crawlStopQueue: List<CrawlStop>,
    crawling: HashMap<UUID, Crawling>,
    crawlRequestTasks: HashMap<UUID, CrawlToShootRequestTask>,
    crawlingAndReadyToShoot: HashMap<UUID, Boolean>,
) {
    for ( r in crawlStopQueue ) {
        try {
            val player = r.player
            val playerId = player.getUniqueId()

            // player.removePotionEffect(PotionEffectType.SLOW)
            player.removePotionEffect(PotionEffectType.JUMP)
            player.setWalkSpeed(0.2f) // default speed

            crawling.remove(playerId)?.cleanup()

            // remove and stop crawl request task
            crawlRequestTasks.remove(playerId)?.let { task ->
                task.cancel()
                // blank the progress bar, schedule on next tick
                // since async task might still be finishing
                Bukkit.getScheduler().runTaskLaterAsynchronously(this.plugin, object: Runnable {
                    override fun run() {
                        Message.announcement(player, "")
                    }
                }, 0L)
            }
            
            // remove flag that player crawling and ready to shoot
            crawlingAndReadyToShoot.remove(playerId)

            // remove aim down sights model
            this.removeAimDownSightsOffhandModel(player)

            // stop fake swimming
            player.setSwimming(false)
        }
        catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed stopping crawl for player ${r.player.getName()}")
        }
    }

    this.crawlStopQueue = ArrayList(4)
}

/**
 * This ticks between 0..1..0..1.. to indicate which
 * tick the crawl refresh system is using.
 * This is done to do a shitty "load balancing" of player
 * crawl refresh tasks, so they are divided between two separate
 * ticks for performance. Each Crawling object is assigned to
 * one of these ticks, and refresh system will only match if
 * tick matches.
 */
private var crawlRefreshTickCounter = 0

/**
 * Update tick for all crawling players.
 * Returns new HashMap with updated crawl state for players.
 */
internal fun XC.crawlRefreshSystem(
    crawling: Map<UUID, Crawling>,
    crawlStopQueue: ArrayList<CrawlStop>,
) {
    val newCrawlingState = HashMap<UUID, Crawling>()
    
    // update crawl refresh tick counter (oscillate between 0,1,0,1,...)
    crawlRefreshTickCounter = (crawlRefreshTickCounter + 1) and 1

    for ( (_playerId, prevCrawlState) in crawling ) {
        try {
            val (
                tickId,
                player,
                initialLocation,
                prevLocationX,
                prevLocationY,
                prevLocationZ,
                blAboveX,
                blAboveY,
                blAboveZ,
                prevBlAboveMaterial,
                _boxEntity,
            ) = prevCrawlState

            
            // not needed, toggle event should cancel stop swimming event
            // player.setSwimming(true)
            
            // if == refresh counter, do full refresh system
            if ( tickId == crawlRefreshTickCounter ) {
                
                // if player location has changed or block above changed, send crawl update packet
                val currLocation = player.getLocation()
                val currBlockAboveMaterial = currLocation.world?.getBlockAt(blAboveX, blAboveY, blAboveZ)?.getType() ?: Material.AIR
                newCrawlingState[player.getUniqueId()] = if (
                    currLocation.x != prevLocationX ||
                    currLocation.y != prevLocationY ||
                    currLocation.z != prevLocationZ ||
                    currBlockAboveMaterial != prevBlAboveMaterial
                ) {
                    // if travelled too far from initial location (e.g. water bucket or falling down)
                    // cancel crawl next tick
                    if ( initialLocation.distance(currLocation) > 1.5 ) {
                        crawlStopQueue.add(CrawlStop(player))
                    }

                    prevCrawlState.update(currLocation)
                } else {
                    // just refresh block
                    prevCrawlState.refreshBlock()

                    prevCrawlState
                }

            } else { // just copy crawl state to next tick
                newCrawlingState[player.getUniqueId()] = prevCrawlState
            }
            

            // if only allowed to crawl while using a crawl required weapon,
            // check if player using a crawl weapon.
            // if not, cancel crawl
            if ( this.config.crawlOnlyAllowedOnCrawlWeapons ) {
                val gun = getGunInHand(player)
                if ( gun == null || gun.crawlRequired == false ) {
                    crawlStopQueue.add(CrawlStop(player))
                }
            }
        }
        catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed refreshing crawl for player ${prevCrawlState.player.getName()}")
        }
    }

    this.crawling = newCrawlingState
}


/**
 * Request to start crawling.
 */
@JvmInline
public value class CrawlToShootRequest(
    val player: Player,
)

/**
 * Crawl finished. Starts crawl system.
 */
@JvmInline
public value class CrawlToShootRequestFinish(
    val player: Player,
)

/**
 * Crawl cancelled (e.g. player moved during request).
 */
@JvmInline
public value class CrawlToShootRequestCancel(
    val player: Player,
)


/**
 * System that queues a "crawl to shoot" request.
 * Return new empty queue for next tick.
 */
internal fun XC.requestCrawlToShootSystem(
    crawlToShootRequestQueue: List<CrawlToShootRequest>,
    crawlStartQueue: ArrayList<CrawlStart>,
    crawlRequestTasks: HashMap<UUID, CrawlToShootRequestTask>,
    playerCrawlRequestFinishQueue: BlockingQueue<CrawlToShootRequestFinish>,
    playerCrawlRequestCancelQueue: BlockingQueue<CrawlToShootRequestCancel>,
    timestamp: Long,
) {
    for ( r in crawlToShootRequestQueue ) {
        try {
            val player = r.player
            val playerId = player.getUniqueId()

            // if request task already exists, skip if "stale" (measure as <5 seconds from previous task)
            val previousTask = crawlRequestTasks[playerId]
            if ( previousTask != null && timestamp < (previousTask.startTimestamp + 2000)  ) {
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
            if ( gun == null || gun.crawlRequired == false ) {
                continue
            }

            var itemMeta = item.getItemMeta()
            val itemData = itemMeta.getPersistentDataContainer()

            // initiate crawling
            crawlStartQueue.add(CrawlStart(player))

            // set crawl to shoot id: this ensures this is same item
            // being used to start crawl and to shoot
            val crawlId = this.newCrawlToShootId()
            itemData.set(this.namespaceKeyItemCrawlToShootId, PersistentDataType.INTEGER, crawlId)
            
            // update item meta with new data
            item.setItemMeta(itemMeta)
            equipment.setItem(inventorySlot, item)

            // launch crawl to shoot preparation task
            val task = CrawlToShootRequestTask(
                player = player,
                crawlId = crawlId,
                finishTime = gun.crawlTimeMillis.toDouble(),
                startTimestamp = timestamp,
                itemGunMaterial = this.config.materialGun,
                itemRequestIdKey = this.namespaceKeyItemCrawlToShootId,
                finishTaskQueue = playerCrawlRequestFinishQueue,
                cancelTaskQueue = playerCrawlRequestCancelQueue,
            )

            // runs every 2 ticks = 100 ms
            task.runTaskTimerAsynchronously(this.plugin, 0L, 1L)
            
            crawlRequestTasks[playerId] = task
        }
        catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed crawling to shoot request for player ${r.player.getName()}")
        }
    }

    this.crawlToShootRequestQueue = ArrayList(4)
}

internal class CrawlToShootRequestTask(
    val player: Player,
    val crawlId: Int,
    val finishTime: Double,
    val startTimestamp: Long,
    val itemGunMaterial: Material,
    val itemRequestIdKey: NamespacedKey,
    val finishTaskQueue: BlockingQueue<CrawlToShootRequestFinish>,
    val cancelTaskQueue: BlockingQueue<CrawlToShootRequestCancel>,
): BukkitRunnable() {
    
    private fun cancelTask() {
        cancelTaskQueue.add(CrawlToShootRequestCancel(player))
        this.cancel()
    }

    override fun run() {
        // check if player log off or died
        if ( !player.isOnline() || player.isDead() ) {
            this.cancelTask()
            return
        }
        
        // check if item swapped
        val itemInHand = player.getInventory().getItemInMainHand()
        if ( itemInHand.getType() != itemGunMaterial ) {
            this.cancelTask()
            Message.announcement(player, "") // clear progress bar
            return
        }
        val itemCurrData = itemInHand.getItemMeta().getPersistentDataContainer()
        val itemCrawlId = itemCurrData.get(itemRequestIdKey, PersistentDataType.INTEGER) ?: -1
        if ( itemCrawlId != crawlId ) {
            this.cancelTask()
            Message.announcement(player, "") // clear progress bar
            return
        }

        val timeElapsedMillis = (System.currentTimeMillis() - startTimestamp).toDouble()
        if ( timeElapsedMillis > finishTime ) {
            // done: add finish task to queue
            finishTaskQueue.add(CrawlToShootRequestFinish(player))
            this.cancel()
        } else {
            val progress = timeElapsedMillis / finishTime
            Message.announcement(player, "${progressBar10(progress)}")
        }
    }
}

/**
 * Finish crawl to shoot requests.
 * Return new empty queue for next tick.
 */
internal fun XC.finishCrawlToShootRequestSystem(
    crawlRequestFinishTasks: List<CrawlToShootRequestFinish>,
    crawling: Map<UUID, Crawling>,
    crawlRequestTasks: HashMap<UUID, CrawlToShootRequestTask>,
    crawlingAndReadyToShoot: HashMap<UUID, Boolean>,
) {
    for ( r in crawlRequestFinishTasks ) {
        try {
            val player = r.player
            val playerId = player.getUniqueId()

            // remove crawl request task
            crawlRequestTasks.remove(playerId)

            // check if player still crawling: if not, skip
            val playerCrawling = crawling[player.getUniqueId()]
            if ( playerCrawling == null ) {
                continue
            }
            // force a crawl update, in 1.18.2, sometimes player moving
            // rapidly near half slab height offsets, player can be forced
            // to crawl, then moved away into non-crawl position.
            playerCrawling.update(player.location, forceUpdate = true)

            // check player still using a crawl weapon
            // TODO: todo properly need to check if crawl id same.
            // but this case is so rare that it's not worth the effort.
            val equipment = player.getInventory()
            val inventorySlot = equipment.getHeldItemSlot()
            val item = equipment.getItem(inventorySlot)
            if ( item == null ) {
                this.crawlStopQueue.add(CrawlStop(player))
                continue
            }

            val gun = getGunFromItem(item)
            if ( gun == null || gun.crawlRequired == false ) {
                crawlStopQueue.add(CrawlStop(player))
                continue
            }

            // set aim down sights model
            var itemMeta = item.getItemMeta()
            val itemData = itemMeta.getPersistentDataContainer()

            // send ammo message
            val ammo = itemData.get(this.namespaceKeyItemAmmo, PersistentDataType.INTEGER) ?: 0
            this.gunAmmoInfoMessageQueue.add(AmmoInfoMessagePacket(player, ammo, gun.ammoMax))

            // if player is aim down sights, add offhand model
            if ( useAimDownSights(player) ) {
                itemMeta = setGunItemMetaModel(itemMeta, gun, ammo, true)
                item.setItemMeta(itemMeta)
                equipment.setItem(inventorySlot, item)

                this.createAimDownSightsOffhandModel(gun, player)
            }

            // mark player ready to shoot
            crawlingAndReadyToShoot[playerId] = true
        }
        catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed to finish crawling request for player ${r.player.getName()}")
        }
    }
}

/**
 * Finish cancelled crawl to shoot requests.
 * Return new empty queue for next tick.
 */
internal fun XC.cancelCrawlToShootRequestSystem(
    crawlRequestCancelTasks: List<CrawlToShootRequestCancel>,
    crawling: Map<UUID, Crawling>,
    crawlRequestTasks: HashMap<UUID, CrawlToShootRequestTask>,
    crawlStopQueue: ArrayList<CrawlStop>,
) {
    for ( r in crawlRequestCancelTasks ) {
        val player = r.player
        val playerId = player.getUniqueId()

        // remove crawl request task
        crawlRequestTasks.remove(playerId)

        // stop crawling
        if ( crawling.contains(player.getUniqueId()) ) {
            crawlStopQueue.add(CrawlStop(player))
        }
        
        // clear crawl to shoot progress bar
        Message.announcement(player, "")
    }
}
