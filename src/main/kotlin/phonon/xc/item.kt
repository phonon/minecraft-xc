/**
 * Plugin data storage and item stack management for custom item types
 * (guns, ammo, hats, etc.). Contains main storage container and 
 * extension functions on XC to get custom items from the storage or
 * Bukkit item stacks.
 * 
 * Some helpers:
 * https://www.spigotmc.org/threads/what-are-nbt-tags-and-how-do-you-use-it.500603/
 */
package phonon.xc.item

import java.util.EnumMap
import kotlin.math.min
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.persistence.PersistentDataContainer
import phonon.xc.nms.NmsNBTTagCompound
import phonon.xc.nms.NmsNBTTagList
import phonon.xc.nms.NBTTagString
import phonon.xc.nms.NBTTagInt
import phonon.xc.nms.putTag
import phonon.xc.nms.containsKey
import phonon.xc.nms.containsKeyOfType
import phonon.xc.nms.NmsItemStack
import phonon.xc.nms.CraftItemStack
import phonon.xc.nms.CraftPlayer
import phonon.xc.nms.CraftMagicNumbers
import phonon.xc.nms.getMainHandNMSItem
import phonon.xc.XC
import phonon.xc.ammo.Ammo
import phonon.xc.armor.Hat
import phonon.xc.gun.Gun
import phonon.xc.landmine.Landmine
import phonon.xc.melee.MeleeWeapon
import phonon.xc.throwable.ThrowableItem

/**
 * Bukkit persistent data container (pdc) key.
 * PDC is stored in a nested table in item's main NBT tags.
 * https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/browse/src/main/java/org/bukkit/craftbukkit/inventory/CraftMetaItem.java#264
 */
internal const val BUKKIT_STORAGE_TAG = "PublicBukkitValues"

/**
 * NBT tag type for integers.
 */
internal const val NBT_TAG_INT = 3

/**
 * Return custom item type player is holding in hand.
 * This is a helper function to map from item config materials
 * to pre-defined constants for item types, used in event listener
 * to map from player item in hand to custom item type. Very slightly
 * slower due to the 2nd translation from material to custom item type
 * ...but makes code that needs to match custom item types a little
 * cleaner by avoiding matching on "XC.this.config.materialGun" directly.
 */
public fun XC.getItemTypeInHand(player: Player): Int {
    val craftPlayer = player as CraftPlayer
    val nmsItem = craftPlayer.getMainHandNMSItem()

    // note: nms item is never null, if hand empty this gives air material
    // println("getItemTypeInHand -> itemInHand: $nmsItem")
    
    // internally uses IntArray lookup table using material enum ordinal
    val material = CraftMagicNumbers.getMaterial(nmsItem.getItem())
    return this.config.materialToCustomItemType[material]
}

/**
 * Return custom item id if item in hand matches the config material
 * for item type. This is basically a helper to get custom model data from item.
 * 
 * `itemType` must be one of the hardcoded integer item type constants
 * in XC like `XC.ITEM_TYPE_GUN` or `XC.ITEM_TYPE_AMMO`.
 * 
 * Returns -1 if item in hand is not of the correct material type or
 * if there is no custom model data.
 */
public fun XC.getCustomItemIdInHand(player: Player, itemType: Int): Int {
    val craftPlayer = player as CraftPlayer
    val nmsItem = craftPlayer.getMainHandNMSItem()

    // note: nms item is never null, if hand empty this gives air material
    // println("getItemTypeInHand -> itemInHand: $nmsItem")
    
    // internally uses IntArray lookup table using material enum ordinal
    val material = CraftMagicNumbers.getMaterial(nmsItem.getItem())
    val itemTypeInHand = this.config.materialToCustomItemType[material]
    if ( itemTypeInHand != itemType ) {
        return -1
    }

    val tags: NmsNBTTagCompound? = nmsItem.getTag()
    // println("tags = $tags")
    // NOTE: must check first before getting tag
    if ( tags != null && tags.containsKeyOfType("CustomModelData", NBT_TAG_INT) ) {
        // https://www.spigotmc.org/threads/registering-custom-entities-in-1-14-2.381499/#post-3460944
        val modelId = tags.getInt("CustomModelData")
        // println("tags['CustomModelData'] = ${modelId}")
        return modelId
    }

    return -1
}

