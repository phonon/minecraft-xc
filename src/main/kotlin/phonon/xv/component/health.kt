package phonon.xv.component

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.bukkit.NamespacedKey
import kotlin.math.min
import java.util.logging.Logger
import org.tomlj.TomlTable
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import phonon.xc.util.damage.DamageType
import phonon.xc.util.EnumArrayMap
import phonon.xc.util.death.XcPlayerDeathEvent
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*

// namespace keys for saving item persistent data
val HEALTH_KEY_CURRENT = NamespacedKey("xv", "current")

/**
 * Small death event for vehicles which contains killer but not player
 * killed, so that when a vehicle is killed we can create death events for
 * all passengers in the vehicle.
 */
public data class VehicleKilledEvent(
    val killer: Entity?,
    val weaponType: Int, // use XC.ITEM_TYPE_*
    val weaponId: Int,   // to get weapon id in array
    val weaponMaterial: Material, // used for weapons attached to a material (e.g. landmine)
) {
    public fun toPlayerDeathEvent(player: Player): XcPlayerDeathEvent {
        return XcPlayerDeathEvent(
            player = player,
            killer = this.killer,
            weaponType = this.weaponType,
            weaponId = this.weaponId,
            weaponMaterial = this.weaponMaterial,
        )
    }
}

/**
 * Health and death component.
 */
public data class HealthComponent(
    var current: Double = -1.0,
    val max: Double = 20.0,
    // damage sources multipliers
    val damageMultiplier: EnumArrayMap<DamageType, Double> = EnumArrayMap.from({_dmg -> 0.0}),
    // whether to support death
    val death: Boolean = true,
    // how much damage to deal to passengers when vehicle dies
    val deathPassengerDamage: Double = 9999.9,
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

    // runtime death message
    var deathEvent: VehicleKilledEvent? = null

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
            
            toml.getNumberAs<Double>("death_passenger_damage")?.let { properties["deathPassengerDamage"] = it }

            toml.getTable("damage_multiplier")?.let { properties["damageMultiplier"] = it.getEnumArrayMap<DamageType, Double>(0.0) }
            
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