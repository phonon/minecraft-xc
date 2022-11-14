/**
 * Contain throwable item object.
 */

package phonon.xc.melee

import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.util.logging.Logger
import java.util.UUID
import org.tomlj.Toml
import org.bukkit.Color
import org.bukkit.ChatColor
import org.bukkit.inventory.ItemStack
import phonon.xc.XC
import phonon.xc.util.mapToObject
import phonon.xc.util.damage.DamageType
import phonon.xc.util.IntoItemStack

/**
 * Melee weapon.
 * Implements "click spam" weapons which use old 1.8 style pvp.
 * Damage = click, cooldown does not matter.
 */
public data class MeleeWeapon(
    // gun item/visual properties
    public val itemName: String = "melee",
    public val itemLore: List<String> = listOf(),
    public val itemModelDefault: Int = 0,     // default normal model (custom model data id)

    // damage
    public val damage: Double = 8.0,
    public val damageArmorReduction: Double = 0.5,     // reduction per armor point
    public val damageResistanceReduction: Double = 1.0, // reduction per resistance protection level
    public val damageType: DamageType = DamageType.MELEE,
): IntoItemStack {

    /**
     * Create a new ItemStack from properties.
     */
    public override fun toItemStack(xc: XC): ItemStack {
        val item = ItemStack(xc.config.materialMelee, 1)
        val itemMeta = item.getItemMeta()
        
        // name
        itemMeta.setDisplayName("${ChatColor.RESET}${this.itemName}")
        
        // model
        itemMeta.setCustomModelData(this.itemModelDefault)
 
        // item lore description
        itemMeta.setLore(this.itemLore)

        item.setItemMeta(itemMeta)

        return item
    }

    companion object {
        /**
         * Parse and return a MeleeWeapon from a `melee.toml` file.
         * Return null MeleeWeapon if something fails or no file found.
         */
        public fun fromToml(source: Path, logger: Logger? = null): MeleeWeapon? {
            try {
                val toml = Toml.parse(source)

                // map with keys as constructor property names
                val properties = HashMap<String, Any>()

                // item properties
                toml.getTable("item")?.let { item -> 
                    item.getString("name")?.let { properties["itemName"] = ChatColor.translateAlternateColorCodes('&', it) }
                    item.getArray("lore")?.let { properties["itemLore"] = it.toList().map { s -> s.toString() } }
                }

                // item model properties
                toml.getTable("model")?.let { model -> 
                    model.getLong("default")?.let { properties["itemModelDefault"] = it.toInt() }
                }

                // damage
                toml.getTable("damage")?.let { d ->
                    d.getDouble("damage")?.let { properties["damage"] = it }
                    d.getDouble("armor_reduction")?.let { properties["damageArmorReduction"] = it }
                    d.getDouble("resist_reduction")?.let { properties["damageResistanceReduction"] = it }
                    d.getString("damage_type")?.let { name ->
                        val damageType = DamageType.match(name)
                        if ( damageType != null ) {
                            properties["damageType"] = damageType
                        } else {
                            logger?.warning("Unknown damage type: ${name}")
                        }
                    }
                }

                return mapToObject(properties, MeleeWeapon::class)
            } catch (e: Exception) {
                logger?.warning("Failed to parse melee file: ${source.toString()}, ${e}")
                e.printStackTrace()
                return null
            }
        }
    }
}

