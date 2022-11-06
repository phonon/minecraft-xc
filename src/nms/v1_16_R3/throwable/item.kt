/**
 * Throwable item specific nms item stack adapter functions.
 */

package phonon.xc.nms.throwable.item

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import net.minecraft.server.v1_16_R3.NBTTagCompound
import net.minecraft.server.v1_16_R3.ItemStack as NMSItemStack
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer

import phonon.xc.XC
import phonon.xc.throwable.ThrowableItem
import phonon.xc.nms.item.getCustomItemUnchecked
import phonon.xc.nms.item.GetNMSItemStack
import phonon.xc.nms.item.getObjectFromNMSItemStack
import phonon.xc.nms.item.BUKKIT_STORAGE_TAG
import phonon.xc.nms.item.NBT_TAG_INT


/**
 * Get a throwable from nms item stack using raw NBT tags.
 * This checks if material matches config throwable item material.
 */
public fun getThrowableFromNMSItemStack(nmsItem: NMSItemStack): ThrowableItem? {
    return getObjectFromNMSItemStack(
        nmsItem,
        XC.config.materialThrowable,
        XC.throwable,
    )
}

/**
 * Return throwable if player holding a throwable in main hand.
 * This uses raw NMS to check item tags.
 */
public fun getThrowableInHand(player: Player): ThrowableItem? {
    val craftPlayer = player as CraftPlayer
    val nmsPlayer = craftPlayer.getHandle()
    val nmsItem = nmsPlayer.inventory.getItemInHand()
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getThrowableFromNMSItemStack(nmsItem)
    }

    return null
}

/**
 * Return throwable from player's main hand item, without
 * checking if the material is correct.
 */
public fun getThrowableInHandUnchecked(player: Player): ThrowableItem? {
    val craftPlayer = player as CraftPlayer
    val nmsPlayer = craftPlayer.getHandle()
    val nmsItem = nmsPlayer.inventory.getItemInHand()
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getCustomItemUnchecked(nmsItem, XC.throwable)
    }

    return null
}


/**
 * Return throwable mapped from item's custom model id.
 * Return null if id out of range or if no gun mapped.
 * 
 * GETTING ITEM META IS ONE OF THE SLOWEST + BIGGEST TICK TIMES
 * THIS FUNCTION IS ALSO EXTREMELY COMMON EACH TICK
 * (runs MULTIPLE times per player)
 * SO WE HAVE TO OPTIMIZE THIS AS MUCH AS POSSIBLE WITH RAW NMS
 */
public fun getThrowableFromItem(item: ItemStack): ThrowableItem? {
    if ( item.type == XC.config.materialThrowable ) {
        try {
            return getThrowableFromItemNMS(item)
        } catch (err: Exception) {
            XC.logger?.warning("Error in getThrowableFromItem: $err")
            return getThrowableFromItemBukkit(item) // fallback
        }
    }
    return null
}

internal fun getThrowableFromItemNMS(item: ItemStack): ThrowableItem? {
    val nmsItem = GetNMSItemStack.from(item as CraftItemStack)
    return getThrowableFromNMSItemStack(nmsItem)
}

/**
 * Safe bukkit method to get gun from item.
 * This is very inefficient because it clones the itemMeta.
 */
internal fun getThrowableFromItemBukkit(item: ItemStack): ThrowableItem? {
    val itemMeta = item.getItemMeta()
    if ( itemMeta != null && itemMeta.hasCustomModelData() ) {
        val modelId = itemMeta.getCustomModelData()
        if ( modelId < XC.MAX_THROWABLE_CUSTOM_MODEL_ID ) {
            return XC.throwable[modelId]
        }
    }
    return null
}
