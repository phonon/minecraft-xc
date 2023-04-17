/**
 * System for teleporting vehicle.
 */

package phonon.xv.system

import java.util.Queue
import kotlin.math.floor
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block

// TODO: abstract out into NMS versioning module
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftEntity
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket

import phonon.xv.XV
import phonon.xv.core.Vehicle
import phonon.xv.component.SeatsComponent
import phonon.xv.component.TransformComponent
import phonon.xv.util.drain

/**
 * Request to teleport vehicle to a location. Does either a direct teleport
 * (if distance small enough or not passengers), or initiates a multi-step
 * teleport process (unmount, teleport, remount).
 */
public data class TeleportVehicleRequest(
    val vehicle: Vehicle,
    val x: Double,
    val y: Double,
    val z: Double,
    // by default yaw/pitch will be unaffected
    val yaw: Double? = null,
    val pitch: Double? = null,
    // details for teleport process
    val forceMultiStep: Boolean = false,
)

/**
 * Step 2 of multi-step teleport process.
 * Teleport vehicle to target location.
 */
internal data class TeleportStepVehicle(
    val vehicle: Vehicle,
    val transform: TransformComponent,
    val seats: SeatsComponent?,
    val x: Double,
    val y: Double,
    val z: Double,
    // by default yaw/pitch will be unaffected
    val yaw: Double? = null,
    val pitch: Double? = null,
)


/**
 * Step 3 of multi-step teleport process.
 * Remount passengers to their seats after both vehicles
 * and players are teleported.
 */
internal data class TeleportStepRemount(
    val vehicle: Vehicle,
    val seats: SeatsComponent?,
)


/**
 * System for initiating vehicle teleport.
 * 
 * Due to mineman chunk/entity loading issues, we have two issues:
 *    1. Server needs to load chunk before we teleport
 *    2. Client needs to load chunk and have armorstand entities
 *       created when we teleport.
 * 
 * So if distance is far enough (> passenger view distance), we 
 * need to do a multi-step teleport process done over 3 ticks:
 *   1. Player teleport tick t=0: Unmount players from vehicle, teleport
 *      players to target location. This will force server and player to
 *      load the target chunk.
 *   2. Vehicle teleport (t+1): Teleport vehicle armorstands to target location.
 *      Also send packets to players to force re-create armorstand 
 *      entities.
 *   3. Remount (t+2): Force remount player back into their original seat
 *      positions.
 */
internal fun XV.systemTeleport(
    requests: Queue<TeleportVehicleRequest>,
    teleportStepVehicleQueue: Queue<TeleportStepVehicle>,
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
            forceMultiStep,
        ) = request

        try {
            // find first transform and seats component in vehicle elements
            // (since elements sorted, this will be the "root" transform)
            var transform: TransformComponent? = null
            var seats: SeatsComponent? = null
            for ( element in vehicle.elements ) {
                transform = transform ?: element.components.transform // finds first non null
                seats = seats ?: element.components.seats // finds first non null
                if ( transform !== null && seats !== null ) {
                    break
                }
            }

            if ( transform === null ) {
                if ( xv.debug ) {
                    xv.logger.severe("Cannot teleport ${vehicle.name} (id: ${vehicle.id}), no element with transform component")
                }
                continue
            }

            // check if vehicle has passengers
            var hasPassengers = false
            if ( seats !== null ) {
                for ( passenger in seats.passengers ) {
                    if ( passenger !== null ) {
                        hasPassengers = true
                        break
                    }
                }
            }

            // get distance^2 to target
            val dx = x - transform.x
            val dy = y - transform.y
            val dz = z - transform.z
            val dist2 = dx*dx + dy*dy + dz*dz

            // for distance threshold for simple teleport, using 2 chunks (32 blocks)
            // as threshold as most view distances should never go under this...
            // but really should be configurable or input parameter
            if ( ( dist2 > 1024.0 && hasPassengers ) || forceMultiStep == true ) { 
                // begin multi-step teleport
                // unmount + teleport player -> teleport vehicle -> remount
                
                if ( seats !== null ) {
                    seats.teleporting = true

                    val destination = Location(
                        transform.world,
                        x,
                        y,
                        z,
                        yaw?.toFloat() ?: transform.yawf,
                        pitch?.toFloat() ?: transform.pitchf,
                    )

                    // unmount players
                    for ( i in 0 until seats.count ) {
                        val passenger = seats.passengers[i]
                        if ( passenger !== null ) {
                            passenger.eject()
                            seats.armorstands[i]?.removePassenger(passenger)
                            passenger.teleport(destination)
                        }
                    }
                }

                teleportStepVehicleQueue.add(TeleportStepVehicle(
                    vehicle,
                    transform,
                    seats,
                    x,
                    y,
                    z,
                    yaw,
                    pitch,
                ))
            } else {
                // < ~2 chunks distance or no passengers, just directly teleport
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
            }
        }
        catch ( err: Exception ) {
            if ( xv.debug ) {
                err.printStackTrace()
                xv.logger.severe("Failed to teleport vehicle: ${vehicle.name} (id: ${vehicle.id})")
            }
        }
    }
}

