package phonon.xv.system

import java.util.Queue
import phonon.xv.XV
import phonon.xv.Config
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.iter.*
import phonon.xv.component.TransformComponent
import phonon.xv.system.DeleteVehicleRequest
import phonon.xv.util.Message

/**
 * System for updating seats if seats are dismounted, clear player
 * and reset player user input.
 */
public fun XV.systemWorldBorderCulling(
    config: Config,
    storage: ComponentsStorage,
    deleteRequests: Queue<DeleteVehicleRequest>,
) {
    val xv = this

    for ( (el, transform) in ComponentTuple1.query<
        TransformComponent,
    >(storage) ) {
        if (
            transform.x < config.cullingBorderMinX ||
            transform.y < config.cullingBorderMinY ||
            transform.z < config.cullingBorderMinZ ||
            transform.x > config.cullingBorderMaxX ||
            transform.y > config.cullingBorderMaxY ||
            transform.z > config.cullingBorderMaxZ
        ) {
            try {
                val element = storage.getElement(el)!!
                val vehicle = vehicleStorage.getOwningVehicle(element)!!
                deleteRequests.add(DeleteVehicleRequest(vehicle))

                // if there are seats, print message to players that
                // vehicle is outside border and is being deleted
                val seats = element.components.seats
                if ( seats !== null ) {
                    for ( i in 0 until seats.count ) {
                        val passenger = seats.passengers[i]
                        if ( passenger !== null && passenger.isValid() ) {
                            Message.error(passenger, "Vehicle travelled outside world border and is being deleted")
                        }
                    }
                }
            } catch ( err: Exception ) {
                err.printStackTrace()
                xv.logger.severe("[systemWorldBorderCulling] Failed to delete vehicle for element id ${el}")
            }
        }
    }
}

