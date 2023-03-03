package phonon.xv.system

import java.util.Queue
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlin.math.min
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import phonon.xv.XV
import phonon.xv.core.Vehicle
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.VehiclePrototype
import phonon.xv.component.FuelComponent
import phonon.xv.util.TaskProgress
import phonon.xv.util.progressBar10
import phonon.xv.util.Message
import phonon.xv.util.drain
import phonon.xv.util.item.addIntId
import phonon.xv.util.item.itemMaterialInMainHandEqualTo

/**
 * A request to add fuel to a vehicle.
 */
public data class FuelVehicleRequest(
    val fuelComponent: FuelComponent,
    val player: Player? = null,
    // if true, skip spawn timer 
    val skipTimer: Boolean = false,
)

/**
 * Signal to finish adding fuel to vehicle.
 */
public data class FuelVehicleFinish(
    val fuelComponent: FuelComponent,
    val player: Player? = null,
)


/**
 * Handle vehicle fuel requests, from player vehicle item or command.
 */
public fun XV.systemFuelVehicle(
    requests: Queue<FuelVehicleRequest>,
    finishQueue: ConcurrentLinkedQueue<FuelVehicleFinish>,
) {
    val xv = this
    val infoMessage = xv.infoMessage

    for ( request in requests.drain() ) {
        val (
            fuelComponent,
            player,
            skipTimer,
        ) = request

        try {
            if ( player !== null ) {
                if ( xv.isPlayerRunningTask(player) ) {
                    continue // player already running task, skip
                }

                if ( fuelComponent.current >= fuelComponent.max ) {
                    infoMessage.put(player, 2, "${ChatColor.RED}Vehicle fuel is already full!")
                    continue
                }

                if ( fuelComponent.timeRefuel > 0.0 && !skipTimer ) {
                    val task = TaskProgress(
                        timeTaskMillis = fuelComponent.timeRefuel,
                        player = player,
                        itemMaterial = fuelComponent.material,
                        maxMoveDistance = 0.5, // TODO: make configurable
                        onProgress = { progress ->
                            infoMessage.put(player, 2, progressBar10(progress))
                        },
                        onCancel = { reason ->
                            when ( reason ) {
                                TaskProgress.CancelReason.ITEM_CHANGED -> infoMessage.put(player, 2, "${ChatColor.RED}Item changed, fueling cancelled!")
                                TaskProgress.CancelReason.MOVED -> infoMessage.put(player, 2, "${ChatColor.RED}Moved too far, fueling cancelled!")
                                else -> {}
                            }
                        },
                        onFinish = {
                            finishQueue.add(FuelVehicleFinish(
                                fuelComponent,
                                player,
                            ))
                        },
                    )

                    xv.startTaskForPlayer(player, task)
                } else { // no spawn, go directly to finish
                    finishQueue.add(FuelVehicleFinish(
                        fuelComponent,
                        player,
                    ))
                }
            }
        } catch ( err: Exception ) {
            err.printStackTrace()
        }
    }
}

/**
 * Handle vehicle finish fuel requests, from player vehicle item or command.
 */
public fun XV.systemFinishFuelVehicle(
    requests: ConcurrentLinkedQueue<FuelVehicleFinish>,
) {
    val xv = this

    for ( request in requests.drain() ) {
        val (
            fuelComponent,
            player,
        ) = request

        try {
            // do final check that vehicle item matches item in player hand,
            // then consume vehicle item
            if ( player !== null ) {
                val itemInHand = player.inventory.itemInMainHand
                if ( itemInHand.type != fuelComponent.material ) {
                    Message.announcement(player, "${ChatColor.RED}Item changed, fueling cancelled!")
                    return
                }

                // calculate how many items needed to refuel to max
                var itemsUsed = max(1, (fuelComponent.max - fuelComponent.current) / fuelComponent.amountPerItem)
                // clamp by max allowed per refuel
                itemsUsed = min(itemsUsed, fuelComponent.maxAmountPerRefuel)
                // clamp by amount actually in player hand
                itemsUsed = min(itemsUsed, itemInHand.amount)

                // else, passed check, consume item
                if ( itemsUsed >= itemInHand.amount ) {
                    player.inventory.setItemInMainHand(null)
                } else {
                    itemInHand.amount -= itemsUsed
                    player.inventory.setItemInMainHand(itemInHand)
                }

                // set fuel based on amount used
                fuelComponent.current = min(fuelComponent.max, fuelComponent.current + fuelComponent.amountPerItem * itemsUsed)
                
                Message.announcement(player, "Fueled vehicle: ${fuelComponent.current}/${fuelComponent.max}")
            }
            else {
                // just fill to max for now, todo: configurable by command
                fuelComponent.current = fuelComponent.max
            }
        } catch ( err: Exception ) {
            err.printStackTrace()
        }
    }
}
