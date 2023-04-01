/**
 * Component for particle effects depending on vehicle condition.
 */

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
import phonon.xc.util.toml.*
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject


/**
 * Component for periodic smoke particle effects depending on vehicle condition.
 * The spawning of smoke particles is different than normal particles,
 * so this is a separate component.
 * 
 * This is intended for use as for train or ship chimney smoke, and smoke
 * indicators when vehicles are damaged.
 */
public data class SmokeParticlesComponent(
    val particle: Particle = Particle.CAMPFIRE_SIGNAL_SMOKE,
    // number of particles (SHOULD BE ZERO FOR SMOKE PARTICLES)
    val count: Int = 0,
    // position offset relative to element transform
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0,
    // random offset for each particle
    val randomX: Double = 0.0,
    val randomY: Double = 0.0,
    val randomZ: Double = 0.0,
    // for particles with extra data, such as campfire smoke
    val extraData: Double? = null,
    // particle float up speed
    val speed: Double = 0.02,
    // force particles render
    val force: Boolean = false,
    // minimum health before displaying particles, set to large number to always display
    val healthThreshold: Double = 10000.0,
    // how often to display particles, in ticks
    val tickPeriod: Int = 3,
): VehicleComponent<SmokeParticlesComponent> {
    override val type = VehicleComponentType.SMOKE_PARTICLES

    override fun self() = this

    // system specific state that should reset when vehicle recreated
    var tickCounter: Int = 0

    override fun deepclone(): SmokeParticlesComponent {
        return this.copy()
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, _logger: Logger? = null): SmokeParticlesComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getParticle("particle")?.let { properties["particle"] = it }
            toml.getNumberAs<Int>("count")?.let { properties["count"] = it }
            toml.getNumberAs<Double>("offset_x")?.let { properties["offsetX"] = it }
            toml.getNumberAs<Double>("offset_y")?.let { properties["offsetY"] = it }
            toml.getNumberAs<Double>("offset_z")?.let { properties["offsetZ"] = it }
            toml.getNumberAs<Double>("random_x")?.let { properties["randomX"] = it }
            toml.getNumberAs<Double>("random_y")?.let { properties["randomY"] = it }
            toml.getNumberAs<Double>("random_z")?.let { properties["randomZ"] = it }
            toml.getNumberAs<Double>("extra_data")?.let { properties["extraData"] = it }
            toml.getNumberAs<Double>("speed")?.let { properties["speed"] = it }
            toml.getBoolean("force")?.let { properties["force"] = it }
            toml.getNumberAs<Double>("health_threshold")?.let { properties["healthThreshold"] = it }
            toml.getNumberAs<Int>("tick_period")?.let { properties["tickPeriod"] = it }
            
            return mapToObject(properties, SmokeParticlesComponent::class)
        }
    }
}