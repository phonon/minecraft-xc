/**
 * System for teleporting vehicle.
 */

package phonon.xv.system

import java.util.Queue
import kotlin.math.floor
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.block.Block
import phonon.xv.XV
import phonon.xv.core.Vehicle
import phonon.xv.component.SeatsComponent
import phonon.xv.component.TransformComponent
import phonon.xv.util.drain

public data class TeleportVehicleRequest(
    val vehicle: Vehicle,
    val x: Double,
    val y: Double,
    val z: Double,
    // by default yaw/pitch will be unaffected
    val yaw: Double? = null,
    val pitch: Double? = null,
)

/**
 * System for teleporting vehicles.
 */
public fun XV.systemTeleport(
    requests: Queue<TeleportVehicleRequest>,
) {
    val xv = this

    for ( request in requests.drain() ) {
        val (
            vehicle,
            x,
            y,
            z,
            yaw,
            pitch,
        ) = request

        try {
            // find first transform component in vehicle elements
            // (since elements sorted, this will be the "root" transform)
            var transform: TransformComponent? = null
            for ( element in vehicle.elements ) {
                transform = element.components.transform
                if ( transform !== null ) {
                    break
                }
            }

            if ( transform === null ) {
                if ( xv.debug ) {
                    xv.logger.severe("Cannot teleport ${vehicle.name} (id: ${vehicle.id}), no element with transform component")
                }
                continue
            }

            transform.x = x
            transform.y = y
            transform.z = z
            transform.positionDirty = true

            if ( yaw !== null ) {
                transform.yaw = yaw
                transform.yawDirty = true
            }

            if ( pitch !== null ) {
                transform.pitch = pitch
                transform.pitchDirty = true
            }

            // TODO:
            // check if target location chunk loaded, if not we need to 
            // load chunk and send packets to players to force show the
            // vehicle armorstand entities
        }
        catch ( err: Exception ) {
            if ( xv.debug ) {
                err.printStackTrace()
                xv.logger.severe("Failed to teleport vehicle: ${vehicle.name} (id: ${vehicle.id})")
            }
        }
    }
}