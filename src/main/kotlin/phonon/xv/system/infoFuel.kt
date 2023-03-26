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
import phonon.xv.util.ConcurrentPlayerInfoMessageMap
import phonon.xv.util.progressBar10Green
import phonon.xv.util.progressBar10Red

/**
 * System for sending vehicle info text to player driving a land vehicle.
 */
public fun systemLandVehicleFuelInfoText(
    storage: ComponentsStorage,
    infoMessage: ConcurrentPlayerInfoMessageMap,
) {
    for ( (_, landMovement, seats, fuel) in ComponentTuple3.query<
        LandMovementControlsComponent,
        SeatsComponent,
        FuelComponent,
    >(storage) ) {
        if ( landMovement.infoTick <= 0 ) {
            landMovement.infoTick = 2
            val player = seats.passengers[landMovement.seatController]
            if ( player !== null && !infoMessage.contains(player) ) {
                // NOTE: the infoMessage.contains check is not really synchronized
                // since infoMessage is concurrent...oh well rip!

                val fuelPercent = fuel.current.toDouble() / fuel.max.toDouble()
                val fuelText = if ( fuel.current == 0 ) {
                    val fuelBar = progressBar10Red(0.0)
                    "${ChatColor.RED}Fuel: ${fuelBar} ${fuel.current}/${fuel.max}"
                } else {
                    val fuelBar = progressBar10Green(fuelPercent)
                    "${ChatColor.GREEN}Fuel: ${fuelBar} ${fuel.current}/${fuel.max}"
                }
                
                // //// printing fuel tick percent, for debugging
                // val fuelTickPercent = fuel.timeRemaining.toDouble() / fuel.timePerFuelWhenIdle.toDouble()
                // val fuelText = if ( fuel.current == 0 ) {
                //     val fuelBar = progressBar10Red(0.0)
                //     "Fuel Tick: ${fuelBar} ${fuel.current}/${fuel.max}"
                // } else {
                //     val fuelBar = progressBar10Green(fuelTickPercent)
                //     "Fuel Tick: ${fuelBar} ${fuel.current}/${fuel.max}"
                // }

                val text = "${fuelText}"
                infoMessage.put(player, 0, text)
            } else {
                landMovement.infoTick = 20
            }
        } else {
            landMovement.infoTick -= 1
        }
    }
}