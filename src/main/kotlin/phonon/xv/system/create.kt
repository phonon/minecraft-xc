package phonon.xv.system

import org.bukkit.Location
import org.bukkit.entity.Player
import phonon.xv.XV
import phonon.xv.core.*

public data class CreateVehicleRequest(
        val player: Player,
        val prototype: VehiclePrototype,
        val location: Location
        // what else...?
)

// TODO
// when we create a vehicle its gonna give the player
// a loading bar, but this functionality is common
// to refueling and reloading a cannon. (when we add that)
// where do we put it?

public fun systemCreateVehicle(
        storage: ComponentsStorage,
        requests: List<CreateVehicleRequest>
) {
    for (req in requests) {
        val (player, prototype, location) = req

        val vehicleId = XV.vehicleStorage.newId()
        val elements = ArrayList<VehicleElement>(prototype.elements.size)
        for ( eltPrototype in prototype.elements ) {
            // copy elements from prototype and create

        }

        val vehicle = Vehicle(
                "${prototype.name}${vehicleId}",
                vehicleId,
                prototype,
                elements.toTypedArray()
        )
        // steps:
        // 1. Reserve a VehicleId for the vehicle itself.
        // 2. iterate thru VehicleElementPrototypes
        // 3. Reserve a VehicleElementId in archetype storage (maybe update elementPrototypeData?)
        // 4. Write components to storage
        // 5. Spawn in armorstand, update entityVehicleData in main class
    }
}