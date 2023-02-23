/**
 * Handle systems for sending and printing vehicle info text to player
 * driving the vehicle
 */

package phonon.xv.system


import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.max
import kotlin.math.floor
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.iter.*
import phonon.xv.component.FuelComponent
import phonon.xv.component.LandMovementControlsComponent
import phonon.xv.component.SeatsComponent
import phonon.xv.util.Message
import phonon.xv.util.progressBar10Green
import phonon.xv.util.progressBar10Red

/**
 * System for sending vehicle info text to player driving a land vehicle.
 */
public fun systemLandVehicleInfoText(
    storage: ComponentsStorage,
) {
    for ( (_, landMovement, seats, fuel) in ComponentTuple3.query<
        LandMovementControlsComponent,
        SeatsComponent,
        FuelComponent,
    >(storage) ) {
        if ( landMovement.infoTick >= 2 ) {
            landMovement.infoTick = 0

            val player = seats.passengers[landMovement.seatController]
            if ( player !== null ) {
                // val fuelPercent = fuel.current.toDouble() / fuel.max.toDouble()
                // val fuelText = if ( fuel.current == 0 ) {
                //     val fuelBar = progressBar10Red(0.0)
                //     "${fuelBar} ${fuel.current}/${fuel.max}"
                // } else {
                //     val fuelBar = progressBar10Green(fuelPercent)
                //     "${fuelBar} ${fuel.current}/${fuel.max}"
                // }
                
                //// printing fuel tick percent, for debugging
                val fuelTickPercent = fuel.timeRemaining.toDouble() / fuel.timePerFuelWhenIdle.toDouble()
                val fuelText = if ( fuel.current == 0 ) {
                    val fuelBar = progressBar10Red(0.0)
                    "${fuelBar} ${fuel.current}/${fuel.max}"
                } else {
                    val fuelBar = progressBar10Green(fuelTickPercent)
                    "${fuelBar} ${fuel.current}/${fuel.max}"
                }

                val text = "${fuelText}"
                Message.announcement(player, text)
            }
        } else {
            landMovement.infoTick += 1
        }
    }
}