package phonon.xv.component

import java.util.logging.Logger
import org.tomlj.TomlTable
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*

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
    override val type = VehicleComponentType.GUN_TURRET

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, _logger: Logger? = null): GunTurretComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getArray("turret_offset")?.let { arr ->
                properties["turretX"] = arr.getNumberAs<Double>(0)
                properties["turretY"] = arr.getNumberAs<Double>(1)
                properties["turretZ"] = arr.getNumberAs<Double>(2)
            }

            toml.getNumberAs<Double>("turret_yaw_max")?.let { properties["turretYawMax"] = it.toFloat() }
            
            toml.getArray("barrel_offset")?.let { arr ->
                properties["barrelX"] = arr.getNumberAs<Double>(0)
                properties["barrelY"] = arr.getNumberAs<Double>(1)
                properties["barrelZ"] = arr.getNumberAs<Double>(2)
            }

            toml.getNumberAs<Double>("barrel_pitch_min")?.let { properties["barrelPitchMin"] = it.toFloat() }
            toml.getNumberAs<Double>("barrel_pitch_max")?.let { properties["barrelPitchMax"] = it.toFloat() }

            return mapToObject(properties, GunTurretComponent::class)
        }
    }
}