/**
 * Get custom item type from nms item stack using raw NBT tags.
 * Checks if material matches, then uses its custom model id to
 * index into custom type storage array. Return null if material
 * does not match or if id is past the storage array size.
 */
public fun <T> getObjectFromNmsItemStack(
    nmsItem: NmsItemStack,
    materialType: Material,
    storage: Array<T>,
): T? {
    val material = CraftMagicNumbers.getMaterial(nmsItem.getItem())
    if ( material == materialType ) {
        val tags: NmsNBTTagCompound? = nmsItem.getTag()
        // println("tags = $tags")
        // NOTE: must check first before getting tag
        if ( tags != null && tags.containsKeyOfType("CustomModelData", NBT_TAG_INT) ) {
            // https://www.spigotmc.org/threads/registering-custom-entities-in-1-14-2.381499/#post-3460944
            val modelId = tags.getInt("CustomModelData")
            // println("tags['CustomModelData'] = ${modelId}")
            if ( modelId < storage.size ) {
                return storage[modelId]
            }
        }
    }

    return null
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
    nmsItem: NmsItemStack,
    storage: Array<T>,
): T? {
    val tags: NmsNBTTagCompound? = nmsItem.getTag()
    // println("tags = $tags")
    if ( tags != null && tags.containsKeyOfType("CustomModelData", NBT_TAG_INT) ) {
        // https://www.spigotmc.org/threads/registering-custom-entities-in-1-14-2.381499/#post-3460944
        val modelId = tags.getInt("CustomModelData")
        // println("tags['CustomModelData'] = ${modelId}")
        if ( modelId < storage.size ) {
            return storage[modelId]
        }
    }

    return null
}

/**
 * Internal helper to get NMS item stack from a bukkit CraftItemStack.
 * Requires reflection to access private NMS item stack handle.
 */
internal object GetNmsItemStack {
    val privField = CraftItemStack::class.java.getDeclaredField("handle")

    init {
        privField.setAccessible(true)
    }

    public fun from(item: CraftItemStack): NmsItemStack {
        return privField.get(item) as NmsItemStack
    }
}

/**
 * 
 * For a bukkit ItemStack
 * 1. Check if material matches input
 * 2. get integer NBT key for input tag
 * 
 * If either fails, return -1
 * 
 * Bukkit PersistentDataContainer keys are stored in a table
 * keyed by "PublicBukkitValues", which is accessible from
 * CraftMetaItem.BUKKIT_STORAGE_TAG.NBT
 * 
 * NOTE: in some cases the cast "item as CraftItemStack" is unsafe...
 * If item is not a CraftItemStack.
 * This can occur in events that create a purely bukkit ItemStack.
 * When interacting with player inventory, they are all implementations
 * of CraftItemStack.
 * But for safety...may need to fix this cast...
 * 
 * See:
 * https://hub.spigotmc.org/stash/users/aquazus/repos/craftbukkit/browse/src/main/java/org/bukkit/craftbukkit/inventory/CraftMetaItem.java
 */
public fun XC.getItemIntDataIfMaterialMatches(
    item: ItemStack,
    material: Material,
    key: String,
): Int {
    if ( item.type == material ) {
        try {
            val nmsItem = GetNmsItemStack.from(item as CraftItemStack)
            if ( nmsItem != null ) {
                val tag: NmsNBTTagCompound? = nmsItem.getTag()
                // println("tags = $tag")
                // https://www.spigotmc.org/threads/registering-custom-entities-in-1-14-2.381499/#post-3460944
                if ( tag != null && tag.containsKey(BUKKIT_STORAGE_TAG) ) {
                    // persistent data container holder NmsNBTTagCompound
                    val pdc = tag.getCompound(BUKKIT_STORAGE_TAG)!!
                    if ( pdc.containsKeyOfType(key, NBT_TAG_INT) ) {
                        return pdc.getInt(key)
                    }
                }
            }
        } catch ( err: Exception ) {
            err.printStackTrace()
            this.logger.severe("Failed to get item NBT key: $err")
        }
    }

    return -1
}

