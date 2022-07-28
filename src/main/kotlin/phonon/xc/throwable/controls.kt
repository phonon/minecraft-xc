/**
 * Contain all player throwable item (grenade, molotov, etc.)
 * controls systems and helper classes.
 */

package phonon.xc.throwable

import java.util.concurrent.ThreadLocalRandom
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
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
import phonon.xc.utils.Message

import phonon.xc.compatibility.v1_16_R3.item.getInventorySlotForCustomItemWithNbtKey
import phonon.xc.compatibility.v1_16_R3.item.getItemIntDataIfMaterialMatches
import phonon.xc.compatibility.v1_16_R3.throwable.item.*

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
 * Data for a throwable that has been thrown from a player.
 * This is now tracks an item entity in the world.
 */
internal data class ThrownThrowable(
    val throwable: ThrowableItem,
    val id: Int,            // item key id for this throwable
    val ticksElapsed: Int,  // current # of ticks passed
    val entity: ItemEntity, // entity for this throwable
    val thrower: Entity,    // thrower of this throwable (used to track kill source)
    // position + velocity: used to project forward to detect block hit
    val prevLocX: Double,
    val prevLocY: Double,
    val prevLocZ: Double,
    val velX: Double, // current velocity in X direction
    val velY: Double, // current velocity in Y direction
    val velZ: Double, // current velocity in Z direction
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
                entity = itemEntity,
                thrower = player,
                prevLocX = location.x,
                prevLocY = location.y,
                prevLocZ = location.z,
                velX = 0.0,
                velY = 0.0,
                velZ = 0.0,
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
        val location = itemEntity.getLocation()

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
                entity = itemEntity,
                thrower = player,
                prevLocX = location.x,
                prevLocY = location.y,
                prevLocZ = location.z,
                velX = 0.0,
                velY = 0.0,
                velZ = 0.0,
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
            // timerExpiredHandler(holder, throwId)
            println("READY THROWABLE EXPIRED")
            
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
internal fun tickThrownThrowableSystem(requests: List<ThrownThrowable>): ArrayList<ThrownThrowable> {
    val newThrowables = ArrayList<ThrownThrowable>()

    for ( th in requests ) {
        // unpack
        val (
            throwable,
            throwId,
            ticksElapsed,
            entity,
            thrower,
            prevLocX,
            prevLocY,
            prevLocZ,
            velX,
            velY,
            velZ,
        ) = th

        val currLocation = entity.getLocation()
        val currLocX = currLocation.x
        val currLocY = currLocation.y
        val currLocZ = currLocation.z
        val currVelX = (currLocX - prevLocX)
        val currVelY = (currLocY - prevLocY)
        val currVelZ = (currLocZ - prevLocZ)

        // check if past lifetime: if so, remove and do timer expired handler
        if ( ticksElapsed >= throwable.timeToExplode || !entity.isValid() ) {
            // timerExpiredHandler(holder, throwId)
            println("THROWN THROWABLE EXPIRED")
            entity.remove()

            continue
        }
        else {
            // else, push into new throwables map
            newThrowables.add(ThrownThrowable(
                throwable = throwable,
                id = throwId,
                ticksElapsed = ticksElapsed + 1,
                entity = entity,
                thrower = thrower,
                prevLocX = currLocX,
                prevLocY = currLocY,
                prevLocZ = currLocZ,
                velX = currVelX,
                velY = currVelY,
                velZ = currVelZ,
            ))
        }
    }

    return newThrowables
}
