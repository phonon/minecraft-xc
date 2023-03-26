/**
 * Inventory gui for getting custom items.
 * Also contain interface for custom items that can be converted
 * into an ItemStack.
 */

package phonon.xc.util

import kotlin.math.max
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import net.kyori.adventure.text.Component
import phonon.xc.XC

/**
 * Interface for any custom item type that can be converted into
 * a Bukkit ItemStack.
 */
internal interface IntoItemStack {
    /**
     * Convert this type into a Bukkit ItemStack.
     * Requires XC instance to get config values (material, meta keys, etc.).
     */
    fun toItemStack(xc: XC): ItemStack
}

/**
 * Generic gui for getting plugin's custom items.
 * TODO: pagination
 */
internal class CustomItemGui(
    val xc: XC,
    val title: String,
    val ids: IntArray,
    val storage: Array<out IntoItemStack?>,
    // page converts to base index ids for which items to show
    // convention: page should start at 0
    val page: Int = 0,
): InventoryHolder {
    val maxPages = 1 + (ids.size / 54)
    val inv: Inventory = Bukkit.createInventory(this, 54, Component.text("${title} (Page ${page+1}/${maxPages})"))
    val baseIndex = max(0, page * 54) // floor to zero
    
    override public fun getInventory(): Inventory {
        var n = 0 // index in storage container
        
        for ( i in baseIndex until ids.size ) {
            val id = ids[i]
            val obj = storage[id]
            if ( obj != null ) {
                this.inv.setItem(n, obj.toItemStack(xc))

                // inventory size cutoff
                n += 1
                if ( n >= 54 ) {
                    break
                }
            }
        }

        return this.inv
    }
}

/**
 * Helper extension function on XC to create custom item gui.
 */
internal fun XC.createCustomItemGui(
    title: String,
    ids: IntArray,
    storage: Array<out IntoItemStack?>,
    page: Int = 0,
): CustomItemGui {
    return CustomItemGui(this, title, ids, storage, page)
}
