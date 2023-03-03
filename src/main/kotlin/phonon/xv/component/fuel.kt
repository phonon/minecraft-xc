package phonon.xv.component

import java.util.UUID
import java.util.logging.Logger
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.tomlj.TomlTable
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import phonon.xv.core.EntityVehicleData
import phonon.xv.core.Vehicle
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.VehicleElement
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*

// namespace keys for saving item persistent data
private val FUEL_KEY_CURRENT = NamespacedKey("xv", "current")

public data class FuelComponent(
    var current: Int = 0,
    val max: Int = 100,
    // how long it takes to refuel (in milliseconds)
    val timeRefuel: Long = 4000,
    // flag if player can reload outside vehicle
    val canReloadOutside: Boolean = true,
    // flag if player can reload inside vehicle
    val canReloadInside: Boolean = false,
    // minecraft material used as fuel
    // @prop material = "DRAGON_BREATH"
    val material: Material = Material.DRAGON_BREATH, // @skip
    // amount of load fuel to give per item
    val amountPerItem: Int = 1,
    // max amount OF ITEMS to load per refuel
    val maxAmountPerRefuel: Int = 64,
    // amount of time per fuel burn while not moving (in ticks)
    val timePerFuelWhenIdle: Long = 200,
    // amouint of time per fuel burn while moving (in ticks)
    val timePerFuelWhenMoving: Long = 60,
    // drop fuel item instead of storing into item during despawn
    val dropItem: Boolean = true,
): VehicleComponent<FuelComponent> {
    // create item stack for checking if items are correct fuel item type
    val item: ItemStack = ItemStack(material)

    override val type = VehicleComponentType.FUEL

    override fun self() = this
    
    // burn rates (inverse of fuel duration), relative to idle time
    val burnRateIdle: Double = 1.0
    val burnRateMoving: Double = burnRateIdle * timePerFuelWhenIdle / timePerFuelWhenMoving

    // counter for fuel ticks, counts down to 0
    var timeRemaining: Double = timePerFuelWhenIdle.toDouble()

    init {
        current = current.coerceIn(0, max)
    }

    /**
     * During creation, inject item specific properties and generate
     * a new instance of this component.
     */
    override fun injectItemProperties(
        itemData: PersistentDataContainer?,
    ): FuelComponent {
        if ( itemData === null ) {
            return this.self()
        }
        return this.copy(
            current = itemData.get(FUEL_KEY_CURRENT, PersistentDataType.INTEGER) ?: 0,
        )
    }

    override fun toItemData(
        itemMeta: ItemMeta,
        itemLore: ArrayList<String>,
        itemData: PersistentDataContainer,
    ) {
        if ( this.dropItem ) {
            return // skip, no need to save if not storing
        }
        itemData.set(FUEL_KEY_CURRENT, PersistentDataType.INTEGER, this.current)
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
            current = json["current"].asInt.coerceIn(0, this.max)
        )
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, logger: Logger? = null): FuelComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getNumberAs<Int>("current")?.let { properties["current"] = it }
            toml.getNumberAs<Int>("max")?.let { properties["max"] = it }

            toml.getLong("time_refuel")?.let { properties["timeRefuel"] = it }
            
            toml.getBoolean("can_reload_outside")?.let { properties["canReloadOutside"] = it }
            toml.getBoolean("can_reload_inside")?.let { properties["canReloadInside"] = it }
            
            toml.getString("material")?.let { s ->
                Material.getMaterial(s)?.let { properties["material"] = it } ?: run {
                    logger?.warning("[FuelComponent] Invalid material: ${s}")
                }
            }
            toml.getNumberAs<Int>("amount_per_item")?.let { properties["amountPerItem"] = it }
            toml.getNumberAs<Int>("max_amount_per_refuel")?.let { properties["maxAmountPerRefuel"] = it }

            toml.getLong("time_per_fuel_when_idle")?.let { properties["timePerFuelWhenIdle"] = it }
            toml.getLong("time_per_fuel_when_moving")?.let { properties["timePerFuelWhenMoving"] = it }
            
            toml.getBoolean("drop_item")?.let { properties["dropItem"] = it }

            return mapToObject(properties, FuelComponent::class)
        }
    }
}