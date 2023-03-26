/**
 * Ammo module
 */

package phonon.xc.ammo

import java.nio.file.Path
import java.util.logging.Logger
import kotlin.math.min
import org.tomlj.Toml
import org.bukkit.ChatColor
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import phonon.xc.XC
import phonon.xc.util.mapToObject
import phonon.xc.util.IntoItemStack


public class Ammo(
    // id, same as custom model id
    public val id: Int = Int.MAX_VALUE,

    // ammo item/visual properties
    public val itemName: String = "ammo",
    public val itemLore: List<String>? = null,
): IntoItemStack {
    /**
     * Create a new ItemStack from ammo properties.
     */
    public override fun toItemStack(xc: XC): ItemStack {
        val item = ItemStack(xc.config.materialAmmo, 1)
        val itemMeta = item.getItemMeta()
        
        // name
        itemMeta.setDisplayName("${ChatColor.RESET}${this.itemName}")
        
        // model
        itemMeta.setCustomModelData(this.id)

        // lore
        this.itemLore?.let { itemMeta.setLore(it) }

        item.setItemMeta(itemMeta)

        return item
    }

    
    companion object {
        /**
         * Parse and return a Ammo from a `ammo.toml` file.
         * Return null if something fails or no file found.
         */
        public fun fromToml(source: Path, logger: Logger? = null): Ammo? {
            try {
                val toml = Toml.parse(source)

                // map with keys as constructor property names
                val properties = HashMap<String, Any>()

                // parse toml file into properties
                
                // ammo id/model data
                toml.getLong("id")?.let { properties["id"] = it.toInt() }

                // item properties
                toml.getTable("item")?.let { item -> 
                    item.getString("name")?.let { properties["itemName"] = ChatColor.translateAlternateColorCodes('&', it) }
                    item.getArray("lore")?.let { properties["itemLore"] = it.toList().map { s -> s.toString() } }
                }

                return mapToObject(properties, Ammo::class)
            } catch (e: Exception) {
                logger?.warning("Failed to parse ammo file: ${source.toString()}, ${e}")
                return null
            }
        }

    }
}