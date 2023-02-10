/**
 * Mineman custom item utils.
 */

package phonon.xv.util.item

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Player
import phonon.xv.nms.NmsNBTTagCompound
import phonon.xv.nms.NBTTagInt
import phonon.xv.nms.putTag
import phonon.xv.nms.containsKey
import phonon.xv.nms.containsKeyOfType
import phonon.xv.nms.NmsItem
import phonon.xv.nms.NmsItemStack
import phonon.xv.nms.NmsPlayer
import phonon.xv.nms.CraftItemStack
import phonon.xv.nms.CraftPlayer
 
/**
 * NBT tag type for integers.
 */
private const val NBT_TAG_INT = 3

/**
 * Create a custom minecraft item for use as a custom model.
 */
public fun createCustomModelItem(
    mat: Material,
    customModelData: Int
): ItemStack {
    val item = ItemStack(mat, 1)
    val meta = item.getItemMeta()
    if ( meta !== null ) {
        meta.setCustomModelData(customModelData)
        item.setItemMeta(meta)
    }

    return item
}

/**
 * Item id counter for `addIntId` and `getIntId`.
 * This is NOT thread safe.
 */
private var itemIdCounter = 0

/**
 * Attaches a SHORT TERM integer NBT tag onto an ItemStack, mainly for
 * uniquely identifying ItemStack during short term tasks like
 * Progress tasks (e.g. vehicle spawning) where each time we get item
 * in player hand, a new ItemStack reference is generated. This is NOT
 * intended for long term storage of ids and ids will not be unique
 * across restarts.
 * 
 * This function is NOT THREAD SAFE. Only add int ID from main thread.
 */
public fun ItemStack.addIntId(): Int {
    val newId = itemIdCounter
    itemIdCounter += 1
    
    val nmsItem = this.toNms()
    var needSetTag = false
    val tags: NmsNBTTagCompound = if ( nmsItem.hasTag() ) {
        nmsItem.getTag()!!
    } else {
        needSetTag = true
        NmsNBTTagCompound()
    }
    tags.putTag("xv_item_id", NBTTagInt(newId).toNms())
    
    if ( needSetTag ) {
        nmsItem.setTag(tags)
    }
    
    // println("tags = $tags")

    return newId
}

/**
 * Read integer id from ItemStack NBT tag.
 */
public fun ItemStack.getIntId(): Int? {
    val nmsItem = this.toNms()
    val tags: NmsNBTTagCompound? = nmsItem.getTag()
    if ( tags != null && tags.containsKeyOfType("xv_item_id", NBT_TAG_INT) ) {
        return tags.getInt("xv_item_id")
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
 * Helper to convert a bukkit ItemStack to NMS ItemStack.
 */
internal fun ItemStack.toNms(): NmsItemStack {
    return GetNmsItemStack.from(this as CraftItemStack)
}

/**
 * Checks if two ItemStack are "equivalent". This means:
 * 1. If material matches (ignore amount)
 * 2. If `idTag` specified, check for id from the `ItemStack.getIntId`
 *    matches in other ItemStack.
 */
internal fun NmsPlayer.itemInMainHandEquivalentTo(
    other: NmsItemStack,
    idTag: Int? = null,
): Boolean {
    val itemInHand = this.getMainHandItem()

    if ( NmsItem.getId(itemInHand.item) != NmsItem.getId(other.item) ) {
        return false
    }

    if ( idTag !== null ) {
        // read new item in hand id tag
        val tags: NmsNBTTagCompound? = itemInHand.getTag()
        if ( tags === null || !tags.containsKeyOfType("xv_item_id", NBT_TAG_INT) ) {
            return false
        }

        val itemInHandIdTag = tags.getInt("xv_item_id")
        if ( itemInHandIdTag != idTag ) {
            return false
        }
    }

    return true
}


/**
 * Checks if two ItemStack are "equivalent", Bukkit version.
 * For more details, see `NmsPlayer.itemInMainHandEquivalentTo`.
 */
internal fun Player.itemInMainHandEquivalentTo(
    other: ItemStack,
    idTag: Int? = null,
): Boolean {
    val nmsPlayer = (this as CraftPlayer).getHandle()
    val nmsItem = other.toNms()
    return nmsPlayer.itemInMainHandEquivalentTo(nmsItem, idTag)
}