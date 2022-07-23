/**
 * Common NMS item handling methods.
 */

package phonon.xc.compatibility.v1_16_R3.item

import org.bukkit.entity.Player
import net.minecraft.server.v1_16_R3.NBTTagCompound
import net.minecraft.server.v1_16_R3.ItemStack as NMSItemStack
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_16_R3.util.CraftMagicNumbers

import phonon.xc.XC


// bukkit persistent data container (pdc) key
// pdc is stored in a nested table in item main tags
private const val BUKKIT_CUSTOM_TAG = "PublicBukkitValues"

// nbt tag integers
private const val NBT_TAG_INT = 3

/**
 * Return custom item type player is holding in hand.
 */
public fun getItemTypeInHand(player: Player): Int {
    val craftPlayer = player as CraftPlayer
    val nmsPlayer = craftPlayer.getHandle()
    val nmsItem = nmsPlayer.inventory.getItemInHand()
    
    // println("itemInHand: $nmsItem")
    val material = CraftMagicNumbers.getMaterial(nmsItem.getItem())

    return when ( material ) {
        XC.config.materialAmmo -> XC.ITEM_TYPE_AMMO
        XC.config.materialArmor -> XC.ITEM_TYPE_HAT
        XC.config.materialGun -> XC.ITEM_TYPE_GUN
        XC.config.materialMelee -> XC.ITEM_TYPE_MELEE
        XC.config.materialThrowable -> XC.ITEM_TYPE_THROWABLE

        else -> XC.ITEM_TYPE_INVALID
    }
}


/**
 * Get a custom item from index in XC engine storage Array<T>
 * from nms item stack's custom model data as index.
 * Get the custom model id using raw NBT tags.
 * 
 * This does not check if item material is correct. Used in cases
 * where previous code has already verified item material type is
 * correct.
 */
public fun <T> getCustomItemUnchecked(
    nmsItem: NMSItemStack,
    storage: Array<T>,
    maxId: Int,
): T? {
    val tags: NBTTagCompound? = nmsItem.getTag()
    // println("tags = $tags")
    if ( tags != null && tags.hasKeyOfType("CustomModelData", NBT_TAG_INT) ) {
        // https://www.spigotmc.org/threads/registering-custom-entities-in-1-14-2.381499/#post-3460944
        val modelId = tags.getInt("CustomModelData")
        // println("tags['CustomModelData'] = ${modelId}")
        if ( modelId < maxId ) {
            return storage[modelId]
        }
    }

    return null
}
