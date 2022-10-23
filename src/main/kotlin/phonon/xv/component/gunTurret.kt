package phonon.xv.component

import java.util.logging.Logger
import org.tomlj.TomlTable
import phonon.xv.core.VehicleComponent
import phonon.xv.util.mapToObject

/**
 * Mouse-controlled rotating gun turret.
 * 
 * Component made from two armor stands:
 * - Turret: yaw (y-plane) rotation only
 * - Barrel: up/down rotation only in-plane,
 *           forward direction = turret yaw
 * 
 * Contains LOCAL position/rotation offsets from base element transform.
 * Controls system functions should always use this in combination
 * with a TransformComponent as the base transform position.
 * 
 * This also internally manages rendering armor stand models
 * for turret and barrel.
 */
public data class GunTurretComponent(
    // turret local offset relative to transform
    val turretX: Double = 0.0,
    val turretY: Double = 1.0,
    val turretZ: Double = 0.0,
    // max turret half arc in degrees.
    val turretYawMax: Float = 90f,
    // barrel local offset relative to transform (NOT turret)
    val barrelX: Double = 0.0,
    val barrelY: Double = 1.0,
    val barrelZ: Double = 0.0,
    // min barrel pitch rotation in degs
    val barrelPitchMin: Float = -15f,
    // max barrel pitch rotation in degs
    val barrelPitchMax: Float = 45f,
): VehicleComponent {
    
    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, _logger: Logger? = null): GunTurretComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getArray("turret_offset")?.let { arr ->
                properties["turretX"] = arr.getDouble(0)
                properties["turretY"] = arr.getDouble(1)
                properties["turretZ"] = arr.getDouble(2)
            }

            toml.getDouble("turret_yaw_max")?.let { properties["turretYawMax"] = it.toFloat() }
            
            toml.getArray("barrel_offset")?.let { arr ->
                properties["barrelX"] = arr.getDouble(0)
                properties["barrelY"] = arr.getDouble(1)
                properties["barrelZ"] = arr.getDouble(2)
            }

            toml.getDouble("barrel_pitch_min")?.let { properties["barrelPitchMin"] = it.toFloat() }
            toml.getDouble("barrel_pitch_max")?.let { properties["barrelPitchMax"] = it.toFloat() }

            return mapToObject(properties, GunTurretComponent::class)
        }
    }
}
