package phonon.xv.component

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.util.logging.Logger
import org.tomlj.TomlTable
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*

public data class FuelComponent(
    var current: Double,
    val max: Double,
): VehicleComponent<FuelComponent> {
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
        item: ItemStack,
        itemMeta: ItemMeta,
        itemData: PersistentDataContainer,
    ): FuelComponent {
        // TODO: get fuel parameter from item data
        return this
    }

    override fun toJson(): JsonObject? {
        val json = JsonObject()
        json.add("current", JsonPrimitive(current))
        return json
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, logger: Logger? = null): FuelComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getNumberAs<Double>("current")?.let { properties["current"] = it }
            toml.getNumberAs<Double>("max")?.let { properties["max"] = it }

            return mapToObject(properties, FuelComponent::class)
        }

        public fun fromJson(json: JsonObject, copy: FuelComponent) {
            copy.current = json["current"].asDouble
        }
    }
}