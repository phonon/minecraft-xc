package phonon.xv.component

import java.util.UUID
import java.util.logging.Logger
import com.google.gson.JsonObject
import org.tomlj.TomlTable
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import phonon.xc.XC
import phonon.xc.util.mapToObject
import phonon.xc.util.toml.*
import phonon.xv.core.*
import kotlin.collections.HashMap

/**
 * Namespace keys for tagging entities as dynamically created armorstand seats.
 */
val SEAT_ENTITY_TAG = NamespacedKey("xv", "seat")

/**
 * Helper function to tag an entity as a dynamically created armorstand seat,
 * with SEAT_ENTITY_TAG. The only thing that matters is the tag is present,
 * value attached is arbitrary.
 */
public fun Entity.addSeatTag() {
    this.getPersistentDataContainer().set(
        SEAT_ENTITY_TAG,
        PersistentDataType.BYTE,
        0, // ARBITRARY VALUE
    )
}

/**
 * Helper function to check if entity has seat tag.
 */
public fun Entity.hasSeatTag(): Boolean {
    val dataContainer = this.getPersistentDataContainer()
    return dataContainer.has(SEAT_ENTITY_TAG, PersistentDataType.BYTE)
}

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
 * 
 * TODO:
 * max mount distance parameter in component
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
    // seat passenger extra armor values, corresponding to each seat,
    // size must equal count
    val armor: DoubleArray = doubleArrayOf(
        0.0,
    ),
): VehicleComponent<SeatsComponent> {
    override val type = VehicleComponentType.SEATS

    override fun self() = this

    // armor stand entities
    val armorstands: Array<ArmorStand?> = Array(count) { null }

    // quick lookup for passenger in each seat
    val passengers: Array<Player?> = Array(count) { null }

    // flag for whether armor stand is controlled by seats systems
    // or is an external armor stand. e.g. for planes, typically
    // pilot mounts the plane entity armorstand itself, so we need
    // to flag that this armorstand is an "external" armorstand
    // and not controlled by seats systems.
    val armorstandIsExternal: BooleanArray = BooleanArray(count) { false }

    // Arrays for tracking and setting vehicle element health display
    // using seat armorstand health. This caches the current health being
    // displayed for the armorstand. If the health is different than actual
    // health component, the armorstand health should be updated.
    val healthDisplay: DoubleArray = DoubleArray(count) { -1.0 }
    val healthDisplayMax: DoubleArray = DoubleArray(count) { -1.0 }

    override fun deepclone(): SeatsComponent {
        return this.copy()
    }

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
        xc: XC,
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
        /**
         * Format for seats:
         * 
         * [seats]
         * seats = [ # array of tables
         *  { offset = [0, 0, 0], armor = 1000 },
         *  { offset = [1, 0, 1], armor = 1000 },
         *  { offset = [-1, 0, 1], armor = 1000 },
         * ]
         */
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, _logger: Logger? = null): SeatsComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()
            
            val seatsArray = toml.getArray("seats")

            val count = seatsArray?.size() ?: 0
            
            // pre-allocate seat offsets and armor arrays
            val offsets = DoubleArray(count * 3)
            val armor = DoubleArray(count)

            // parse each seat config and push into array at seat index
            if ( seatsArray !== null ) {
                for ( i in 0 until count ) {
                    val seatConfig = seatsArray.getTable(i)

                    seatConfig?.getArray("offset")?.let { arr ->
                        offsets[i*3 + 0] = arr.getNumberAs<Double>(0)
                        offsets[i*3 + 1] = arr.getNumberAs<Double>(1)
                        offsets[i*3 + 2] = arr.getNumberAs<Double>(2)
                    }

                    seatConfig?.getNumberAs<Double>("armor")?.let { it ->
                        armor[i] = it
                    }
                }
            }
            
            properties["count"] = count
            properties["offsets"] = offsets
            properties["armor"] = armor

            return mapToObject(properties, SeatsComponent::class)
        }
    }
}