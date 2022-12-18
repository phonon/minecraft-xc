package phonon.xv.system

import java.util.UUID
import org.bukkit.entity.Player
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.iter.*
import phonon.xv.common.UserInput
import phonon.xv.component.TransformComponent
import phonon.xv.component.SeatsComponent

/**
 * System for updating seats if seats are dismounted, clear player
 * and reset player user input.
 */
public fun systemUpdateSeats(
    storage: ComponentsStorage,
    userInputs: HashMap<UUID, UserInput>,
) {
    for ( (_, transform, seats) in ComponentTuple2.query<
        TransformComponent,
        SeatsComponent,
    >(storage) ) {
        for ( i in 0 until seats.count ) {
            val armorstand = seats.armorstands[i]
            val player = seats.passengers[i]

            // debug
            // println("armorstand: ${armorstand?.getUniqueId()}, player: $player, player.getVehicle() ${ ?.getVehicle()?.getUniqueId()}")

            if ( player !== null ) {
                val playerVehicle = player.getVehicle()
                if ( playerVehicle === null || playerVehicle.getUniqueId() !== armorstand?.getUniqueId() || !player.isOnline() || player.isDead() ) {
                    armorstand?.remove()
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
    }
}

