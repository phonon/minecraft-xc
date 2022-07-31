/**
 * Common NMS item handling methods.
 */

package phonon.xc.compatibility.v1_16_R3.item

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import net.minecraft.server.v1_16_R3.NBTTagCompound
import net.minecraft.server.v1_16_R3.NBTTagList
import net.minecraft.server.v1_16_R3.NBTTagString
import net.minecraft.server.v1_16_R3.NBTTagInt
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

/**
 * Internal helper to get NMS item stack from a bukkit CraftItemStack.
 * Requires reflection to access private NMS item stack handle.
 */
internal object GetNMSItemStack {
    val privField = CraftItemStack::class.java.getDeclaredField("handle")

    init {
        privField.setAccessible(true)
    }

    public fun from(item: CraftItemStack): NMSItemStack {
        return privField.get(item) as NMSItemStack
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
 * CraftMetaItem.BUKKIT_CUSTOM_TAG.NBT
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
public fun getItemIntDataIfMaterialMatches(
    item: ItemStack,
    material: Material,
    key: String,
): Int {
    if ( item.type == material ) {
        try {
            val nmsItem = GetNMSItemStack.from(item as CraftItemStack)
            if ( nmsItem != null ) {
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
        } catch ( err: Exception ) {
            err.printStackTrace()
            XC.logger?.severe("Failed to get item NBT key: $err")
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
            val tag: NBTTagCompound? = nmsItem.getTag()
            // println("tags = $tag")
            // https://www.spigotmc.org/threads/registering-custom-entities-in-1-14-2.381499/#post-3460944
            if ( tag != null && tag.hasKey(BUKKIT_CUSTOM_TAG) ) {
                // persistent data container holder NBTTagCompound
                val pdc = tag.getCompound(BUKKIT_CUSTOM_TAG)!!
                if ( pdc.hasKeyOfType(nbtKey, NBT_TAG_INT) ) {
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
 * NBTTagString.a(str) is the static constructor
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
        val tag: NBTTagCompound = if ( nmsItem.hasTag() ) {
            nmsItem.getTag()!!
        } else {
            NBTTagCompound()
        }

        // attribute modifiers are an nbt tag list
        val attributeModifiers = NBTTagList()

        val armorTag = NBTTagCompound()
        armorTag.set("AttributeName", NBTTagString.a("generic.armor"))
        armorTag.set("Name", NBTTagString.a("generic.armor"))
        armorTag.set("Amount", NBTTagInt.a(armor))
        armorTag.set("Slot", NBTTagString.a(slot))
        armorTag.set("Operation", NBTTagInt.a(0))
        armorTag.set("UUIDLeast", NBTTagInt.a(uuidLeast))
        armorTag.set("UUIDMost", NBTTagInt.a(uuidMost))

        attributeModifiers.add(armorTag)
        tag.set("AttributeModifiers", attributeModifiers)

        nmsItem.setTag(tag)
        
        return CraftItemStack.asBukkitCopy(nmsItem)
    }

    // failed to set armor
    return item
}