/**
 * Internal helper to find player inventory slot for a custom item
 * with matching material and matching integer NBT key.
 * Return -1 if item not found in inventory
 * 
 * Use case:
 * - For throwables, when they expire in player's inventory, controls
 * system must search the inventory for the item's slot, then remove that item.
 * The item must match material and nbt key.
 */
internal fun getInventorySlotForCustomItemWithNbtKey(
    player: Player,
    material: Material,
    nbtKey: String,
    value: Int,
): Int {
    val craftPlayer = player as CraftPlayer
    val nmsPlayer = craftPlayer.getHandle()
    val nmsInventory = nmsPlayer.inventory
    
    // remove first item found matching
    val items = nmsInventory.getContents()
    for ( slot in 0 until items.size ) {
        val nmsItem = items[slot]
        if ( nmsItem != null && CraftMagicNumbers.getMaterial(nmsItem.getItem()) == material ) {
            // check for nbt key
            val tag: NmsNBTTagCompound? = nmsItem.getTag()
            // println("tags = $tag")
            // https://www.spigotmc.org/threads/registering-custom-entities-in-1-14-2.381499/#post-3460944
            if ( tag != null && tag.containsKey(BUKKIT_STORAGE_TAG) ) {
                // persistent data container holder NmsNBTTagCompound
                val pdc = tag.getCompound(BUKKIT_STORAGE_TAG)!!
                if ( pdc.containsKeyOfType(nbtKey, NBT_TAG_INT) ) {
                    if ( pdc.getInt(nbtKey) == value ) {
                        return slot
                    }
                }
            }
        }
    }

    return -1
}

/**
 * Set an item stack's armor attribute using NMS.
 * https://www.spigotmc.org/threads/tutorial-the-complete-guide-to-itemstack-nbttags-attributes.131458/
 * 
 * Return new item stack with new armor
 * 
 * NOTE:
 * in 1.16.X
 * NmsNBTTagString.a(str) is the static constructor
 * This is same for all NBTTag_____ objects.
 */
internal fun setItemArmorNMS(
    item: ItemStack,
    armor: Int,
    slot: String,
    uuidLeast: Int,
    uuidMost: Int,
): ItemStack {
    val nmsItem = CraftItemStack.asNMSCopy(item)
    if ( nmsItem != null ) {
        val tag: NmsNBTTagCompound = if ( nmsItem.hasTag() ) {
            nmsItem.getTag()!!
        } else {
            NmsNBTTagCompound()
        }

        // attribute modifiers are an nbt tag list
        val attributeModifiers = NmsNBTTagList()

        // NOTE: THESE ARE USING XC's INTERNAL NBTTag VALUE CLASS WRAPPERS
        // SEE nms.kt IMPLEMENTATIONS
        val armorTag = NmsNBTTagCompound()
        armorTag.putTag("AttributeName", NBTTagString("generic.armor").toNms())
        armorTag.putTag("Name", NBTTagString("generic.armor").toNms())
        armorTag.putTag("Amount", NBTTagInt(armor).toNms())
        armorTag.putTag("Slot", NBTTagString(slot).toNms())
        armorTag.putTag("Operation", NBTTagInt(0).toNms())
        armorTag.putTag("UUIDLeast", NBTTagInt(uuidLeast).toNms())
        armorTag.putTag("UUIDMost", NBTTagInt(uuidMost).toNms())

        attributeModifiers.add(armorTag)
        tag.putTag("AttributeModifiers", attributeModifiers)

        nmsItem.setTag(tag)
        
        return CraftItemStack.asBukkitCopy(nmsItem)
    }

    // failed to set armor
    return item
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
    val nmsItem = craftPlayer.getMainHandNMSItem()

    if ( nmsItem != null ) {
        val itemMat = CraftMagicNumbers.getMaterial(nmsItem.getItem())
        if ( itemMat == material ) {
            val tag: NmsNBTTagCompound? = nmsItem.getTag()
            // println("tags = $tag")
            // https://www.spigotmc.org/threads/registering-custom-entities-in-1-14-2.381499/#post-3460944
            if ( tag != null && tag.containsKey(BUKKIT_STORAGE_TAG) ) {
                // persistent data container holder NmsNBTTagCompound
                val pdc = tag.getCompound(BUKKIT_STORAGE_TAG)!!
                if ( pdc.containsKeyOfType(key, NBT_TAG_INT) ) {
                    return pdc.getInt(key)
                }
            }
        }
    }

    return -1
}


