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
import phonon.xc.XC
import phonon.xv.XV
import phonon.xv.core.Vehicle
import phonon.xv.core.VehicleElement
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.VehiclePrototype
import phonon.xv.system.CreateVehicleRequest
import phonon.xv.system.DeleteVehicleRequest
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
public enum class VehicleInteraction {
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
    val action: VehicleInteraction,
)

/**
 * Handle vehicle armorstand interactions. For now, just a hard-coded system
 * for all possible actions. In future, perhaps create a more generalized
 * interaction system with handlers.
 */
public fun XV.systemInteractWithVehicle(
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
            if ( action == VehicleInteraction.LEFT_CLICK ) { // punching vehicle
                // try despawning
                xv.despawnRequests.add(DespawnVehicleRequest(
                    vehicle = vehicle,
                    player = player,
                    dropItem = true,
                    force = false,
                    skipTimer = false,
                ))
            }
            else if ( action == VehicleInteraction.RIGHT_CLICK ) {
                // try actions in order depending on layout:
                // 1. add fuel
                // 2. add ammo
                // 3. mount request
                if ( element.layout.contains(VehicleComponentType.FUEL) ) {
                    val fuelComponent = element.components.fuel!!
                    if ( fuelComponent.canReloadOutside && player.itemMaterialInMainHandEqualTo(fuelComponent.material) ) {
                        xv.fuelRequests.add(FuelVehicleRequest(
                            fuelComponent = fuelComponent,
                            player = player,
                        ))
                        continue
                    }
                }

                if ( element.layout.contains(VehicleComponentType.AMMO) ) {
                    val ammoComponent = element.components.ammo!!
                    if ( ammoComponent.canReloadOutside && player.itemMaterialInMainHandEqualTo(xv.xc.config.materialAmmo) ) {
                        xv.ammoLoadRequests.add(AmmoLoadRequest(
                            ammoComponent = ammoComponent,
                            player = player,
                            isInside = false,
                        ))
                        continue
                    }
                }

                // fueling or ammo load did not pass, try mounting
                // may not want this since it might block raycasts? idk...
                if ( element.layout.contains(VehicleComponentType.SEATS) ) {
                    xv.mountRequests.add(MountVehicleRequest(
                        player = player,
                        vehicle = vehicle,
                        element = element,
                        componentType = componentType,
                    ))
                    continue
                }
            } else {
                // should never occur, left for future
            }
        } catch ( err: Exception ) {
            err.printStackTrace()
        }
    }
}


/**
 * Vehicle player interaction request when a player is inside a vehicle.
 */
public data class VehicleInteractInside(
    // player interacting with vehicle
    val player: Player,
    // action
    val action: VehicleInteraction,
    // player vehicle interacted with
    val playerVehicle: Vehicle,
    // player vehicle element
    val playerElement: VehicleElement,
    // component type associated with interacted entity
    val playerComponentType: VehicleComponentType,
    // target vehicle entity interacted with
    val targetEntity: Entity?,
    // target vehicle (vehicle being interacted with)
    val targetVehicle: Vehicle?,
    // target vehicle element (vehicle being interacted with)
    val targetElement: VehicleElement?,
    // component type associated with interacted entity
    val targetComponentType: VehicleComponentType?,
)


/**
 * Handle interactions while inside a vehicle. 
 */
public fun XV.systemInteractInsideVehicle(
    requests: Queue<VehicleInteractInside>,
) {
    val xv = this

    for ( request in requests.drain() ) {
        val (
            player,
            action,
            playerVehicle,
            playerElement,
            playerComponentType,
            targetEntity,
            targetVehicle,
            targetElement,
            targetComponentType,
        ) = request

        try {
            if ( action == VehicleInteraction.LEFT_CLICK ) { // punching
                // create shoot request TODO
            }
            else if ( action == VehicleInteraction.RIGHT_CLICK ) {
                // try actions in order depending on layout:
                // 1. add fuel
                // 2. add ammo
                // 3. mount request
                if ( playerElement.layout.contains(VehicleComponentType.FUEL) ) {
                    val fuelComponent = playerElement.components.fuel!!
                    if ( fuelComponent.canReloadInside && player.itemMaterialInMainHandEqualTo(fuelComponent.material) ) {
                        xv.fuelRequests.add(FuelVehicleRequest(
                            fuelComponent = fuelComponent,
                            player = player,
                        ))
                        break
                    }
                }

                if ( playerElement.layout.contains(VehicleComponentType.AMMO) ) {
                    val ammoComponent = playerElement.components.ammo!!
                    if ( ammoComponent.canReloadInside && player.itemMaterialInMainHandEqualTo(xv.xc.config.materialAmmo) ) {
                        xv.ammoLoadRequests.add(AmmoLoadRequest(
                            ammoComponent = ammoComponent,
                            player = player,
                            isInside = true,
                        ))
                        break
                    }
                }

                // fueling or ammo load did not pass, try mounting
                // may not want this since it might block raycasts? idk...
                if ( targetElement !== null ) {
                    if ( targetElement.layout.contains(VehicleComponentType.SEATS) ) {
                        xv.mountRequests.add(MountVehicleRequest(
                            player = player,
                            vehicle = targetVehicle!!,
                            element = targetElement,
                            componentType = targetComponentType!!,
                        ))
                    }
                }
            } else {
                // should never occur, left for future
            }
        } catch ( err: Exception ) {
            err.printStackTrace()
        }
    }
}
