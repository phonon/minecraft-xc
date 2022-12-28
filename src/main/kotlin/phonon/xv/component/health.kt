package phonon.xv.component

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.bukkit.NamespacedKey
import java.util.logging.Logger
import org.tomlj.TomlTable
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*

val currentHealth = NamespacedKey("xv", "current")

public data class HealthComponent(
    var current: Double,
    val max: Double,
): VehicleComponent<HealthComponent> {
    override val type = VehicleComponentType.HEALTH

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
    ): HealthComponent {
        if ( itemData === null )
            return this.self()
        return this.copy(
                current = itemData.get(currentHealth, PersistentDataType.DOUBLE)!!
        )
    }

    override fun toItemData(context: PersistentDataAdapterContext): PersistentDataContainer? {
        val container = context.newPersistentDataContainer()
        container.set(currentHealth, PersistentDataType.DOUBLE, this.current)
        return container
    }

    override fun toJson(): JsonObject {
        val json = JsonObject()
        json.add("current", JsonPrimitive(current))
        return json
    }

    override fun injectJsonProperties(json: JsonObject?): HealthComponent {
        if ( json === null ) return this.self()
        return this.copy(
            current = json["current"].asDouble.coerceIn(0.0, this.max)
        )
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, _logger: Logger? = null): HealthComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getNumberAs<Double>("current")?.let { properties["current"] = it }
            toml.getNumberAs<Double>("max")?.let { properties["max"] = it }

            return mapToObject(properties, HealthComponent::class)
        }
    }
}