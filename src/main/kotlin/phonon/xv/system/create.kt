package phonon.xv.system

import org.bukkit.Location
import org.bukkit.entity.Player
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.VehicleElementId
import phonon.xv.core.VehicleElementPrototype
import phonon.xv.core.VehiclePrototype

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
        requests: List<CreateVehicleRequest>,
        elementPrototypeData: Map<VehicleElementId, VehicleElementPrototype>
) {
    for (req in requests) {
        val (player, prototype, location) = req

        // TODO
        // mc armorstand spawning stuff


    }
}