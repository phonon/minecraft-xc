/**
 * Contains adapter functions to set/get hat properties
 * on minecraft item stacks.
 * 
 * Some helpers:
 * https://www.spigotmc.org/threads/what-are-nbt-tags-and-how-do-you-use-it.500603/
 */

package phonon.xc.compatibility.v1_16_R3.armor.item

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
import phonon.xc.armor.*
import phonon.xc.compatibility.v1_16_R3.item.getCustomItemUnchecked


// bukkit persistent data container (pdc) key
// pdc is stored in a nested table in item main tags
private const val BUKKIT_CUSTOM_TAG = "PublicBukkitValues"

// nbt tag integers
private const val NBT_TAG_INT = 3

/**
 * Get a hat from nms item stack using raw NBT tags.
 * This checks if material matches config hat material.
 */
public fun getHatFromNMSItemStack(nmsItem: NMSItemStack): Hat? {
    val material = CraftMagicNumbers.getMaterial(nmsItem.getItem())
    if ( material == XC.config.materialArmor ) {
        val tags: NBTTagCompound? = nmsItem.getTag()
        // println("tags = $tags")
        if ( tags != null && tags.hasKeyOfType("CustomModelData", NBT_TAG_INT) ) {
            // https://www.spigotmc.org/threads/registering-custom-entities-in-1-14-2.381499/#post-3460944
            val modelId = tags.getInt("CustomModelData")
            // println("tags['CustomModelData'] = ${modelId}")
            if ( modelId < XC.MAX_HAT_CUSTOM_MODEL_ID ) {
                return XC.hats[modelId]
            }
        }
    }

    return null
}

/**
 * Return hat if player holding a hat in main hand.
 * This uses raw NMS to check item tags.
 */
public fun getHatInHand(player: Player): Hat? {
    val craftPlayer = player as CraftPlayer
    val nmsPlayer = craftPlayer.getHandle()
    val nmsItem = nmsPlayer.inventory.getItemInHand()
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getHatFromNMSItemStack(nmsItem)
    }

    return null
}

/**
 * Return hat from player's main hand item, without
 * checking if the material is correct.
 */
public fun getHatInHandUnchecked(player: Player): Hat? {
    val craftPlayer = player as CraftPlayer
    val nmsPlayer = craftPlayer.getHandle()
    val nmsItem = nmsPlayer.inventory.getItemInHand()
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getCustomItemUnchecked(nmsItem, XC.hats, XC.MAX_HAT_CUSTOM_MODEL_ID)
    }

    return null
}

