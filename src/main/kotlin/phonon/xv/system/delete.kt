package phonon.xv.system

import phonon.xv.XV
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.VehicleId
import phonon.xv.core.VehicleStorage
import java.util.*

public data class DeleteVehicleRequest(
    val id: VehicleId
)

public fun XV.systemDeleteVehicle(
    vehicleStorage: VehicleStorage,
    componentStorage: ComponentsStorage,
    requests: Queue<DeleteVehicleRequest>
) {
    val xv = this

    while ( requests.isNotEmpty() ) {
        val (
            id
        ) = requests.remove()

        // free vehicle
        val vehicle = vehicleStorage.get(id)
        if ( vehicle === null)
            continue
        vehicleStorage.free(id)
        // free vehicle elements
        vehicle.elements.forEach {
            // prototype still points to inserted components
            it.prototype.delete(vehicle, it, xv.entityVehicleData)
            // free from archetype
            val archetype = componentStorage.lookup[it.layout]!!
            archetype.free(it.id)
        }
        // TODO construct item, inject props from all components,
        // then spawn
    }
}