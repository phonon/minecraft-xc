/**
 * Contains functions to set/get gun properties on item stack
 * ItemMeta and PersistentDataContainer.
 * 
 * For NMS functions to get gun from item stack, see nms 
 * specific implementations in `nms/v_1_XX_RY/gun/item.kt`.
 */

package phonon.xc.gun.item

import kotlin.math.min
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.persistence.PersistentDataContainer

import phonon.xc.XC
import phonon.xc.gun.Gun

/**
 * Updates item metadata with gun lore and ammo.
 * Note: this does not update an item itself, this is a sub-function
 * for a client updating a gun item.
 */
public fun XC.setGunItemMetaAmmo(itemMeta: ItemMeta, gun: Gun, ammo: Int): ItemMeta {
    // update ammo data
    val itemData = itemMeta.getPersistentDataContainer()
    itemData.set(this.namespaceKeyItemAmmo, PersistentDataType.INTEGER, ammo)

    // update item description
    itemMeta.setLore(gun.getItemDescriptionForAmmo(ammo))

    return itemMeta
}

/**
 * Updates item metadata with gun lore and ammo.
 * Note: this does not update an item itself, this is a sub-function
 * for a client updating a gun item.
 */
public fun XC.setGunItemMetaAmmoAndModel(
    itemMeta: ItemMeta,
    itemData: PersistentDataContainer,
    gun: Gun,
    ammo: Int,
    useAimDownSights: Boolean,
): ItemMeta {
    // update ammo data
    itemData.set(this.namespaceKeyItemAmmo, PersistentDataType.INTEGER, ammo)

    // update item description
    itemMeta.setLore(gun.getItemDescriptionForAmmo(ammo))

    return setGunItemMetaModel(itemMeta, gun, ammo, useAimDownSights)
}

/**
 * Set gun item meta model based on gun and ammo count.
 * Player used to set model to aim down sights.
 */
public fun XC.setGunItemMetaModel(itemMeta: ItemMeta, gun: Gun, ammo: Int, aimdownsights: Boolean): ItemMeta {
    // gun empty and there is custom empty model
    if ( ammo <= 0 && gun.itemModelEmpty > 0 ) {
        itemMeta.setCustomModelData(gun.itemModelEmpty)
    }
    // else, use regular or aim down sights model
    else {
        if ( gun.itemModelAimDownSights > 0 && aimdownsights )
            itemMeta.setCustomModelData(gun.itemModelAimDownSights)
        else {
            itemMeta.setCustomModelData(gun.itemModelDefault)
        }
    }

    return itemMeta
}

/**
 * Set gun item meta to reload model, if it exists for Gun.
 */
public fun XC.setGunItemMetaReloadModel(itemMeta: ItemMeta, gun: Gun): ItemMeta {
    // gun empty and there is custom empty model
    if ( gun.itemModelReload > 0 ) {
        itemMeta.setCustomModelData(gun.itemModelReload)
    }
    // else, use regular model
    else {
        itemMeta.setCustomModelData(gun.itemModelDefault)
    }

    return itemMeta
}