/**
 * System for teleporting vehicles.
 */
internal fun XV.systemTeleportStepVehicles(
    teleportStepVehicleQueue: Queue<TeleportStepVehicle>,
    teleportStepRemountQueue: Queue<TeleportStepRemount>,
) {
    val xv = this

    for ( request in teleportStepVehicleQueue.drain() ) {
        val (
            vehicle,
            transform,
            seats,
            x,
            y,
            z,
            yaw,
            pitch,
        ) = request

        // vehicle despawned or deleted between ticks
        if ( vehicle.valid == false ) {
            continue
        }

        try {
            if ( seats !== null ) {
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

                // re-send entity to players to make sure it exists on client
                for ( i in 0 until seats.count ) {
                    val passenger = seats.passengers[i]
                    val seatEntity = seats.armorstands[i]
                    if ( passenger !== null && seatEntity !== null ) {
                        try {
                            val nmsPlayer = (passenger as CraftPlayer).getHandle()
                            val nmsEntity = (seatEntity as CraftEntity).getHandle()
                            nmsPlayer.connection.send(ClientboundAddEntityPacket(nmsEntity))
                        } catch ( err: Exception ) {
                            if ( xv.debug ) {
                                err.printStackTrace()
                                xv.logger.severe("Failed to send vehicle entity packet to player ${passenger.name}: ${vehicle.name} (id: ${vehicle.id})")
                            }
                        }
                    }
                }

                teleportStepRemountQueue.add(TeleportStepRemount(
                    vehicle,
                    seats,
                ))
            }
        }
        catch ( err: Exception ) {
            if ( xv.debug ) {
                err.printStackTrace()
                xv.logger.severe("Failed to teleport vehicle during step 2: ${vehicle.name} (id: ${vehicle.id})")
            }
        }
    }
}


/**
 * System for teleporting vehicles.
 */
internal fun XV.systemTeleportStepRemount(
    teleportStepRemountQueue: Queue<TeleportStepRemount>,
) {
    val xv = this
    
    for ( request in teleportStepRemountQueue.drain() ) {
        val (
            vehicle,
            seats,
        ) = request

        // vehicle despawned or deleted between ticks
        if ( vehicle.valid == false ) {
            continue
        }

        if ( seats !== null ) {
            try {
                // remount players
                for ( i in 0 until seats.count ) {
                    val passenger = seats.passengers[i]
                    if ( passenger !== null ) {
                        seats.armorstands[i]?.addPassenger(passenger)
                    }
                }
                seats.teleporting = false
            }
            catch ( err: Exception ) {
                if ( xv.debug ) {
                    err.printStackTrace()
                    xv.logger.severe("Failed to finish vehicle teleport remount step 3: ${vehicle.name} (id: ${vehicle.id})")
                }
            }
        }
    }
}
