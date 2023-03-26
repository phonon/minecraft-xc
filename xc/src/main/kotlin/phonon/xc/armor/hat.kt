/**
 * Handler for helmets/hats
 */

package phonon.xc.armor

import java.nio.file.Path
import java.util.UUID
import java.util.logging.Logger
import kotlin.math.max
import org.tomlj.Toml
import org.bukkit.ChatColor
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.enchantments.Enchantment
import phonon.xc.XC
import phonon.xc.item.getHatInHand
import phonon.xc.util.mapToObject
import phonon.xc.util.IntoItemStack


public data class Hat(
    public val id: Int = Int.MAX_VALUE, // invalid
    public val armor: Double = 4.0,
    public val enchants: HashMap<Enchantment, Int> = HashMap(),

    // item properties
    public val itemName: String = "helmet",
    public val itemModel: Int = 0,
    public val itemLore: List<String> = listOf(),
): IntoItemStack {
    /**
     * Create a new ItemStack from helmet properties.
     */
    public override fun toItemStack(xc: XC): ItemStack {
        val item = ItemStack(xc.config.materialArmor, 1)
        val itemMeta = item.getItemMeta()
        
        // name
        itemMeta.setDisplayName("${ChatColor.RESET}${this.itemName}")
        
        // model
        itemMeta.setCustomModelData(this.itemModel)

        // lore
        itemMeta.setLore(this.itemLore)

        // set armor
        val modifier = AttributeModifier(
            UUID.randomUUID(),
            "armor_helmet",
            this.armor,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlot.HEAD,
        )
        itemMeta.addAttributeModifier(Attribute.GENERIC_ARMOR, modifier)
        
        itemMeta.removeItemFlags(ItemFlag.HIDE_ATTRIBUTES)

        // apply enchantments
        for ( (enchant, level) in this.enchants ) {
            itemMeta.addEnchant(enchant, level, true) // ignores enchant level restricts
        }

        item.setItemMeta(itemMeta)

        return item
    }


    companion object {
        /**
         * Parse and return all Hats specified in a `hat.toml` config file.
         * This returns a list of hats since format allows each file to
         * specify multiple hats in an array of tables. This is because
         * most hats config is small and are nearly identical.
         * 
         * Return null list if any hat fails to parse.if something fails
         * or no file found.
         */
        public fun listFromToml(source: Path, logger: Logger? = null): List<Hat>? {
            val hats = ArrayList<Hat>()

            try {
                val toml = Toml.parse(source)

                toml.getArray("hat")?.let { tomlHatArray ->
                    for ( i in 0 until tomlHatArray.size() ) {
                        val tomlHat = tomlHatArray.getTable(i)

                        // map with keys as constructor property names
                        val properties = HashMap<String, Any>()

                        // parse basic item properties
                        tomlHat.getLong("id")?.let { properties["id"] = it.toInt() }
                        tomlHat.getString("name")?.let { properties["itemName"] = ChatColor.translateAlternateColorCodes('&', it) }
                        tomlHat.getLong("model")?.let { properties["itemModel"] = it.toInt() }
                        tomlHat.getArray("lore")?.let { properties["itemLore"] = it.toList().map { s -> s.toString() } }
                        tomlHat.getDouble("armor")?.let { properties["armor"] = it }
                        
                        // parse enchantments
                        tomlHat.getTable("enchants")?.let { enchantTable ->
                            val enchants = HashMap<Enchantment, Int>()
                            for ( key in enchantTable.keySet() ) {
                                val en = Enchantment.getByKey(NamespacedKey.minecraft(key));
                                if ( en != null ) {
                                    val level = enchantTable.getLong(key)?.toInt()
                                    if ( level != null ) {
                                        enchants[en] = level
                                    } else {
                                        logger?.warning("Failed to parse enchantment level for ${key}")
                                    }
                                } else {
                                    logger?.warning("Unknown enchantment: ${key}")
                                }
                            }
                            properties["enchants"] = enchants
                        }

                        hats.add(mapToObject(properties, Hat::class))
                    }
                }
            } catch (e: Exception) {
                logger?.warning("Failed to parse hat file: ${source.toString()}, ${e}")
                e.printStackTrace()
                return null
            }

            return hats
        }
    }
}


/**
 * Request for player to wear a hat item.
 */
@JvmInline
internal value class PlayerWearHatRequest(
    val player: Player,
)


/**
 * System for handling player wear hat requests.
 * Puts hat item in main hand into head slot.
 * Reset empty request queue for next tick cycle.
 */
internal fun XC.wearHatSystem(
    wearHatRequests: List<PlayerWearHatRequest>,
) {
    val playerHandled = HashSet<UUID>(wearHatRequests.size) // players ids already handled to avoid redundant requests

    for ( request in wearHatRequests ) {
        try {
            val player = request.player
            val playerId = player.getUniqueId()
            
            if ( playerHandled.add(playerId) == false ) {
                // false if already contained in set
                continue
            }

            // Do redundant player main hand is hat check here
            // since events could override the first request, causing
            // inventory slot or item to change
            val hat = getHatInHand(player)
            if ( hat == null ) {
                continue
            }

            val playerInventory = player.getInventory()
            val newHelmet = playerInventory.getItemInMainHand()
            val currHelmet = playerInventory.getHelmet()
            playerInventory.setItemInMainHand(currHelmet)
            playerInventory.setHelmet(newHelmet)

            // equip sound
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1f)
        } catch (e: Exception) {
            e.printStackTrace()
            this.logger.severe("Failed to handle player wear hat request: ${e}")
        }
    }

    // new empty queue for next tick
    this.wearHatRequests = ArrayList(0)
}