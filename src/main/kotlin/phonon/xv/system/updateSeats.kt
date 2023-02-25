package phonon.xv.system

import java.util.UUID
import kotlin.math.max
import org.bukkit.attribute.Attribute
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import phonon.xv.XV
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.iter.*
import phonon.xv.common.UserInput
import phonon.xv.component.HealthComponent
import phonon.xv.component.SeatsComponent
import phonon.xv.component.TransformComponent

/**
 * System for updating seats if seats are dismounted, clear player
 * and reset player user input.
 */
public fun XV.systemUpdateSeats(
    storage: ComponentsStorage,
    userInputs: HashMap<UUID, UserInput>,
) {
    val xv = this

    for ( (el, transform, seats) in ComponentTuple2.query<
        TransformComponent,
        SeatsComponent,
    >(storage) ) {
        try {
            for ( i in 0 until seats.count ) {
                val armorstand = seats.armorstands[i]
                val player = seats.passengers[i]

                // debug
                // println("armorstand: ${armorstand?.getUniqueId()}, player: $player, player.getVehicle() ${ ?.getVehicle()?.getUniqueId()}")

                if ( player !== null ) {
                    val playerVehicle = player.getVehicle()
                    if ( playerVehicle === null || playerVehicle.getUniqueId() !== armorstand?.getUniqueId() || !player.isOnline() || player.isDead() ) {
                        if ( armorstand !== null ) {
                            xv.entityVehicleData.remove(armorstand.getUniqueId())
                            armorstand.remove()
                        }
                        seats.passengers[i] = null
                        seats.armorstands[i] = null
                        userInputs[player.getUniqueId()] = UserInput() // clear
                        continue // early exit
                    }
                }

                // update seat armorstand position
                if ( armorstand !== null && armorstand.isValid() ) {
                    val seatPos = armorstand.location
                    if (
                        transform.yawDirty ||
                        transform.x != seatPos.x ||
                        transform.y != seatPos.y ||
                        transform.z != seatPos.z
                    ) {
                        // get seat local offset
                        val offsetX = seats.offsets[i*3]
                        val offsetY = seats.offsets[i*3 + 1]
                        val offsetZ = seats.offsets[i*3 + 2]

                        // world position = transformPosition + Rotation * localPosition
                        // using only yaw (in-plane) rotation, pitch not supported by
                        // default since complicates things a lot...
                        // TODO: either make separate components that support rotations: none, yaw, yawpitch, etc...
                        // or add flags into the current model component
                        seatPos.x = transform.x + transform.yawCos * offsetX - transform.yawSin * offsetZ
                        seatPos.y = transform.y + offsetY
                        seatPos.z = transform.z + transform.yawSin * offsetX + transform.yawCos * offsetZ
                        seatPos.yaw = transform.yawf
        
                        armorstand.teleport(seatPos)
                    }
                }
            }
        } catch ( err: Exception ) {
            xv.logger.severe("Error updating seats for element ${el}: ${err}")
        }
    }
}


/**
 * System for updating seats health display bar based on vehicle element
 * health component current value.
 */
public fun XV.systemUpdateSeatHealthDisplay(
    storage: ComponentsStorage,
) {
    val xv = this
    
    for ( (el, seats, health) in ComponentTuple2.query<
        SeatsComponent,
        HealthComponent,
    >(storage) ) {
        try {
            for ( i in 0 until seats.count ) {
                val armorstand = seats.armorstands[i]
    
                // update seat armorstand health display
                if ( armorstand !== null && armorstand.isValid() ) {
                    // set max health
                    if ( seats.healthDisplayMax[i] != health.max ) {
                        seats.healthDisplayMax[i] = health.max
                        armorstand.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.setBaseValue(health.max)
                    }

                    // set current health
                    if ( seats.healthDisplay[i] != health.current ) {
                        seats.healthDisplay[i] = health.current
                        // must clamp to 1.0 as min, otherwise error because armorstand appears like it should die
                        armorstand.setHealth(health.current.coerceIn(1.0, health.max))
                    }
                } else {
                    seats.healthDisplayMax[i] = -1.0
                    seats.healthDisplay[i] = -1.0
                }
            }
        } catch ( err: Exception ) {
            xv.logger.severe("Error updating seat health display for element ${el}: ${err}")
        }
    }
}

