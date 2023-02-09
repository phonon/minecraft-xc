/**
 * Systems for in-game spawning and despawning of vehicles.
 * These manage the request and progress timer for spawn/despawn process.
 * When spawn/despawn is complete, these systems generate a  create/destroy
 * request which finalizes the process. The "create" and "destroy" systems
 * are responsible for the actual creation and destruction of vehicles.
 */

package phonon.xv.system

import java.util.Queue
import java.util.UUID
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import phonon.xv.XV
import phonon.xv.core.Vehicle
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.VehiclePrototype
import phonon.xv.system.CreateVehicleRequest
import phonon.xv.system.DestroyVehicleRequest

/**
 * A request to spawn a vehicle, containing optional creation sources
 * (player, item, etc.) which are used to customize creation parameters.
 */
public data class SpawnVehicleRequest(
    val prototype: VehiclePrototype,
    val location: Location? = null,
    val player: Player? = null,
    val item: ItemStack? = null,
    // if true, skip spawn timer 
    val skipTimer: Boolean = false,
)

/**
 * A request to despawn a vehicle, containing despawn source
 * and options.
 */
public data class DespawnVehicleRequest(
    val vehicle: Vehicle,
    val player: Player? = null,
    // if true, drop item for vehicle
    val dropItem: Boolean = true,
    // if true, despawn even if there are passengers
    val force: Boolean = false,
    // if true, skip despawn timer
    val skipTimer: Boolean = false,
)

/**
 * Handle despawning vehicle requests.
 * Actual deletion of vehicle and its elements is handled by the destroy
 * system.
 */
public fun XV.systemSpawnVehicle(
    requests: Queue<SpawnVehicleRequest>,
    createRequests: Queue<CreateVehicleRequest>,
) {
    val xv = this

    while ( requests.isNotEmpty() ) {
        val (
            prototype,
            location,
            player,
            item,
            skipTimer,
        ) = requests.remove()
    }
}

/**
 * Handle despawning vehicle requests.
 * Actual deletion of vehicle and its elements is handled by the destroy
 * system.
 */
public fun XV.systemDespawnVehicle(
    requests: Queue<DespawnVehicleRequest>,
    deleteRequests: Queue<DestroyVehicleRequest>,
) {
    val xv = this

    while ( requests.isNotEmpty() ) {
        val (
            vehicle,
            player,
            dropItem,
            force,
            skipTimer,
        ) = requests.remove()

        // create request to destroy vehicle
        deleteRequests.add(DestroyVehicleRequest(vehicle))

        try {
            // drop item if vehicle has valid position in world
            if ( dropItem ) {
                // search elements for first element with transform component
                // -> use that element as vehicle "location"
                var location: Location? = null
                for ( element in vehicle.elements ) {
                    if ( element.layout.contains(VehicleComponentType.TRANSFORM) ) {
                        val transform = element.components.transform!!
                        val world = transform.world
                        if ( world !== null ) {
                            // add some y offset so that item drops from near middle of vehicle
                            location = Location(world, transform.x, transform.y + 1.0, transform.z)
                        }
                        break
                    }
                }

                if ( location !== null ) {
                    val item = vehicle.prototype.toItemStack(
                        xv.config.materialVehicle,
                        vehicle.elements,
                    )
                    if ( item !== null ) {
                        location.world.dropItem(location, item)
                    }
                }
            } else {
                null
            }
        } catch ( err: Exception ) {
            err.printStackTrace()
        }
    }
}