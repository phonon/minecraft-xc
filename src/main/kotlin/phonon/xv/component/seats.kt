package phonon.xv.component

import com.google.gson.JsonObject
import java.util.logging.Logger
import org.tomlj.TomlTable
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import phonon.xv.core.*
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*
import java.util.*
import kotlin.collections.HashMap

/**
 * This adds a list of player seats to the vehicle element.
 * 
 * For mounting the seats there are two systems:
 * 1. Direct mounting a seat index by right clicking an armorstand
 *    model in the vehicle element. Set with "mount_seat = INDEX"
 *    property on any component with armorstand models.
 * 2. Custom raycasting system. Add a [seats_raycast] component to
 *    the vehicle elment. Each seat location is given a special hitbox.
 * 
 * For vehicles with only a single seat (e.g. a single driver bike),
 * use method 1. Just use "mount_seat" on the main model of the 
 * vehicle element. Method 1 will be more efficient for vehicles
 * that only need 1 seat per armorstand model.
 * 
 * For vehicles with many seats (e.g. car with 4 seats) and only a
 * single model, you must use method 2. Add a [seats_raycast] component
 * to the vehicle element. If using 2, avoid using any "mount_seat"
 * properties as these systems will conflict.
 */
public data class SeatsComponent(
    // number of seats
    val count: Int = 1,
    // seat local offsets in a packed array format
    // [x0, y0, z0, x1, y1, z1, ...]
    // size must equal 3*count
    val offsets: DoubleArray = doubleArrayOf(
        0.0, 0.0, 0.0,
    ),
): VehicleComponent<SeatsComponent> {
    override val type = VehicleComponentType.SEATS

    override fun self() = this

    // armor stand entities
    var armorstands: Array<Entity?> = Array(count) { null }

    // quick lookup for passenger in each seat
    var passengers: Array<Player?> = Array(count) { null }

    /**
     * Get seat location relative to input transform component.
     * Useful for initializing seat locations
     */
    public fun getSeatLocation(
        i: Int, // index
        transform: TransformComponent,
    ): Location {
        // get seat local offset
        val offsetX = this.offsets[i*3]
        val offsetY = this.offsets[i*3 + 1]
        val offsetZ = this.offsets[i*3 + 2]

        return Location(
            transform.world,
            transform.x + transform.yawCos * offsetX - transform.yawSin * offsetZ,
            transform.y + offsetY,
            transform.z + transform.yawSin * offsetX + transform.yawCos * offsetZ,
            transform.yawf,
            0f,
        )
    }

    override fun delete(
        vehicle: Vehicle,
        element: VehicleElement,
        entityVehicleData: HashMap<UUID, EntityVehicleData>,
        despawn: Boolean,
    ) {
        // in case there are any active stands
        for ( stand in armorstands ) {
            if ( stand !== null ) {
                entityVehicleData.remove(stand.uniqueId)
                stand.remove()
            }
        }
    }


    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, _logger: Logger? = null): SeatsComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()
            
            val count = toml.getLong("count")?.toInt() ?: 0
            properties["count"] = count
            
            val offsets = DoubleArray(count * 3)
            toml.getArray("offsets")?.let { arr ->
                for ( i in 0 until count ) {
                    offsets[i*3 + 0] = arr.getNumberAs<Double>(i*3 + 0)
                    offsets[i*3 + 1] = arr.getNumberAs<Double>(i*3 + 1)
                    offsets[i*3 + 2] = arr.getNumberAs<Double>(i*3 + 2)
                }
            }
            properties["offsets"] = offsets

            return mapToObject(properties, SeatsComponent::class)
        }
    }
}