// ============================================================================
// GUN ITEM GETTERS
// ============================================================================

/**
 * Get a gun from nms item stack using raw NBT tags.
 * This checks if material matches config gun material.
 */
public fun XC.getGunFromNmsItemStack(nmsItem: NmsItemStack): Gun? {
    return getObjectFromNmsItemStack(
        nmsItem,
        this.config.materialGun,
        this.storage.gun,
    )
}

/**
 * Return gun if player holding a gun in main hand.
 * This uses raw NMS to check item tags.
 */
public fun XC.getGunInHand(player: Player): Gun? {
    val craftPlayer = player as CraftPlayer
    val nmsItem = craftPlayer.getMainHandNMSItem()
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getGunFromNmsItemStack(nmsItem)
    }

    return null
}

/**
 * Return gun player is holding in hand from item's 
 * custom model id, without checking if item material is 
 * the gun material type. Used in situations where code has already
 * checked if material is valid.
 */
public fun XC.getGunInHandUnchecked(player: Player): Gun? {
    val craftPlayer = player as CraftPlayer
    val nmsItem = craftPlayer.getMainHandNMSItem()
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getCustomItemUnchecked(nmsItem, this.storage.gun)
    }

    return null
}

/**
 * Return gun if player holding a gun in main hand.
 * This uses raw NMS to check item tags.
 */
public fun XC.getGunInSlot(player: Player, slot: Int): Gun? {
    val craftPlayer = player as CraftPlayer
    val nmsPlayer = craftPlayer.getHandle()
    val nmsItem = nmsPlayer.inventory.getItem(slot)
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getGunFromNmsItemStack(nmsItem)
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
public fun XC.getGunFromItem(item: ItemStack): Gun? {
    if ( item.type == this.config.materialGun ) {
        try {
            return getGunFromItemNMS(item)
        } catch (err: Exception) {
            logger.warning("Error in getGunFromItem: $err")
            return getGunFromItemBukkit(item)
        }
    }
    return null
}

internal fun XC.getGunFromItemNMS(item: ItemStack): Gun? {
    val nmsItem = GetNmsItemStack.from(item as CraftItemStack)
    return getGunFromNmsItemStack(nmsItem)
}

/**
 * Safe bukkit method to get gun from item.
 * This is very inefficient because it clones the itemMeta.
 */
internal fun XC.getGunFromItemBukkit(item: ItemStack): Gun? {
    val itemMeta = item.getItemMeta()
    if ( itemMeta != null && itemMeta.hasCustomModelData() ) {
        val modelId = itemMeta.getCustomModelData()
        if ( modelId < this.config.maxGunTypes ) {
            return this.storage.gun[modelId]
        }
    }
    return null
}


// ============================================================================
// THROWABLE ITEM GETTERS
// ============================================================================

/**
 * Get a throwable from nms item stack using raw NBT tags.
 * This checks if material matches config throwable item material.
 */
public fun XC.getThrowableFromNmsItemStack(nmsItem: NmsItemStack): ThrowableItem? {
    return getObjectFromNmsItemStack(
        nmsItem,
        this.config.materialThrowable,
        this.storage.throwable,
    )
}

/**
 * Return throwable if player holding a throwable in main hand.
 * This uses raw NMS to check item tags.
 */
public fun XC.getThrowableInHand(player: Player): ThrowableItem? {
    val craftPlayer = player as CraftPlayer
    val nmsItem = craftPlayer.getMainHandNMSItem()
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getThrowableFromNmsItemStack(nmsItem)
    }

    return null
}

/**
 * Return throwable from player's main hand item, without
 * checking if the material is correct.
 */
public fun XC.getThrowableInHandUnchecked(player: Player): ThrowableItem? {
    val craftPlayer = player as CraftPlayer
    val nmsItem = craftPlayer.getMainHandNMSItem()
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getCustomItemUnchecked(nmsItem, this.storage.throwable)
    }

    return null
}


