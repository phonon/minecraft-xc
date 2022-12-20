/**
 * Mineman custom item utils.
 */

package phonon.xv.util.item

import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
 
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