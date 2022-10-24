package phonon.xv.component

import java.util.logging.Logger
import org.tomlj.TomlTable
import org.bukkit.World
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*

/**
 * Contains a vehicle elements world position and rotation.
 * 
 * Vehicles are rigid bodies, so no scale.
 */
public data class TransformComponent(
    // offset from parent element
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0,
    // minecraft world, immutable, don't allow moving between worlds :^(
    val world: World? = null,
    // current world position
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0,
    // rotation
    var yaw: Double = 0.0,
    var pitch: Double = 0.0,
): VehicleComponent {
    override val type = VehicleComponentType.TRANSFORM

    var positionDirty: Boolean = false

    var yawf: Float = yaw.toFloat()
    var yawRad: Double = Math.toRadians(yaw)
    var yawSin: Double = Math.sin(yawRad)
    var yawCos: Double = Math.cos(yawRad)
    var yawDirty: Boolean = false

    // flag that vehicle in water
    var inWater: Boolean = false

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, _logger: Logger? = null): TransformComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getArray("offset")?.let { arr ->
                properties["offsetX"] = arr.getNumberAs<Double>(0)
                properties["offsetY"] = arr.getNumberAs<Double>(1)
                properties["offsetZ"] = arr.getNumberAs<Double>(2)
            }

            return mapToObject(properties, TransformComponent::class)
        }
    }
}