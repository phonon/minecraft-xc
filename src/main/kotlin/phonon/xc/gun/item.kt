/**
 * Contains adapter functions to set/get gun properties
 * on minecraft item stacks. 
 */

package phonon.xc.gun

import kotlin.math.min
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import phonon.xc.XC


/**
 * Return gun mapped from item's custom model id.
 * Return null if id out of range or if no gun mapped.
 */
public fun getGunFromItem(item: ItemStack): Gun? {
    if ( item.type == XC.config.materialGun ) {
        val itemMeta = item.getItemMeta()
        if ( itemMeta != null && itemMeta.hasCustomModelData() ) {
            val modelId = itemMeta.getCustomModelData()
            if ( modelId < XC.MAX_GUN_CUSTOM_MODEL_ID ) {
                return XC.guns[modelId]
            }
        }
    }
    return null
}


/**
 * Create a new ItemStack from gun properties.
 */
public fun createItemFromGun(
    gun: Gun,
    ammo: Int = Int.MAX_VALUE,
): ItemStack {
    val item = ItemStack(XC.config.materialGun, 1)
    val itemMeta = item.getItemMeta()
    
    // name
    itemMeta.setDisplayName("${ChatColor.RESET}${gun.itemName}")
    
    // model
    itemMeta.setCustomModelData(gun.itemModelDefault)

    // ammo (IMPORTANT: actual ammo count used for shooting/reload logic)
    val ammoCount = min(ammo, gun.ammoMax)
    val itemData = itemMeta.getPersistentDataContainer()
    itemData.set(XC.namespaceKeyItemAmmo!!, PersistentDataType.INTEGER, min(ammoCount, gun.ammoMax))
    
    // begin item description with ammo count
    val itemDescription: ArrayList<String> = arrayListOf("${ChatColor.GRAY}Ammo: ${ammoCount}/${gun.ammoMax}")
    // append lore
    gun.itemLore?.let { lore -> itemDescription.addAll(lore) }
    itemMeta.setLore(itemDescription.toList())

    item.setItemMeta(itemMeta)

    return item
}


/**
 * Re-creates gun item's text description using input ammo amount.
 * This does not do any checks if item is actually the gun
 * or if the ammo is in proper range. Client does these checks. 
 */
public fun updateGunItemAmmo(item: ItemStack, gun: Gun, ammo: Int) {
    val itemMeta = item.getItemMeta()

    // update ammo data
    val itemData = itemMeta.getPersistentDataContainer()
    itemData.set(XC.namespaceKeyItemAmmo!!, PersistentDataType.INTEGER, ammo)

    // update description
    val itemDescription: ArrayList<String> = arrayListOf("${ChatColor.GRAY}Ammo: ${ammo}/${gun.ammoMax}")
    // append lore
    gun.itemLore?.let { lore -> itemDescription.addAll(lore) }
    itemMeta.setLore(itemDescription.toList())

    item.setItemMeta(itemMeta)
}

/**
 * Updates item metadata with gun lore and ammo.
 * Note: this does not update an item itself, this is a sub-function
 * for a client updating a gun item.
 */
public fun setGunItemMetaAmmo(itemMeta: ItemMeta, gun: Gun, ammo: Int): ItemMeta {
    // update ammo data
    val itemData = itemMeta.getPersistentDataContainer()
    itemData.set(XC.namespaceKeyItemAmmo!!, PersistentDataType.INTEGER, ammo)

    // update description
    val itemDescription: ArrayList<String> = arrayListOf("${ChatColor.GRAY}Ammo: ${ammo}/${gun.ammoMax}")
    // append lore
    gun.itemLore?.let { lore -> itemDescription.addAll(lore) }
    itemMeta.setLore(itemDescription.toList())

    return itemMeta
}

/**
 * Return ammo value stored in an item stack's persistent data.
 * Does not check if item is a valid gun, only that the item metadata
 * contains a key for the ammo value.
 */
public fun getAmmoFromItem(item: ItemStack): Int? {
    val itemMeta = item.getItemMeta()
    val dataContainer = itemMeta.getPersistentDataContainer()
    return dataContainer.get(XC.namespaceKeyItemAmmo!!, PersistentDataType.INTEGER)
}


/**
 * Set gun item model based on gun and ammo count.
 * Just a wrapper around `setGunItemMetaModel`.
 */
public fun setGunItemStackModel(item: ItemStack, gun: Gun, ammo: Int, aimdownsights: Boolean): ItemStack {
    val itemMeta = item.getItemMeta()
    item.setItemMeta(setGunItemMetaModel(itemMeta, gun, ammo, aimdownsights))

    return item
}

/**
 * Set gun item meta model based on gun and ammo count.
 * Player used to set model to aim down sights.
 */
public fun setGunItemMetaModel(itemMeta: ItemMeta, gun: Gun, ammo: Int, aimdownsights: Boolean): ItemMeta {
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
public fun setGunItemMetaReloadModel(itemMeta: ItemMeta, gun: Gun): ItemMeta {
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