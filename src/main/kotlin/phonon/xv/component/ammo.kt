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

public data class AmmoComponent(
    var current: Double,
    val max: Double,
): VehicleComponent<AmmoComponent> {
    override val type = VehicleComponentType.AMMO

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
    ): AmmoComponent {
        // TODO: get ammo parameter from item data?
        return this
    }

    override fun toJson(): JsonObject {
        val json = JsonObject()
        json.add("current", JsonPrimitive(current))
        return json
    }

    override fun injectJsonProperties(json: JsonObject?): AmmoComponent {
        if ( json === null ) return this.self()
        return this.copy(
            current = json["current"].asDouble.coerceIn(0.0, this.max)
        )
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, logger: Logger? = null): AmmoComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getNumberAs<Double>("current")?.let { properties["current"] = it }
            toml.getNumberAs<Double>("max")?.let { properties["max"] = it }

            return mapToObject(properties, AmmoComponent::class)
        }
    }
}