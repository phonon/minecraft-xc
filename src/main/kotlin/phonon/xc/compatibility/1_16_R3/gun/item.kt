/**
 * Contains adapter functions to set/get gun properties
 * on minecraft item stacks.
 * 
 * Some helpers:
 * https://www.spigotmc.org/threads/what-are-nbt-tags-and-how-do-you-use-it.500603/
 */

package phonon.xc.compatibility.v1_16_R3.gun.item

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
import phonon.xc.gun.*

// bukkit persistent data container (pdc) key
// pdc is stored in a nested table in item main tags
private const val BUKKIT_CUSTOM_TAG = "PublicBukkitValues"

// nbt tag integers
private const val NBT_TAG_INT = 3

/**
 * Get a gun from nms item stack using raw NBT tags.
 * This checks if material matches config gun material.
 */
public fun getGunFromNMSItemStack(nmsItem: NMSItemStack): Gun? {
    val material = CraftMagicNumbers.getMaterial(nmsItem.getItem())
    if ( material == XC.config.materialGun ) {
        val tags: NBTTagCompound? = nmsItem.getTag()
        // println("tags = $tags")
        if ( tags != null && tags.hasKeyOfType("CustomModelData", NBT_TAG_INT) ) {
            // https://www.spigotmc.org/threads/registering-custom-entities-in-1-14-2.381499/#post-3460944
            val modelId = tags.getInt("CustomModelData")
            // println("tags['CustomModelData'] = ${modelId}")
            if ( modelId < XC.MAX_GUN_CUSTOM_MODEL_ID ) {
                return XC.guns[modelId]
            }
        }
    }

    return null
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

private object GetNMSItemStack {
    val privField = CraftItemStack::class.java.getDeclaredField("handle")

    init {
        privField.setAccessible(true)
    }

    public fun get(item: CraftItemStack): NMSItemStack {
        return privField.get(item) as NMSItemStack
    }
}

internal fun getGunFromItemNMS(item: ItemStack): Gun? {
    val nmsItem = GetNMSItemStack.get(item as CraftItemStack)
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
 * CraftMetaItem.BUKKIT_CUSTOM_TAG.NBT
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
            if ( tag != null && tag.hasKey(BUKKIT_CUSTOM_TAG) ) {
                // persistent data container holder NBTTagCompound
                val pdc = tag.getCompound(BUKKIT_CUSTOM_TAG)!!
                if ( pdc.hasKeyOfType(key, NBT_TAG_INT) ) {
                    return pdc.getInt(key)
                }
            }
        }
    }

    return -1
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
 * Updates item metadata with gun lore and ammo.
 * Note: this does not update an item itself, this is a sub-function
 * for a client updating a gun item.
 */
public fun setGunItemMetaAmmo(itemMeta: ItemMeta, gun: Gun, ammo: Int): ItemMeta {
    // update ammo data
    val itemData = itemMeta.getPersistentDataContainer()
    itemData.set(XC.namespaceKeyItemAmmo!!, PersistentDataType.INTEGER, ammo)

    // update item description
    itemMeta.setLore(gun.getItemDescriptionForAmmo(ammo))

    return itemMeta
}

/**
 * Updates item metadata with gun lore and ammo.
 * Note: this does not update an item itself, this is a sub-function
 * for a client updating a gun item.
 */
public fun setGunItemMetaAmmoAndModel(
    itemMeta: ItemMeta,
    itemData: PersistentDataContainer,
    gun: Gun,
    ammo: Int,
    useAimDownSights: Boolean,
): ItemMeta {
    // update ammo data
    itemData.set(XC.namespaceKeyItemAmmo!!, PersistentDataType.INTEGER, ammo)

    // update item description
    itemMeta.setLore(gun.getItemDescriptionForAmmo(ammo))

    return setGunItemMetaModel(itemMeta, gun, ammo, useAimDownSights)
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