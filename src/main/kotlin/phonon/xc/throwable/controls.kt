/**
 * Contain all player throwable item (grenade, molotov, etc.)
 * controls systems and helper classes.
 */

package phonon.xc.throwable

import java.util.concurrent.ThreadLocalRandom
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.floor
import kotlin.math.sqrt
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Item as ItemEntity
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.scheduler.BukkitRunnable
import phonon.xc.XC
import phonon.xc.util.Message
import phonon.xc.util.ChunkCoord
import phonon.xc.util.ChunkCoord3D
import phonon.xc.util.Hitbox
// nms version specific imports
import phonon.xc.nms.item.getInventorySlotForCustomItemWithNbtKey
import phonon.xc.nms.item.getItemIntDataIfMaterialMatches
import phonon.xc.nms.throwable.item.*

private val ERROR_MESSAGE_THROWABLE_NOT_READY = "${ChatColor.DARK_RED}First ready throwable with [LEFT MOUSE]"
// private val INFO_MESSAGE_THROWABLE_READY = "${ChatColor.GREEN}Throw using [RIGHT MOUSE]"

/**
 * Data for controls request to ready a throwable.
 */
@JvmInline
internal value class ReadyThrowableRequest(
    val player: Player,
)

/**
 * Data for controls request to throw a throwable.
 */
@JvmInline
internal value class ThrowThrowableRequest(
    val player: Player,
)

/**
 * Data for controls request to throw a throwable.
 */
internal data class DroppedThrowable(
    val player: Player,
    val itemEntity: ItemEntity,
)

/**
 * Data for a throwable that has been readied, but not thrown yet
 * (e.g. still in player's hand).
 */
internal data class ReadyThrowable(
    val throwable: ThrowableItem,
    val id: Int,           // item key id for this throwable
    val ticksElapsed: Int, // current # of ticks passed
    val holder: Player,    // player entity holding this throwable
)

/**
 * Data for throwable that expired. Used to delay running throwable
 * expired handler, since it must be run after hitboxes are
 * calculated.
 */
internal data class ExpiredThrowable(
    val throwable: ThrowableItem, // throwable type
    val location: Location,       // location of entity when throwable expired
    val entity: Entity,           // source entity holding throwable that expired
)

/**
 * Data for a throwable that has been thrown from a player.
 * This is now tracks an item entity in the world.
 */
internal data class ThrownThrowable(
    val throwable: ThrowableItem,
    val id: Int,            // item key id for this throwable
    val ticksElapsed: Int,  // current # of ticks passed
    val itemEntity: ItemEntity, // entity for this throwable
    val thrower: Entity,    // thrower of this throwable (used to track kill source)
    // position: used to project forward to detect block hit
    // NO LONGER NEEDED: velocity for Item entity is accurate, so can project
    // next motion state with just instantaneous pos/vel during system tick.
    // val prevLocX: Double,
    // val prevLocY: Double,
    // val prevLocZ: Double,
)

/**
 * System for handling player requests to ready a throwable
 * in their hand.
 */
internal fun requestReadyThrowableSystem(requests: List<ReadyThrowableRequest>): ArrayList<ReadyThrowableRequest> {
    val playerHandled = HashSet<UUID>() // players ids already handled to avoid redundant requests

    for ( request in requests ) {
        val player = request.player
        val playerId = player.getUniqueId()
        
        if ( playerHandled.add(playerId) == false ) {
            // false if already contained in set
            continue
        }
        
        // Do redundant player main hand is throwable check here
        // since other events could override and change inventory slot or item
        val equipment = player.getInventory()
        val inventorySlot = equipment.getHeldItemSlot()
        val item = equipment.getItem(inventorySlot)
        if ( item == null ) {
            continue
        }

        val throwable = getThrowableFromItem(item)
        if ( throwable == null ) {
            continue
        }

        val itemMeta = item.getItemMeta()
        val itemData = itemMeta.getPersistentDataContainer()

        // check if throwable is already readied, if so continue
        if ( itemData.has(XC.namespaceKeyItemThrowableId!!, PersistentDataType.INTEGER) ) {
            continue
        }

        // get new ready throwable item id
        val throwId = XC.newThrowableId()
        itemData.set(XC.namespaceKeyItemThrowableId!!, PersistentDataType.INTEGER, throwId)
        
        // set item model to ready state
        if ( throwable.itemModelReady > 0 ) {
            itemMeta.setCustomModelData(throwable.itemModelReady)
        }

        item.setItemMeta(itemMeta)
        equipment.setItem(inventorySlot, item)

        // track ready throwable
        // NOTE: throwable ids are used inside the map
        //      XC.readyThrowable[throwId] -> ReadyThrowable
        // It's technically possible for this to overflow and overwrite.
        // But extremely unlikely, since throwables have a lifetime and
        // should be removed from this map before 
        // Integer.MAX_VALUE new throwables are created to overflow and
        // overwrite the key.
        XC.readyThrowables[throwId] = ReadyThrowable(
            throwable = throwable,
            id = throwId,
            ticksElapsed = 0,
            holder = player,
        )

        // seems unnecessary so removed. put it in item lore instead
        // Message.announcement(player, INFO_MESSAGE_THROWABLE_READY)

        // play ready sound
        // playing sound can fail if sound string formatted improperly
        try {
            val world = player.getWorld()
            val location = player.getLocation()
            world.playSound(location, throwable.soundReady, 1f, 1f)
        } catch ( e: Exception ) {
            e.printStackTrace()
            XC.logger?.severe("Failed to play sound: ${throwable.soundReady}")
        }
    }

    return ArrayList()
}


