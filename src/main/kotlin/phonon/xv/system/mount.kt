/**
 * Contain systems for players to mounting and unmounting vehicle entities.
 */

package phonon.xv.system

import java.util.UUID
import java.util.EnumSet
import org.bukkit.entity.Player
import org.bukkit.entity.ArmorStand
import phonon.xv.XV
import phonon.xv.core.*
import phonon.xv.common.UserInput
import phonon.xv.util.CustomArmorStand

public data class MountVehicleRequest(
    val player: Player,
    val elementId: VehicleElementId,
    val componentType: VehicleComponentType,
)

public data class DismountVehicleRequest(
    val player: Player,
    val elementId: VehicleElementId,
    val componentType: VehicleComponentType,
)

/**
 * System for mounting a vehicle entity.
 * Return empty queue for next tick.
 * TODO: this will be generalized to an "interact" event system.
 */
public fun systemMountVehicle(
    storage: ComponentsStorage,
    requests: List<MountVehicleRequest>,
): ArrayList<MountVehicleRequest> {
    for ( req in requests ) {
        println("REQUEST: ${req}")
        val (
            player,
            elementId,
            componentType,
        ) = req
        /**
         * What we want to do here:
         * -> Player clicked vehicle for some interaction.
         * Just handle mount for now...
         * 
         * 1. Find the vehicle element from clicked id
         * 2. Check if element has a seat component
         *    NOTE: just within element not entire vehicle
         * 3. Get the seat component from archetype
         * 4. Determine which seat should be mounted from
         *    the interacted component.
         * 5. Mount the player to that seat index.
         * 
         */
        
        // hard-coded layout, need to change when we implement an function
        // for element id => archetype
        val layout = EnumSet.of(
            VehicleComponentType.TRANSFORM,
            VehicleComponentType.MODEL,
            VehicleComponentType.SEATS,
            VehicleComponentType.LAND_MOVEMENT_CONTROLS,
        )

        // TODO: this lookup should be main engine function
        val archetypeIndex = XV.storage.lookup[layout]
        if ( archetypeIndex == null ) {
            println("ERROR: archetype not found")
            continue
        }
        val archetype = XV.storage.archetypes[archetypeIndex]!!

        val transformComponent = archetype.transform!![elementId]!!
        val modelComponent = archetype.model!![elementId]!!
        val seatsComponent = archetype.seats!![elementId]!!

        val seatToMount = modelComponent.seatToMount

        val world = player.world
        val locSeat = seatsComponent.getSeatLocation(seatToMount, transformComponent)
        val seatEntity = CustomArmorStand.create(world, locSeat)
        // val seatEntity = world.spawn(locSeat, ArmorStand::class.java)
        seatEntity.setGravity(false)
        seatEntity.setVisible(true)
        seatEntity.addPassenger(player)

        seatsComponent.armorstands[seatToMount] = seatEntity
        seatsComponent.passengers[seatToMount] = player
    }

    return ArrayList()
}

/**
 * System for unmounting a vehicle entity.
 * Return empty queue for next tick.
 */
public fun systemDismountVehicle(
    storage: ComponentsStorage,
    requests: List<DismountVehicleRequest>,
): ArrayList<DismountVehicleRequest> {
    for ( req in requests ) {
        // no-op right now
    }

    return ArrayList()
}

