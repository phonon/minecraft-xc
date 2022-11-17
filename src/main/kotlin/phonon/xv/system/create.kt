package phonon.xv.system

import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import phonon.xv.XV
import phonon.xv.core.*
import java.util.Stack

public data class CreateVehicleRequest(
        val player: Player,
        val prototype: VehiclePrototype,
        val location: Location
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
    fun buildElement(prototype: VehicleElementPrototype): VehicleElement {
        val childrenElts = ArrayList<VehicleElement>()
        // build children first
        for ( childPrototype in prototype.children!! ) {
            val elt = buildElement(childPrototype)
            childrenElts.add(elt)
        }
        val id = XV.storage.lookup[prototype.layout]!!.newId()
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

    for (req in requests) {
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
        // now we build relevant data for all the components
        // no codegen because some need special code :-D

        // TODO When adding components this will always need to be updated
        for ( elt in vehicle.elements ) {
            XV.storage.lookup[elt.layout()]!!.inject(
                    elt,
                    elt.prototype.fuel?.copy(),
                    elt.prototype.gunTurret?.copy(),
                    elt.prototype.health?.copy(),
                    elt.prototype.landMovementControls?.copy(),
                    if ( elt.prototype.model != null ) {
                        // TODO use custom armorstand implementation
                        val armorstand: ArmorStand = location.world!!.spawn(location, ArmorStand::class.java)
                        armorstand.setGravity(false)
                        armorstand.setVisible(true)
                        // armorstand.getEquipment()!!.setHelmet(createModel(Tank.modelMaterial, this.modelDataBody))
                        armorstand.setRotation(location.yaw, 0f)
                        XV.entityVehicleData[armorstand.uniqueId] = EntityVehicleData(
                                elt.id,
                                elt.layout(),
                                VehicleComponentType.MODEL
                        )
                        elt.prototype.model.copy(armorstand = armorstand)
                    } else {
                        null
                    },
                    elt.prototype.seats?.copy(),
                    elt.prototype.seatsRaycast?.copy(),
                    elt.prototype.transform?.copy(
                            world = location.world,
                            x = location.x,
                            y = location.y,
                            z = location.z,
                            yaw = location.yaw.toDouble()
                    )
            )
        }

        // steps:
        // 1. Reserve a VehicleId for the vehicle itself.
        // 2. iterate thru VehicleElementPrototypes
        // 3. Reserve a VehicleElementId in archetype storage (maybe update elementPrototypeData?)
        // 4. Write components to storage
        // 5. Spawn in armorstand, update entityVehicleData in main class
    }


}