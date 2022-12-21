/**
 * Controller for WASD gun barrel component.
 */

package phonon.xv.system

import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.max
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.PI
import org.bukkit.World
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.util.EulerAngle
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.iter.*
import phonon.xv.common.UserInput
import phonon.xv.component.GunBarrelComponent
import phonon.xv.component.ModelComponent
import phonon.xv.component.SeatsComponent
import phonon.xv.component.TransformComponent

/**
 * System for controlling a single gun barrel component. Directly
 * updates just the barrel yaw/pitch values. For vehicles that only use
 * a single armorstand for a "gun barrel" component, without any 
 * additional base part. E.g. a mortar weapon.
 */
public fun systemSingleGunBarrelControls(
    storage: ComponentsStorage,
    userInputs: Map<UUID, UserInput>,
) {
    for ( (_, transform, seats, gunBarrel) in ComponentTuple3.query<
        TransformComponent,
        SeatsComponent,
        GunBarrelComponent,
    >(storage) ) {

        // local dirty flags
        var dirtyYaw = false
        var dirtyPitch = false

        // yaw, pitch to be updated
        var newYaw = gunBarrel.yaw
        var newPitch = gunBarrel.pitch

        // update barrel yaw/pitch from player input
        val player = seats.passengers[gunBarrel.seatController]
        if ( player !== null ) {
            // get user input
            val controls = userInputs[player.getUniqueId()] ?: UserInput()
            
            // yaw controls:
            newYaw = if ( gunBarrel.controlYawWasd ) {
                // WASD: rotate barrel yaw with A/D keys (left/right keys)
                if ( controls.right ) {
                    dirtyYaw = true
                    gunBarrel.yaw + gunBarrel.yawRotationSpeed
                } else if ( controls.left ) {
                    dirtyYaw = true
                    gunBarrel.yaw - gunBarrel.yawRotationSpeed
                } else {
                    gunBarrel.yaw
                }
            } else if ( gunBarrel.controlYawMouse ) {
                // Mouse: rotate barrel yaw to match player's yaw
                dirtyYaw = true
                player.location.yaw.toDouble()
            } else {
                // TODO: arc clamping + rotation speed
                gunBarrel.yaw
            }

            // pitch controls (NOTE: in mineman, negative is towards sky):
            newPitch = if ( gunBarrel.controlPitchWasd ) {
                // WASD: rotate barrel pitch with W/S keys (up/down keys)
                if ( controls.forward ) {
                    dirtyPitch = true
                    max(-gunBarrel.barrelPitchMax, gunBarrel.pitch - gunBarrel.pitchRotationSpeed)
                } else if ( controls.backward ) {
                    dirtyPitch = true
                    min(-gunBarrel.barrelPitchMin, gunBarrel.pitch + gunBarrel.pitchRotationSpeed)
                } else {
                    gunBarrel.pitch
                }
            } else if ( gunBarrel.controlPitchMouse ) {
                // Mouse: rotate barrel pitch to match player's pitch
                dirtyPitch = true
                // TODO: clamping + rotation speed
                player.location.pitch.toDouble()
            } else {
                gunBarrel.pitch
            }

        }

        // update local position and rotation if updated here or if transform is dirty
        val dirtyYawOrPitch = dirtyYaw || dirtyPitch
        val needsUpdate = dirtyYawOrPitch || transform.positionDirty

        if ( needsUpdate ) {
            // update local transform
            if ( dirtyYawOrPitch ) {
                gunBarrel.yaw = newYaw
                gunBarrel.yawf = newYaw.toFloat()
                gunBarrel.yawRad = Math.toRadians(newYaw)
                gunBarrel.yawSin = Math.sin(gunBarrel.yawRad)
                gunBarrel.yawCos = Math.cos(gunBarrel.yawRad)
                gunBarrel.pitch = newPitch
                gunBarrel.pitchf = newPitch.toFloat()
                gunBarrel.pitchRad = Math.toRadians(newPitch)
                gunBarrel.pitchSin = Math.sin(gunBarrel.pitchRad)
                gunBarrel.pitchCos = Math.cos(gunBarrel.pitchRad)

                // optionally update base transform
                if ( gunBarrel.updateTransform ) {
                    transform.updateYaw(newYaw)
                }
            }

            // update armorstand model
            val armorstand = gunBarrel.armorstand
            if ( armorstand != null && armorstand.isValid() ) {
                val modelPos = armorstand.location
                if (
                    dirtyYawOrPitch ||
                    transform.x != modelPos.x ||
                    transform.y != modelPos.y ||
                    transform.z != modelPos.z
                ) {
                    // world position = transformPosition + Rotation * localPosition
                    // but yaw, pitch are ONLY LOCAL yaw, pitch, NOT COMPOSED with transform
                    modelPos.x = transform.x + transform.yawCos * gunBarrel.barrelX - transform.yawSin * gunBarrel.barrelZ
                    modelPos.y = transform.y + gunBarrel.barrelY
                    modelPos.z = transform.z + transform.yawSin * gunBarrel.barrelX + transform.yawCos * gunBarrel.barrelZ
                    armorstand.setHeadPose(EulerAngle(
                        gunBarrel.pitchRad,
                        gunBarrel.yawRad,
                        0.0
                    ))
                    armorstand.teleport(modelPos)
                }
            }
        }
    }
}


/**
 * System for controlling a gun barrel component that also has an 
 * additional model base "body". The barrel is treated as a movable turret
 * on this "body" model. E.g. a medieval cannon or a tank turret.
 */
public fun systemGunBarrelWithBaseControls(
    storage: ComponentsStorage,
    userInputs: Map<UUID, UserInput>,
) {
    for ( (_, transform, seats, body, gunBarrel) in ComponentTuple4.query<
        TransformComponent,
        SeatsComponent,
        ModelComponent,
        GunBarrelComponent,
    >(storage) ) {
        
    }
}