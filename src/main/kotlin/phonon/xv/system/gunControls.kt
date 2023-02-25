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
import phonon.xv.XV
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.iter.*
import phonon.xv.common.ControlStyle
import phonon.xv.common.UserInput
import phonon.xv.component.GunBarrelComponent
import phonon.xv.component.GunTurretComponent
import phonon.xv.component.ModelComponent
import phonon.xv.component.SeatsComponent
import phonon.xv.component.TransformComponent

/**
 * System for controlling a single gun barrel component. Directly
 * updates just the barrel yaw/pitch values. For vehicles that only use
 * a single armorstand for a "gun barrel" component, without any 
 * additional base part. E.g. a mortar weapon.
 */
public fun XV.systemGunBarrelControls(
    storage: ComponentsStorage,
    userInputs: Map<UUID, UserInput>,
) {
    val xv = this

    for ( (el, transform, seats, gunBarrel) in ComponentTuple3.query<
        TransformComponent,
        SeatsComponent,
        GunBarrelComponent,
    >(storage) ) {
        try {
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
                newYaw = when ( gunBarrel.controlYaw ) {
                    ControlStyle.WASD -> {
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
                    }

                    ControlStyle.MOUSE -> {
                        // Mouse: rotate barrel yaw to match player's yaw
                        dirtyYaw = true
                        player.location.yaw.toDouble()
                    }
                    
                    ControlStyle.NONE -> {
                        gunBarrel.yaw
                    }
                }

                // pitch controls (NOTE: in mineman, negative is towards sky):
                newPitch = when ( gunBarrel.controlPitch ) {
                    ControlStyle.WASD -> {
                        // WASD: rotate barrel pitch with W/S keys (up/down keys)
                        if ( controls.forward ) {
                            dirtyPitch = true
                            max(-gunBarrel.pitchMax, gunBarrel.pitch - gunBarrel.pitchRotationSpeed)
                        } else if ( controls.backward ) {
                            dirtyPitch = true
                            min(-gunBarrel.pitchMin, gunBarrel.pitch + gunBarrel.pitchRotationSpeed)
                        } else {
                            gunBarrel.pitch
                        }
                    }

                    ControlStyle.MOUSE -> {
                        // Mouse: rotate barrel pitch to match player's pitch
                        dirtyPitch = true
                        player.location.pitch.toDouble().coerceIn(-gunBarrel.pitchMax, -gunBarrel.pitchMin)
                    }
                    
                    ControlStyle.NONE -> {
                        gunBarrel.pitch
                    }
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
                    if ( dirtyYawOrPitch || transform.positionDirty ) {
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
        catch ( err: Exception ) {
            if ( xv.debug ) {
                err.printStackTrace()
                xv.logger.severe("Failed to update gun barrel controls for element ${el}")
            }
        }
    }
}


/**
 * System for controlling a gun turret component. This consists of a 
 * in-plane rotating turret base (yaw only) and a full rotating gun
 * barrel (yaw + pitch).
 * E.g. used for a medieval cannon, maxim machine gun, or a tank turret.
 */
public fun XV.systemGunTurretControls(
    storage: ComponentsStorage,
    userInputs: Map<UUID, UserInput>,
) {
    val xv = this

    for ( (el, transform, seats, gun) in ComponentTuple3.query<
        TransformComponent,
        SeatsComponent,
        GunTurretComponent,
    >(storage) ) {
        try {
            // local dirty flags
            var dirtyTurretYaw = false
            var dirtyBarrelYaw = false
            var dirtyBarrelPitch = false

            // yaw, pitch to be updated
            var newTurretYaw = gun.turretYaw
            var newBarrelYaw = gun.barrelYaw
            var newBarrelPitch = gun.barrelPitch

            // update barrel yaw/pitch from player input
            val player = seats.passengers[gun.seatController]
            if ( player !== null ) {
                // get user input
                val controls = userInputs[player.getUniqueId()] ?: UserInput()
                
                // turret yaw controls:
                newTurretYaw = when ( gun.turretControlYaw ) {
                    ControlStyle.WASD -> {
                        // WASD: rotate barrel yaw with A/D keys (left/right keys)
                        if ( controls.right ) {
                            dirtyTurretYaw = true
                            gun.turretYaw + gun.turretYawRotationSpeed
                        } else if ( controls.left ) {
                            dirtyTurretYaw = true
                            gun.turretYaw - gun.turretYawRotationSpeed
                        } else {
                            gun.turretYaw
                        }
                    }

                    ControlStyle.MOUSE -> {
                        // Mouse: rotate barrel yaw to match player's yaw
                        // TODO: half arc constraint
                        dirtyTurretYaw = true
                        player.location.yaw.toDouble()
                    }
                    
                    ControlStyle.NONE -> {
                        gun.turretYaw
                    }
                }

                // barrel yaw controls:
                newBarrelYaw = when ( gun.barrelControlYaw ) {
                    ControlStyle.WASD -> {
                        // WASD: rotate barrel yaw with A/D keys (left/right keys)
                        if ( controls.right ) {
                            dirtyBarrelYaw = true
                            gun.barrelYaw + gun.barrelYawRotationSpeed
                        } else if ( controls.left ) {
                            dirtyBarrelYaw = true
                            gun.barrelYaw - gun.barrelYawRotationSpeed
                        } else {
                            gun.barrelYaw
                        }
                    }

                    ControlStyle.MOUSE -> {
                        // Mouse: rotate barrel yaw to match player's yaw
                        // TODO: half arc constraint
                        dirtyBarrelYaw = true
                        player.location.yaw.toDouble()
                    }
                    
                    ControlStyle.NONE -> {
                        gun.barrelYaw
                    }
                }

                // barrel pitch controls (NOTE: in mineman, negative is towards sky):
                newBarrelPitch = when ( gun.barrelControlPitch ) {
                    ControlStyle.WASD -> {
                        // WASD: rotate barrel pitch with W/S keys (up/down keys)
                        if ( controls.forward ) {
                            dirtyBarrelPitch = true
                            max(-gun.barrelPitchMax, gun.barrelPitch - gun.barrelPitchRotationSpeed)
                        } else if ( controls.backward ) {
                            dirtyBarrelPitch = true
                            min(-gun.barrelPitchMin, gun.barrelPitch + gun.barrelPitchRotationSpeed)
                        } else {
                            gun.barrelPitch
                        }
                    }

                    ControlStyle.MOUSE -> {
                        // Mouse: rotate barrel pitch to match player's pitch
                        dirtyBarrelPitch = true
                        player.location.pitch.toDouble().coerceIn(-gun.barrelPitchMax, -gun.barrelPitchMin)
                    }
                    
                    ControlStyle.NONE -> {
                        gun.barrelPitch
                    }
                }
            }

            // update local position and rotation if updated here or if transform is dirty
            val dirtyBarrelYawOrPitch = dirtyBarrelYaw || dirtyBarrelPitch
            val needsUpdate = dirtyTurretYaw || dirtyBarrelYawOrPitch || transform.positionDirty

            if ( needsUpdate ) {
                // update local transform
                if ( dirtyTurretYaw ) {
                    gun.updateTurretYaw(newTurretYaw)
                    
                    // optionally update base transform
                    if ( gun.updateTransform ) {
                        transform.updateYaw(newTurretYaw)
                    }
                }
                if ( dirtyBarrelYawOrPitch ) {
                    gun.updateBarrelYaw(newBarrelYaw)
                    gun.updateBarrelPitch(newBarrelPitch)
                }

                // update turret and barrel armorstand models
                // (these are updated together because barrel is local child
                // to the turret)
                val armorstandTurret = gun.armorstandTurret
                if ( armorstandTurret != null && armorstandTurret.isValid() ) {
                    val modelPosTurret = armorstandTurret.location
                    if ( dirtyTurretYaw || transform.positionDirty ) {
                        // world position = transformPosition + Rotation * localPosition
                        // but yaw, pitch are ONLY LOCAL yaw, pitch, NOT COMPOSED with transform
                        modelPosTurret.x = transform.x + gun.turretYawCos * gun.turretX - gun.turretYawSin * gun.turretZ
                        modelPosTurret.y = transform.y + gun.turretY
                        modelPosTurret.z = transform.z + gun.turretYawSin * gun.turretX + gun.turretYawCos * gun.turretZ
                        armorstandTurret.setHeadPose(EulerAngle(
                            gun.turretPitchRad,
                            gun.turretYawRad,
                            0.0
                        ))
                        armorstandTurret.teleport(modelPosTurret)
                    }

                    // update barrel armorstand model
                    // LOCATION RELATIVE TO TURRET
                    val armorstandBarrel = gun.armorstandBarrel
                    if ( armorstandBarrel != null && armorstandBarrel.isValid() ) {
                        val modelPosBarrel = armorstandBarrel.location
                        if ( dirtyBarrelYawOrPitch || transform.positionDirty ) {
                            // world position = transformPosition + Rotation * localPosition
                            // but yaw, pitch are ONLY LOCAL yaw, pitch, NOT COMPOSED with transform
                            modelPosBarrel.x = modelPosTurret.x + gun.barrelYawCos * gun.barrelX - gun.barrelYawSin * gun.barrelZ
                            modelPosBarrel.y = modelPosTurret.y + gun.barrelY
                            modelPosBarrel.z = modelPosTurret.z + gun.barrelYawSin * gun.barrelX + gun.barrelYawCos * gun.barrelZ
                            armorstandBarrel.setHeadPose(EulerAngle(
                                gun.barrelPitchRad,
                                gun.barrelYawRad,
                                0.0
                            ))
                            armorstandBarrel.teleport(modelPosBarrel)
                        }
                    }
                }

            }
        }
        catch ( err: Exception ) {
            if ( xv.debug ) {
                err.printStackTrace()
                xv.logger.severe("Failed to update gun barrel controls for element ${el}")
            }
        }
    }
}