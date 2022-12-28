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

// TODO these will likely need to be refactored.
val vehiclePrototypeKey = NamespacedKey("xv", "vehicle_prototype")
val elementsKey = NamespacedKey("xv", "elements")

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
            location!!
            val item = ItemStack(xv.config.materialVehicle)
            val meta = item.itemMeta
            val container = meta.persistentDataContainer
            // save vehicle prototype
            container.set(vehiclePrototypeKey, PersistentDataType.STRING, vehicle.prototype.name)
            // save each element
            val elementsContainer = container.adapterContext.newPersistentDataContainer()
            vehicle.elements.forEach { elt ->
                // elt prototype still points to inserted components
                val eltContainer = elt.prototype.toItemData(elementsContainer.adapterContext)
                // save with prototype name as key
                elementsContainer.set(
                    NamespacedKey("xv", elt.prototype.name),
                    PersistentDataType.TAG_CONTAINER,
                    eltContainer
                )
            }
            container.set(
                elementsKey,
                PersistentDataType.TAG_CONTAINER,
                elementsContainer
            )
            item.setItemMeta(meta)
            item
        } else {
            null
        }
        // we'll drop the item AFTER we destroy vehicle

        // free vehicle
        vehicleStorage.free(id)
        // free vehicle elements
        vehicle.elements.forEach {
            // prototype still points to inserted components
            it.prototype.delete(vehicle, it, xv.entityVehicleData)
            // free from archetype
            val archetype = componentStorage.lookup[it.layout]!!
            archetype.free(it.id)
        }
        // drop item
        if ( dropItem && item !== null ) {
            location?.world?.dropItem(location, item)
        }
    }
}