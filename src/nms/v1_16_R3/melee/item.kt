/**
 * Melee weapon specific nms item stack adapter functions.
 */

package phonon.xc.nms.melee.item

import org.bukkit.entity.Player
import net.minecraft.server.v1_16_R3.NBTTagCompound
import net.minecraft.server.v1_16_R3.ItemStack as NMSItemStack
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer

import phonon.xc.XC
import phonon.xc.melee.MeleeWeapon
import phonon.xc.nms.item.getCustomItemUnchecked
import phonon.xc.nms.item.getObjectFromNMSItemStack
import phonon.xc.nms.item.BUKKIT_STORAGE_TAG
import phonon.xc.nms.item.NBT_TAG_INT


/**
 * Get a melee weapon from nms item stack using raw NBT tags.
 * This checks if material matches config melee item material.
 */
public fun getMeleeFromNMSItemStack(nmsItem: NMSItemStack): MeleeWeapon? {
    return getObjectFromNMSItemStack(
        nmsItem,
        XC.config.materialMelee,
        XC.melee,
    )
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
        return getCustomItemUnchecked(nmsItem, XC.melee)
    }

    return null
}
