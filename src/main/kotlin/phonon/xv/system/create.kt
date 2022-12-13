package phonon.xv.system

import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import phonon.xv.XV
import phonon.xv.core.*
import java.util.Queue
import java.util.Stack

public enum class CreateReason {
    NEW,
    LOAD
}

public data class CreateVehicleRequest(
        val player: Player,
        val prototype: VehiclePrototype,
        val location: Location,
        val reason: CreateReason = CreateReason.NEW
)

// TODO when we create a vehicle its gonna give the player
// a loading bar, but this functionality is common
// to refueling and reloading a cannon. (when we add that)
// where do we put it?

public fun systemCreateVehicle(
        componentStorage: ComponentsStorage,
        requests: Queue<CreateVehicleRequest>
) {
    // recursive inline function
    fun buildElement(prototype: VehicleElementPrototype): VehicleElement {
        val childrenElts = ArrayList<VehicleElement>()
        // build children first
        for ( childPrototype in prototype.children!! ) {
            val elt = buildElement(childPrototype)
            childrenElts.add(elt)
        }
        val id = componentStorage.lookup[prototype.layout]!!.newId()
        val elt = VehicleElement(
                "${prototype.vehicle}.${prototype.name}${id}",
                id,
                prototype,
                childrenElts.toTypedArray()
        )
        // go for another pass thru and set parent of children
        for ( child in childrenElts ) {
            child.parent = elt
        }
        return elt
    }

    // TODO consider pipelining
    while ( requests.isNotEmpty() ) {
        val req = requests.remove()
        val (player, prototype, location) = req

        val vehicleId = XV.vehicleStorage.newId()
        val elements = HashSet<VehicleElement>(prototype.elements.size)

        val stack = Stack<VehicleElement>()
        for ( rootPrototype in prototype.rootElements ) {
            val rootElt = buildElement(rootPrototype)
            // traverse tree and add elts
            stack.push(rootElt)
            while ( !stack.isEmpty() ) {
                val elt = stack.pop()
                elements.add(elt)
                for ( child in elt.children ) {
                    stack.push(child)
                }
            }
        }

        val vehicle = Vehicle(
                "${prototype.name}${vehicleId}",
                vehicleId,
                prototype,
                elements.toTypedArray()
        )

        for ( elt in vehicle.elements ) {
            injectComponents(elt, req)
        }
        // test stuff
        player.sendMessage("Created your vehicle at x:${location.x} y:${location.y} z:${location.z}")
    }


}