/**
 * System for handling player requests to throw a throwable
 * in their hand.
 */
internal fun requestThrowThrowableSystem(requests: List<ThrowThrowableRequest>): ArrayList<ThrowThrowableRequest> {
    val playerHandled = HashSet<UUID>() // players ids already handled to avoid redundant requests

    for ( request in requests ) {
        val player = request.player
        val playerId = player.getUniqueId()
        
        if ( playerHandled.add(playerId) == false ) {
            // false if already contained in set
            continue
        }

        // Do redundant player main hand is throwable check here
        // since other events could override and change inventory slot or item
        val equipment = player.getInventory()
        val inventorySlot = equipment.getHeldItemSlot()
        val item = equipment.getItem(inventorySlot)
        if ( item == null ) {
            continue
        }

        val throwable = getThrowableFromItem(item)
        if ( throwable == null ) {
            continue
        }

        val itemMeta = item.getItemMeta()
        val itemData = itemMeta.getPersistentDataContainer()

        // check if throwable is readied. if not readied, skip
        if ( !itemData.has(XC.namespaceKeyItemThrowableId!!, PersistentDataType.INTEGER) ) {
            Message.announcement(player, ERROR_MESSAGE_THROWABLE_NOT_READY)
            continue
        }

        val world = player.getWorld()
        val location = player.getEyeLocation()
        val direction = location.getDirection()
        val itemEntity = world.dropItem(location, item)

        itemEntity.setPickupDelay(Integer.MAX_VALUE)
        itemEntity.setVelocity(direction.multiply(throwable.throwSpeed))

        equipment.setItem(inventorySlot, null)
        
        // throw id must exist (since we checked if key exists)
        val throwId = itemData.get(XC.namespaceKeyItemThrowableId!!, PersistentDataType.INTEGER)!!
        // get current ticks elapsed from ready throwable tracking (this should always exist...)
        val ticksElapsed = XC.readyThrowables[throwId]?.ticksElapsed ?: 0
        // remove ready throwable tracking
        XC.readyThrowables.remove(throwId)

        // create thrown throwable tracking
        XC.thrownThrowables[world.getUID()]?.let { throwables ->
            throwables.add(ThrownThrowable(
                throwable = throwable,
                id = throwId,
                ticksElapsed = ticksElapsed,
                itemEntity = itemEntity,
                thrower = player,
                // prevLocX = location.x,
                // prevLocY = location.y,
                // prevLocZ = location.z,
            ))
        }

        // play throw sound
        // playing sound can fail if sound string formatted improperly
        try {
            world.playSound(location, throwable.soundThrow, 1f, 1f)
        } catch ( e: Exception ) {
            e.printStackTrace()
            XC.logger?.severe("Failed to play sound: ${throwable.soundThrow}")
        }
    }

    return ArrayList()
}


/**
 * System for handling player dropping throwable items. If throwable
 * was a readied throwable, must add it to ThrownThrowables.
 */
internal fun droppedThrowableSystem(requests: List<DroppedThrowable>): ArrayList<DroppedThrowable> {
    for ( request in requests ) {
        val (player, itemEntity) = request
        val item = itemEntity.getItemStack()

        // check if item is a readied throwable
        val throwId = getItemIntDataIfMaterialMatches(
            item,
            XC.config.materialThrowable,
            XC.nbtKeyItemThrowableId,
        )
        
        if ( throwId == -1 ) { // skip if not readied throwable
            continue
        }

        val throwable = getThrowableFromItem(item)
        if ( throwable == null ) {
            continue
        }

        val world = itemEntity.getWorld()

        // make item impossible to pick up
        itemEntity.setPickupDelay(Integer.MAX_VALUE)

        // get current ticks elapsed from ready throwable tracking (this should always exist...)
        val ticksElapsed = XC.readyThrowables[throwId]?.ticksElapsed ?: 0
        // remove ready throwable tracking
        XC.readyThrowables.remove(throwId)

        // create thrown throwable tracking
        XC.thrownThrowables[world.getUID()]?.let { throwables ->
            throwables.add(ThrownThrowable(
                throwable = throwable,
                id = throwId,
                ticksElapsed = ticksElapsed,
                itemEntity = itemEntity,
                thrower = player,
                // prevLocX = location.x,
                // prevLocY = location.y,
                // prevLocZ = location.z,
            ))
        }
    }

    return ArrayList()
}