/**
 * Return throwable mapped from item's custom model id.
 * Return null if id out of range or if no gun mapped.
 * 
 * GETTING ITEM META IS ONE OF THE SLOWEST + BIGGEST TICK TIMES
 * THIS FUNCTION IS ALSO EXTREMELY COMMON EACH TICK
 * (runs MULTIPLE times per player)
 * SO WE HAVE TO OPTIMIZE THIS AS MUCH AS POSSIBLE WITH RAW NMS
 */
public fun XC.getThrowableFromItem(item: ItemStack): ThrowableItem? {
    if ( item.type == this.config.materialThrowable ) {
        try {
            return getThrowableFromItemNMS(item)
        } catch (err: Exception) {
            logger.warning("Error in getThrowableFromItem: $err")
            return getThrowableFromItemBukkit(item) // fallback
        }
    }
    return null
}

internal fun XC.getThrowableFromItemNMS(item: ItemStack): ThrowableItem? {
    val nmsItem = GetNmsItemStack.from(item as CraftItemStack)
    return getThrowableFromNmsItemStack(nmsItem)
}

/**
 * Safe bukkit method to get gun from item.
 * This is very inefficient because it clones the itemMeta.
 */
internal fun XC.getThrowableFromItemBukkit(item: ItemStack): ThrowableItem? {
    val itemMeta = item.getItemMeta()
    if ( itemMeta != null && itemMeta.hasCustomModelData() ) {
        val modelId = itemMeta.getCustomModelData()
        if ( modelId < this.config.maxThrowableTypes ) {
            return this.storage.throwable[modelId]
        }
    }
    return null
}


// ============================================================================
// MELEE WEAPON ITEM GETTERS
// ============================================================================

/**
 * Get a melee weapon from nms item stack using raw NBT tags.
 * This checks if material matches config melee item material.
 */
public fun XC.getMeleeFromNmsItemStack(nmsItem: NmsItemStack): MeleeWeapon? {
    return getObjectFromNmsItemStack(
        nmsItem,
        this.config.materialMelee,
        this.storage.melee,
    )
}

/**
 * Return throwable if player holding a throwable in main hand.
 * This uses raw NMS to check item tags.
 */
public fun XC.getMeleeInHand(player: Player): MeleeWeapon? {
    val craftPlayer = player as CraftPlayer
    val nmsItem = craftPlayer.getMainHandNMSItem()
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getMeleeFromNmsItemStack(nmsItem)
    }

    return null
}

/**
 * Return melee weapon from player's main hand item, without
 * checking if the material is correct.
 */
public fun XC.getMeleeInHandUnchecked(player: Player): MeleeWeapon? {
    val craftPlayer = player as CraftPlayer
    val nmsItem = craftPlayer.getMainHandNMSItem()
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getCustomItemUnchecked(nmsItem, this.storage.melee)
    }

    return null
}


// ============================================================================
// ARMOR/HAT ITEM GETTERS
// ============================================================================

/**
 * Get a hat from nms item stack using raw NBT tags.
 * This checks if material matches config hat material.
 */
public fun XC.getHatFromNmsItemStack(nmsItem: NmsItemStack): Hat? {
    return getObjectFromNmsItemStack(
        nmsItem,
        this.config.materialArmor,
        this.storage.hat,
    )
}

/**
 * Return hat if player holding a hat in main hand.
 * This uses raw NMS to check item tags.
 */
public fun XC.getHatInHand(player: Player): Hat? {
    val craftPlayer = player as CraftPlayer
    val nmsItem = craftPlayer.getMainHandNMSItem()
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getHatFromNmsItemStack(nmsItem)
    }

    return null
}

/**
 * Return hat from player's main hand item, without
 * checking if the material is correct.
 */
public fun XC.getHatInHandUnchecked(player: Player): Hat? {
    val craftPlayer = player as CraftPlayer
    val nmsItem = craftPlayer.getMainHandNMSItem()
    
    // println("itemInHand: $nmsItem")

    if ( nmsItem != null ) {
        return getCustomItemUnchecked(nmsItem, this.storage.hat)
    }

    return null
}
