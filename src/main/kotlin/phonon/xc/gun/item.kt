/**
 * Contains adapter functions to set/get gun properties
 * on minecraft item stacks. 
 */

package phonon.xc.gun

import kotlin.math.min
import org.bukkit.ChatColor
import org.bukkit.inventory.ItemStack
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
    val dataContainer = itemMeta.getPersistentDataContainer()
    dataContainer.set(XC.namespaceKeyItemAmmo!!, PersistentDataType.INTEGER, min(ammoCount, gun.ammoMax))
    
    // begin item description with ammo count
    val itemDescription: ArrayList<String> = arrayListOf("${ChatColor.GRAY}Ammo: ${ammoCount}/${gun.ammoMax}")
    // append lore
    gun.itemLore?.let { lore -> itemDescription.addAll(lore) }
    itemMeta.setLore(itemDescription.toList())

    item.setItemMeta(itemMeta)

    return item
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