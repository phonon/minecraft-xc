package phonon.xv.component

import java.util.logging.Logger
import org.tomlj.TomlTable
import org.bukkit.entity.Entity
import phonon.xv.core.VehicleComponent
import phonon.xv.util.mapToObject


/**
 * Represents an ArmorStand model
 */
public data class ModelComponent(
    // armor stand local offset
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0,
    // hitbox size in blocks, at local position
    val hitboxX: Double = 2.0,
    val hitboxY: Double = 2.0,
    val hitboxZ: Double = 2.0,
    // armor stand entity
    var armorstand: Entity? = null,
): VehicleComponent {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, _logger: Logger? = null): ModelComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getArray("offset")?.let { arr ->
                properties["offsetX"] = arr.getDouble(0)
                properties["offsetY"] = arr.getDouble(1)
                properties["offsetZ"] = arr.getDouble(2)
            }
            
            toml.getArray("hitbox")?.let { arr ->
                properties["hitboxX"] = arr.getDouble(0)
                properties["hitboxY"] = arr.getDouble(1)
                properties["hitboxZ"] = arr.getDouble(2)
            }

            return mapToObject(properties, ModelComponent::class)
        }
    }
}