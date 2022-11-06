/**
 * Contains adapter functions to set/get gun properties
 * on minecraft item stacks.
 * 
 * Some helpers:
 * https://www.spigotmc.org/threads/what-are-nbt-tags-and-how-do-you-use-it.500603/
 */

package phonon.xc.nms.gun.item

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
import phonon.xc.gun.Gun
import phonon.xc.nms.item.getCustomItemUnchecked
import phonon.xc.nms.item.GetNMSItemStack
import phonon.xc.nms.item.getObjectFromNMSItemStack
import phonon.xc.nms.item.BUKKIT_STORAGE_TAG
import phonon.xc.nms.item.NBT_TAG_INT


/**
 * Get a gun from nms item stack using raw NBT tags.
 * This checks if material matches config gun material.
 */
public fun getGunFromNMSItemStack(nmsItem: NMSItemStack): Gun? {
    return getObjectFromNMSItemStack(
        nmsItem,
        XC.config.materialGun,
        XC.guns,
    )
}

/**
 * Return gun if player holding a gun in main hand.
 * This uses raw NMS to check item tags.
 */
public fun getGunInHand(player: Player): Gun? {
    val craftPlayer = player as CraftPlayer
    val nmsPlayer = craftPlayer.getHandle()
    val nmsItem = nmsPlayer.inventory.getItemInHand()
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getGunFromNMSItemStack(nmsItem)
    }

    return null
}

/**
 * Return gun player is holding in hand from item's 
 * custom model id, without checking if item material is 
 * the gun material type. Used in situations where code has already
 * checked if material is valid.
 */
public fun getGunInHandUnchecked(player: Player): Gun? {
    val craftPlayer = player as CraftPlayer
    val nmsPlayer = craftPlayer.getHandle()
    val nmsItem = nmsPlayer.inventory.getItemInHand()
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getCustomItemUnchecked(nmsItem, XC.guns)
    }

    return null
}

/**
 * Return gun if player holding a gun in main hand.
 * This uses raw NMS to check item tags.
 */
public fun getGunInSlot(player: Player, slot: Int): Gun? {
    val craftPlayer = player as CraftPlayer
    val nmsPlayer = craftPlayer.getHandle()
    val nmsItem = nmsPlayer.inventory.getItem(slot)
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getGunFromNMSItemStack(nmsItem)
    }

    return null
}

/**
 * Return gun mapped from item's custom model id.
 * Return null if id out of range or if no gun mapped.
 * 
 * GETTING ITEM META IS ONE OF THE SLOWEST + BIGGEST TICK TIMES
 * THIS FUNCTION IS ALSO EXTREMELY COMMON EACH TICK
 * (runs MULTIPLE times per player)
 * SO WE HAVE TO OPTIMIZE THIS AS MUCH AS POSSIBLE WITH RAW NMS
 */
public fun getGunFromItem(item: ItemStack): Gun? {
    if ( item.type == XC.config.materialGun ) {
        try {
            return getGunFromItemNMS(item)
        } catch (err: Exception) {
            XC.logger?.warning("Error in getGunFromItem: $err")
            return getGunFromItemBukkit(item)
        }
    }
    return null
}

internal fun getGunFromItemNMS(item: ItemStack): Gun? {
    val nmsItem = GetNMSItemStack.from(item as CraftItemStack)
    return getGunFromNMSItemStack(nmsItem)
}

/**
 * Safe bukkit method to get gun from item.
 * This is very inefficient because it clones the itemMeta.
 */
internal fun getGunFromItemBukkit(item: ItemStack): Gun? {
    val itemMeta = item.getItemMeta()
    if ( itemMeta != null && itemMeta.hasCustomModelData() ) {
        val modelId = itemMeta.getCustomModelData()
        if ( modelId < XC.MAX_GUN_CUSTOM_MODEL_ID ) {
            return XC.guns[modelId]
        }
    }
    return null
}


/**
 * For item in main player hand,
 * 1. check if material matches input
 * 2. get integer NBT key for input tag
 * 
 * If either fails, return -1
 * 
 * Bukkit PersistentDataContainer keys are stored in a table
 * keyed by "PublicBukkitValues", which is accessible from
 * CraftMetaItem.BUKKIT_STORAGE_TAG.NBT
 * 
 * See:
 * https://hub.spigotmc.org/stash/users/aquazus/repos/craftbukkit/browse/src/main/java/org/bukkit/craftbukkit/inventory/CraftMetaItem.java
 */
public fun checkHandMaterialAndGetNbtIntKey(player: Player, material: Material, key: String): Int {
    val craftPlayer = player as CraftPlayer
    val nmsPlayer = craftPlayer.getHandle()
    val nmsItem = nmsPlayer.inventory.getItemInHand()

    if ( nmsItem != null ) {
        val itemMat = CraftMagicNumbers.getMaterial(nmsItem.getItem())
        if ( itemMat == material ) {
            val tag: NBTTagCompound? = nmsItem.getTag()
            // println("tags = $tag")
            // https://www.spigotmc.org/threads/registering-custom-entities-in-1-14-2.381499/#post-3460944
            if ( tag != null && tag.hasKey(BUKKIT_STORAGE_TAG) ) {
                // persistent data container holder NBTTagCompound
                val pdc = tag.getCompound(BUKKIT_STORAGE_TAG)!!
                if ( pdc.hasKeyOfType(key, NBT_TAG_INT) ) {
                    return pdc.getInt(key)
                }
            }
        }
    }

    return -1
}