/**
 * Each tick update loop for throwables that are ready but still
 * in a player's inventory waiting to be thrown.
 * This system used to make throwables explode in players hand if
 * throwable lifetime is reached.
 * 
 * Returns new list of ready throwables that need to be ticked.
 */
internal fun tickReadyThrowableSystem(requests: Map<Int, ReadyThrowable>): HashMap<Int, ReadyThrowable> {
    val newThrowables = HashMap<Int, ReadyThrowable>()

    for ( th in requests.values ) {
        // unpack
        val (
            throwable,
            throwId,
            ticksElapsed,
            holder,
        ) = th

        // check if past lifetime: if so, remove and do timer expired handler
        if ( ticksElapsed >= throwable.timeToExplode ) {            
            // remove from player inventory
            val slot = getInventorySlotForCustomItemWithNbtKey(
                holder,
                XC.config.materialThrowable,
                XC.nbtKeyItemThrowableId,
                throwId,
            )
            if ( slot != -1 ) {
                val equipment = holder.getInventory()
                equipment.setItem(slot, null)
            }

            // damage holder
            if ( throwable.damageHolderOnTimerExpired > 0.0 ) {
                holder.damage(throwable.damageHolderOnTimerExpired, null)
                holder.setNoDamageTicks(0)
            }

            // queue expired throwable handler
            XC.expiredThrowables[holder.getWorld().getUID()]?.add(ExpiredThrowable(
                throwable = throwable,
                location = holder.location,
                entity = holder,
            ))

            continue
        }
        else {
            // else, push into new throwables map
            newThrowables[throwId] = ReadyThrowable(
                throwable = throwable,
                id = throwId,
                ticksElapsed = ticksElapsed + 1,
                holder = holder,
            )
        }

    }

    return newThrowables
}


/**
 * System for ticking throwable after it has been thrown.
 * Handle impact checking, lifetime, and explosions.
 * 
 * Returns new list of thrown throwables that need to be ticked.
 */
internal fun handleExpiredThrowableSystem(
    requests: List<ExpiredThrowable>,
    hitboxes: HashMap<ChunkCoord3D, ArrayList<Hitbox>>,
): ArrayList<ExpiredThrowable> {
    for ( th in requests ) {
        val (
            throwable,
            location,
            entity,
        ) = th

        // queue timer expired handler
        throwable.onTimerExpiredHandler(
            hitboxes,
            throwable,
            location,
            entity,
        )
    }

    return ArrayList()
}

/**
 * System for gathering all visited chunks for thrown throwables.
 * This is needed for throwable entity hit detection and explosions
 */
internal fun getThrownThrowableVisitedChunksSystem(
    requests: List<ThrownThrowable>,
):  LinkedHashSet<ChunkCoord> {
    val visitedChunks = LinkedHashSet<ChunkCoord>()

    for ( th in requests ) {
        // unpack
        val itemEntity = th.itemEntity
        val world = itemEntity.getWorld()
        val location = itemEntity.location

        // get item entity's mineman chunk coords (divides by 16)
        val cx0 = floor(location.x).toInt() shr 4
        val cz0 = floor(location.z).toInt() shr 4
        
        // add 1 chunk margin in case hitboxes spans multiple chunks
        val cxmin = cx0 - 1
        val czmin = cz0 - 1
        val cxmax = cx0 + 1
        val czmax = cz0 + 1
        
        for ( cx in cxmin..cxmax ) {
            for ( cz in czmin..czmax ) {
                // only add if chunk loaded
                if ( world.isChunkLoaded(cx, cz) ) {
                    visitedChunks.add(ChunkCoord(cx, cz))
                }
            }
        }
    }
    
    return visitedChunks
}

/**
 * System for ticking throwable after it has been thrown.
 * Handle impact checking, lifetime, and explosions.
 * 
 * Returns new list of thrown throwables that need to be ticked.
 */
