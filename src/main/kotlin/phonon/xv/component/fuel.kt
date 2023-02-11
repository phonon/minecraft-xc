package phonon.xv.component

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.bukkit.NamespacedKey
import java.util.logging.Logger
import org.tomlj.TomlTable
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*

// namespace keys for saving item persistent data
private val FUEL_KEY_CURRENT = NamespacedKey("xv", "current")

public data class FuelComponent(
    var current: Double = 0.0,
    val max: Double = 100.0,
    // minecraft material used as fuel
    // @prop material = "DRAGON_BREATH"
    val material: Material = Material.DRAGON_BREATH, // @skip
    // amount of load fuel to give per item
    val amountPerItem: Int = 1,
    // amount of time per fuel burn while not moving (in milliseconds)
    val timePerFuelWhenIdle: Long = 10000,
    // amouint of time per fuel burn while moving (in milliseconds)
    val timePerFuelWhenMoving: Long = 3000,
): VehicleComponent<FuelComponent> {
    // create item stack for checking if items are correct fuel item type
    val item: ItemStack = ItemStack(material)

    override val type = VehicleComponentType.FUEL

    override fun self() = this
    
    init {
        current = current.coerceIn(0.0, max)
    }

    /**
     * During creation, inject item specific properties and generate
     * a new instance of this component.
     */
    override fun injectItemProperties(
        itemData: PersistentDataContainer?,
    ): FuelComponent {
        if ( itemData === null )
            return this.self()
        return this.copy(
            current = itemData.get(FUEL_KEY_CURRENT, PersistentDataType.DOUBLE)!!
        )
    }

    override fun toItemData(
        itemMeta: ItemMeta,
        itemLore: ArrayList<String>,
        itemData: PersistentDataContainer,
    ) {
        itemData.set(FUEL_KEY_CURRENT, PersistentDataType.DOUBLE, this.current)
        itemLore.add("Fuel: ${this.current}/${this.max}")
    }

    override fun toJson(): JsonObject {
        val json = JsonObject()
        json.add("current", JsonPrimitive(current))
        return json
    }

    override fun injectJsonProperties(json: JsonObject?): FuelComponent {
        if ( json === null ) return this.self()
        return this.copy(
            current = json["current"].asDouble.coerceIn(0.0, this.max)
        )
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, logger: Logger? = null): FuelComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getNumberAs<Double>("current")?.let { properties["current"] = it }
            toml.getNumberAs<Double>("max")?.let { properties["max"] = it }
            
            toml.getString("material")?.let { s ->
                Material.getMaterial(s)?.let { properties["material"] = it } ?: run {
                    logger?.warning("[FuelComponent] Invalid material: ${s}")
                }
            }
            toml.getNumberAs<Int>("amount_per_item")?.let { properties["amountPerItem"] = it }

            toml.getLong("time_per_fuel_when_idle")?.let { properties["timePerFuelWhenIdle"] = it }
            toml.getLong("time_per_fuel_when_moving")?.let { properties["timePerFuelWhenMoving"] = it }

            return mapToObject(properties, FuelComponent::class)
        }
    }
}