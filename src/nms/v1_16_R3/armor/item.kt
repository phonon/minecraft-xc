/**
 * Armor specific nms item stack adapter functions.
 */

package phonon.xc.nms.armor.item

import org.bukkit.entity.Player
import net.minecraft.server.v1_16_R3.NBTTagCompound
import net.minecraft.server.v1_16_R3.ItemStack as NMSItemStack
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer

import phonon.xc.XC
import phonon.xc.armor.Hat
import phonon.xc.nms.item.getCustomItemUnchecked
import phonon.xc.nms.item.getObjectFromNMSItemStack


/**
 * Get a hat from nms item stack using raw NBT tags.
 * This checks if material matches config hat material.
 */
public fun getHatFromNMSItemStack(nmsItem: NMSItemStack): Hat? {
    return getObjectFromNMSItemStack(
        nmsItem,
        XC.config.materialArmor,
        XC.hats,
    )
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
        return getCustomItemUnchecked(nmsItem, XC.hats)
    }

    return null
}

