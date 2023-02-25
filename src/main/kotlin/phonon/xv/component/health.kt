package phonon.xv.component

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.bukkit.NamespacedKey
import kotlin.math.min
import java.util.logging.Logger
import org.tomlj.TomlTable
import org.bukkit.ChatColor
import org.bukkit.Particle
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
val HEALTH_KEY_CURRENT = NamespacedKey("xv", "current")

public data class HealthComponent(
    var current: Double = -1.0,
    val max: Double = 20.0,
    // whether to support death
    val death: Boolean = true,
    // death effects
    val deathSound: String? = null,
    val deathParticle: Particle? = null,
    var deathParticleCount: Int = 1,
    var deathParticleRandomX: Double = 0.0,
    var deathParticleRandomY: Double = 0.0,
    var deathParticleRandomZ: Double = 0.0,
): VehicleComponent<HealthComponent> {
    override val type = VehicleComponentType.HEALTH

    override fun self() = this

    init {
        current = if ( current <= 0.0 ) {
            max
        } else {
            min(current, max)
        }
    }

    /**
     * During creation, inject item specific properties and generate
     * a new instance of this component.
     */
    override fun injectItemProperties(
        itemData: PersistentDataContainer?,
    ): HealthComponent {
        if ( itemData === null ) return this.self()
        return this.copy(
            current = itemData.get(HEALTH_KEY_CURRENT, PersistentDataType.DOUBLE) ?: this.max,
        )
    }

    override fun toItemData(
        itemMeta: ItemMeta,
        itemLore: ArrayList<String>,
        itemData: PersistentDataContainer,
    ) {
        itemData.set(HEALTH_KEY_CURRENT, PersistentDataType.DOUBLE, this.current)
        itemLore.add("${ChatColor.GRAY}Health: ${this.current.toInt()}/${this.max.toInt()}")
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

            toml.getBoolean("death")?.let { properties["death"] = it }
            
            toml.getString("death_sound")?.let { properties["deathSound"] = it }
            toml.getParticle("death_particle")?.let { properties["deathParticle"] = it }
            toml.getNumberAs<Double>("death_particle_count")?.let { properties["deathParticleCount"] = 1 }
            toml.getNumberAs<Double>("death_particle_randomX")?.let { properties["deathParticleRandomX"] = 0.0 }
            toml.getNumberAs<Double>("death_particle_randomY")?.let { properties["deathParticleRandomY"] = 0.0 }
            toml.getNumberAs<Double>("death_particle_randomZ")?.let { properties["deathParticleRandomZ"] = 0.0 }

            return mapToObject(properties, HealthComponent::class)
        }
    }
}