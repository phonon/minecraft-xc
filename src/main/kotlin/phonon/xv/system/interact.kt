package phonon.xv.system

import java.util.Queue
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import phonon.xv.XV
import phonon.xv.core.Vehicle
import phonon.xv.core.VehicleElement
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.VehiclePrototype
import phonon.xv.system.CreateVehicleRequest
import phonon.xv.system.DestroyVehicleRequest
import phonon.xv.util.TaskProgress
import phonon.xv.util.progressBar10
import phonon.xv.util.Message
import phonon.xv.util.drain
import phonon.xv.util.item.addIntId
import phonon.xv.util.item.itemMaterialInMainHandEqualTo
import phonon.xv.util.item.itemInMainHandEquivalentTo

/**
 * Vehicle entity interaction types, either punch (left click)
 * or right click.
 */
public enum class VehicleEntityInteraction {
    LEFT_CLICK,
    RIGHT_CLICK,
    ;
}

/**
 * Vehicle player interaction request.
 */
public data class VehicleInteract(
    // vehicle interacted with
    val vehicle: Vehicle,
    // vehicle element interacted with
    val element: VehicleElement,
    // component type associated with interacted entity
    val componentType: VehicleComponentType,
    // player interacting with vehicle
    val player: Player,
    // vehicle entity interacted with
    val entity: Entity,
    // action
    val action: VehicleEntityInteraction,
)

/**
 * Handle vehicle armorstand interactions. For now, just a hard-coded system
 * for all possible actions. In future, perhaps create a more generalized
 * interaction system with handlers.
 */
public fun XV.systemInteract(
    requests: Queue<VehicleInteract>,
) {
    val xv = this

    for ( request in requests.drain() ) {
        val (
            vehicle,
            element,
            componentType,
            player,
            entity,
            action,
        ) = request

        try {
            if ( action == VehicleEntityInteraction.LEFT_CLICK ) { // punching vehicle
                // try despawning
                xv.despawnRequests.add(DespawnVehicleRequest(
                    vehicle = vehicle,
                    player = player,
                    dropItem = true,
                    force = false,
                    skipTimer = false,
                ))
            }
            else if ( action == VehicleEntityInteraction.RIGHT_CLICK ) {
                // try actions in order depending on layout:
                // 1. add fuel
                // 2. add ammo
                // 3. mount request
                val layout = element.layout
                if ( layout.contains(VehicleComponentType.FUEL) ) {
                    val fuelComponent = element.components.fuel!!
                    if ( player.itemMaterialInMainHandEqualTo(fuelComponent.material) ) {
                        xv.fuelRequests.add(FuelVehicleRequest(
                            fuelComponent = fuelComponent,
                            player = player,
                        ))
                        break
                    }
                }

                if ( layout.contains(VehicleComponentType.AMMO) ) {
                    val ammoComponent = element.components.ammo!!
                    println("TODO: check if item matches ammo")
                    // TODO: check if item matches ammo
                }

                // fueling or ammo load did not pass, try mounting
                // may not want this since it might block raycasts? idk...
                if ( layout.contains(VehicleComponentType.SEATS) ) {
                    xv.mountRequests.add(MountVehicleRequest(
                        player = player,
                        vehicle = vehicle,
                        element = element,
                        componentType = componentType,
                    ))
                }
            } else {
                // should never occur, left for future
            }
        } catch ( err: Exception ) {
            err.printStackTrace()
        }
    }
}
