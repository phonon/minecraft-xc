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

// namespace keys for saving item persistent data
private val AMMO_KEY_CURRENT = NamespacedKey("xv", "current")

public data class AmmoComponent(
    var current: Int = 0,
    val max: Int = 40,
): VehicleComponent<AmmoComponent> {
    override val type = VehicleComponentType.AMMO

    override fun self() = this
    
    init {
        current = current.coerceIn(0, max)
    }

    /**
     * During creation, inject item specific properties and generate
     * a new instance of this component.
     */
    override fun injectItemProperties(
        itemData: PersistentDataContainer?,
    ): AmmoComponent {
        if ( itemData === null )
            return this.self()
        return this.copy(
            current = itemData.get(AMMO_KEY_CURRENT, PersistentDataType.INTEGER)!!
        )
    }

    override fun toItemData(
        itemMeta: ItemMeta,
        itemLore: ArrayList<String>,
        itemData: PersistentDataContainer,
    ) {
        itemData.set(AMMO_KEY_CURRENT, PersistentDataType.INTEGER, this.current)
        itemLore.add("Ammo: ${this.current}/${this.max}")
    }

    override fun toJson(): JsonObject {
        val json = JsonObject()
        json.add("current", JsonPrimitive(current))
        return json
    }

    override fun injectJsonProperties(json: JsonObject?): AmmoComponent {
        if ( json === null ) return this.self()
        return this.copy(
            current = json["current"].asInt.coerceIn(0, this.max)
        )
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, logger: Logger? = null): AmmoComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getNumberAs<Int>("current")?.let { properties["current"] = it }
            toml.getNumberAs<Int>("max")?.let { properties["max"] = it }

            return mapToObject(properties, AmmoComponent::class)
        }
    }
}