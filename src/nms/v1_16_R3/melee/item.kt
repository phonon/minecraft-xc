/**
 * Contains adapter functions to set/get hat properties
 * on minecraft item stacks.
 * 
 * Some helpers:
 * https://www.spigotmc.org/threads/what-are-nbt-tags-and-how-do-you-use-it.500603/
 */

package phonon.xc.nms.melee.item

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
import phonon.xc.melee.MeleeWeapon
import phonon.xc.nms.item.getCustomItemUnchecked


// bukkit persistent data container (pdc) key
// pdc is stored in a nested table in item main tags
private const val BUKKIT_CUSTOM_TAG = "PublicBukkitValues"

// nbt tag integers
private const val NBT_TAG_INT = 3

/**
 * Get a melee weapon from nms item stack using raw NBT tags.
 * This checks if material matches config melee item material.
 */
public fun getMeleeFromNMSItemStack(nmsItem: NMSItemStack): MeleeWeapon? {
    val material = CraftMagicNumbers.getMaterial(nmsItem.getItem())
    if ( material == XC.config.materialMelee ) {
        val tags: NBTTagCompound? = nmsItem.getTag()
        // println("tags = $tags")
        if ( tags != null && tags.hasKeyOfType("CustomModelData", NBT_TAG_INT) ) {
            // https://www.spigotmc.org/threads/registering-custom-entities-in-1-14-2.381499/#post-3460944
            val modelId = tags.getInt("CustomModelData")
            // println("tags['CustomModelData'] = ${modelId}")
            if ( modelId < XC.MAX_MELEE_CUSTOM_MODEL_ID ) {
                return XC.melee[modelId]
            }
        }
    }

    return null
}

/**
 * Return throwable if player holding a throwable in main hand.
 * This uses raw NMS to check item tags.
 */
public fun getMeleeInHand(player: Player): MeleeWeapon? {
    val craftPlayer = player as CraftPlayer
    val nmsPlayer = craftPlayer.getHandle()
    val nmsItem = nmsPlayer.inventory.getItemInHand()
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getMeleeFromNMSItemStack(nmsItem)
    }

    return null
}

/**
 * Return melee weapon from player's main hand item, without
 * checking if the material is correct.
 */
public fun getMeleeInHandUnchecked(player: Player): MeleeWeapon? {
    val craftPlayer = player as CraftPlayer
    val nmsPlayer = craftPlayer.getHandle()
    val nmsItem = nmsPlayer.inventory.getItemInHand()
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getCustomItemUnchecked(nmsItem, XC.melee, XC.MAX_MELEE_CUSTOM_MODEL_ID)
    }

    return null
}