internal fun tickThrownThrowableSystem(
    requests: List<ThrownThrowable>,
    hitboxes: HashMap<ChunkCoord3D, ArrayList<Hitbox>>,
): ArrayList<ThrownThrowable> {
    val newThrowables = ArrayList<ThrownThrowable>()

    for ( th in requests ) {
        // unpack
        val (
            throwable,
            throwId,
            ticksElapsed,
            itemEntity,
            thrower,
            // prevLocX,
            // prevLocY,
            // prevLocZ,
        ) = th

        val currLocation = itemEntity.getLocation()

        // check if past lifetime: if so, remove and do timer expired handler
        if ( ticksElapsed >= throwable.timeToExplode || !itemEntity.isValid() ) {
            itemEntity.remove()

            // run handler
            throwable.onTimerExpiredHandler(
                hitboxes,
                throwable,
                currLocation,
                thrower,
            )

            continue
        }
        else {

            // do block hit handling: projects forward to next tick position
            // and determines if that is inside a solid block.
            // do this first to help prevent false detection for hitting entities
            // behind partially empty blocks
            if ( throwable.hasBlockHitHandler ) {
                val world = itemEntity.getWorld()
                val currLocX = currLocation.x
                val currLocY = currLocation.y
                val currLocZ = currLocation.z

                // not needed: unlike Player entity, getVelocity() is accurate here
                // val currVelX = (currLocX - prevLocX)
                // val currVelY = (currLocY - prevLocY)
                // val currVelZ = (currLocZ - prevLocZ)

                val itemEntityVel = itemEntity.getVelocity()

                val velX = itemEntityVel.x
                val velY = itemEntityVel.y - 0.04
                val velZ = itemEntityVel.z

                val nextLocX = currLocX + velX
                val nextLocY = currLocY + velY // gravity
                val nextLocZ = currLocZ + velZ

                // for debugging: needed to verify currLoc and projected nextLoc are same
                // println("currLoc = ($currLocX, $currLocY, $currLocZ)")
                // println("itemEntityVel = (${itemEntityVel.x}, ${itemEntityVel.y}, ${itemEntityVel.z})")
                // println("currVel = (${currVelX}, ${currVelY}, ${currVelZ})")
                // println("nextLoc = ($nextLocX, $nextLocY, $nextLocZ)")

                val nextBlx = floor(nextLocX).toInt()
                val nextBly = floor(nextLocY).toInt()
                val nextBlz = floor(nextLocZ).toInt()

                val bl = world.getBlockAt(nextBlx, nextBly, nextBlz)
                
                if ( bl.type != Material.AIR ) {
                    val distance = sqrt(velX * velX + velY * velY + velZ * velZ)
                    val dirX = velX / distance
                    val dirY = velY / distance
                    val dirZ = velZ / distance

                    // check if movement path intersects into solid block
                    val hitDistance = XC.config.blockCollision[bl.type](
                        bl,
                        currLocX.toFloat(),
                        currLocY.toFloat(),
                        currLocZ.toFloat(),
                        dirX.toFloat(),
                        dirY.toFloat(),
                        dirZ.toFloat(),
                        distance.toFloat(),
                    )

                    if ( hitDistance != Float.MAX_VALUE ) { // hit found
                        val hitDist = hitDistance.toDouble()
                        val hitBlock = bl
                        val hitBlockLocation = Location(
                            world,
                            currLocX + hitDist * dirX,
                            currLocY + hitDist * dirY,
                            currLocZ + hitDist * dirZ,
                        )
                        
                        itemEntity.remove()

                        throwable.onBlockHitHandler(
                            hitboxes,
                            throwable,
                            hitBlockLocation,
                            hitBlock,
                            thrower,
                        )

                        continue
                    }
                }
            }

            // entity hit handling:
            // detect if current location is inside an entity
            if ( throwable.hasEntityHitHandler ) {
                val currLocX = currLocation.x
                val currLocY = currLocation.y
                val currLocZ = currLocation.z

                // get chunk
                val cx = floor(currLocX).toInt() shr 4
                val cy = floor(currLocY).toInt() shr 4
                val cz = floor(currLocZ).toInt() shr 4

                hitboxes[ChunkCoord3D(cx, cy, cz)]?.let { hbs ->
                    for ( hitbox in hbs ) {
                        // skip if hitbox is thrower
                        if ( hitbox.entity === thrower ) {
                            continue
                        }

                        if ( hitbox.contains(currLocX.toFloat(), currLocY.toFloat(), currLocZ.toFloat()) ) {
                            itemEntity.remove()

                            throwable.onEntityHitHandler(
                                hitboxes,
                                throwable,
                                currLocation,
                                hitbox.entity,
                                thrower,
                            )

                            continue
                        }
                    }
                }
            }

            
            // else, push into new throwables map
            newThrowables.add(ThrownThrowable(
                throwable = throwable,
                id = throwId,
                ticksElapsed = ticksElapsed + 1,
                itemEntity = itemEntity,
                thrower = thrower,
                // prevLocX = currLocX,
                // prevLocY = currLocY,
                // prevLocZ = currLocZ,
            ))
        }
    }

    return newThrowables
}
