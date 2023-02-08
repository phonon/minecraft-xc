package phonon.xv.system

import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import phonon.xv.XV
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.VehicleId
import phonon.xv.core.VehicleStorage
import java.util.*


public data class DeleteVehicleRequest(
    val id: VehicleId,
    val dropItem: Boolean, // if true location shouldn't be null
    val location: Location? = null
)

public fun XV.systemDeleteVehicle(
    vehicleStorage: VehicleStorage,
    componentStorage: ComponentsStorage,
    requests: Queue<DeleteVehicleRequest>
) {
    val xv = this

    while ( requests.isNotEmpty() ) {
        val (id, dropItem, location) = requests.remove()

        val vehicle = vehicleStorage.get(id)
        if ( vehicle === null) // invalid vehicle
            continue

        // construct item
        val item = if ( dropItem ) {
            vehicle.prototype.toItemStack(
                xv.config.materialVehicle,
                vehicle.elements,
            )
        } else {
            null
        }
        // we'll drop the item AFTER we destroy vehicle

        // free vehicle
        vehicleStorage.free(id)
        // free vehicle elements
        vehicle.elements.forEach { element ->
            // prototype still points to inserted components
            element.components.delete(vehicle, element, xv.entityVehicleData)
            // free from archetype
            val archetype = componentStorage.lookup[element.layout]!!
            archetype.free(element.id)
        }
        // drop item
        if ( dropItem && item !== null ) {
            location?.world?.dropItem(location, item)
        }
    }
}