/**
 * Contains adapter functions to set/get hat properties
 * on minecraft item stacks.
 * 
 * Some helpers:
 * https://www.spigotmc.org/threads/what-are-nbt-tags-and-how-do-you-use-it.500603/
 */

package phonon.xc.compatibility.v1_16_R3.throwable.item

import kotlin.math.min
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.persistence.PersistentDataContainer

import net.minecraft.server.v1_16_R3.NBTTagCompound
import net.minecraft.server.v1_16_R3.ItemStack as NMSItemStack
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_16_R3.util.CraftMagicNumbers

import phonon.xc.XC
import phonon.xc.throwable.ThrowableItem
import phonon.xc.compatibility.v1_16_R3.item.getCustomItemUnchecked
import phonon.xc.compatibility.v1_16_R3.item.GetNMSItemStack


// bukkit persistent data container (pdc) key
// pdc is stored in a nested table in item main tags
private const val BUKKIT_CUSTOM_TAG = "PublicBukkitValues"

// nbt tag integers
private const val NBT_TAG_INT = 3

/**
 * Get a throwable from nms item stack using raw NBT tags.
 * This checks if material matches config throwable item material.
 */
public fun getThrowableFromNMSItemStack(nmsItem: NMSItemStack): ThrowableItem? {
    val material = CraftMagicNumbers.getMaterial(nmsItem.getItem())
    if ( material == XC.config.materialThrowable ) {
        val tags: NBTTagCompound? = nmsItem.getTag()
        // println("tags = $tags")
        if ( tags != null && tags.hasKeyOfType("CustomModelData", NBT_TAG_INT) ) {
            // https://www.spigotmc.org/threads/registering-custom-entities-in-1-14-2.381499/#post-3460944
            val modelId = tags.getInt("CustomModelData")
            // println("tags['CustomModelData'] = ${modelId}")
            if ( modelId < XC.MAX_THROWABLE_CUSTOM_MODEL_ID ) {
                return XC.throwable[modelId]
            }
        }
    }

    return null
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
        return getCustomItemUnchecked(nmsItem, XC.throwable, XC.MAX_THROWABLE_CUSTOM_MODEL_ID)
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
