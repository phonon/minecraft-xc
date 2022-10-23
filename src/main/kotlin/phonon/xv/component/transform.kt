package phonon.xv.component

import java.util.logging.Logger
import org.tomlj.TomlTable
import org.bukkit.World
import phonon.xv.core.VehicleComponent
import phonon.xv.util.mapToObject

/**
 * Contains a vehicle elements world position and rotation.
 * 
 * Vehicles are rigid bodies, so no scale.
 */
public data class TransformComponent(
    // minecraft world, immutable, don't allow moving between worlds :^(
    val world: World?,
): VehicleComponent {
    // world position
    var x: Double = 0.0
    var y: Double = 0.0
    var z: Double = 0.0
    var positionDirty: Boolean = false

    // rotation
    var yaw: Double = 0.0
    var yawf: Float = 0f
    var yawRad: Double = 0.0
    var yawSin: Double = 0.0
    var yawCos: Double = 0.0
    var yawDirty: Boolean = false

    // flag that vehicle in water
    var inWater: Boolean = false

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, _logger: Logger? = null): TransformComponent {
            // currently nothing to parse
            return TransformComponent(world = null)
        }
    }
}