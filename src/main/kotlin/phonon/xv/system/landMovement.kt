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
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.iter.*
import phonon.xv.common.UserInput
import phonon.xv.component.LandMovementControlsComponent
import phonon.xv.component.SeatsComponent
import phonon.xv.component.TransformComponent

/**
 * System for land movement controls
 */
public fun systemLandMovement(
    storage: ComponentsStorage,
    userInputs: Map<UUID, UserInput>,
) {
    for ( (_, transform, seats, landMovement) in ComponentTuple3.query<
        TransformComponent,
        SeatsComponent,
        LandMovementControlsComponent,
    >(storage) ) {
        // clear transform dirty flags
        transform.positionDirty = false
        transform.yawDirty = false

        // update speed/turning from player input
        val player = seats.passengers[landMovement.seatController]
        if ( player !== null ) {
            // get user input
            val controls = userInputs[player.getUniqueId()] ?: UserInput()

            // DRIVING FORWARD/BACKWARD CONTROLS
            var controllingSpeed = false // flag that user is controlling speed
            val newSpeed = if ( controls.forward ) {
                controllingSpeed = true
                val s = if ( landMovement.speed < 0 ) {
                    // apply deceleration first if inversing current motion
                    landMovement.speed * landMovement.decelerationMultiplier
                } else {
                    landMovement.speed
                }
                s + landMovement.acceleration
            } else if ( controls.backward ) {
                controllingSpeed = true
                val s = if ( landMovement.speed > 0 ) {
                    // apply deceleration first if inversing current motion
                    landMovement.speed * landMovement.decelerationMultiplier
                } else {
                    landMovement.speed
                }
                s - landMovement.acceleration
            } else {
                // no controls -> decelerate
                var s = landMovement.speed * landMovement.decelerationMultiplier
                if ( s < 0.1 && s > -0.1) { // clamp to 0
                    s = 0.0
                }
                s
            }
            
            if ( controllingSpeed ) {
                landMovement.speed = newSpeed.coerceIn(-landMovement.speedMaxReverse, landMovement.speedMaxForward)
            } else {
                landMovement.speed = newSpeed
            }

            // TURNING ROTATION CONTROLS
            var controllingYaw = false
            val newYawSpeed = if ( controls.right ) {
                controllingYaw = true
                val r = if ( landMovement.yawRotationSpeed < 0 ) {
                    // apply deceleration first if inversing current motion
                    landMovement.yawRotationSpeed * landMovement.yawRotationDecelerationMultiplier
                } else {
                    landMovement.yawRotationSpeed
                }
                r + landMovement.yawRotationAcceleration
            } else if ( controls.left ) {
                controllingYaw = true
                val r = if ( landMovement.yawRotationSpeed > 0 ) {
                    // apply deceleration first if inversing current motion
                    landMovement.yawRotationSpeed * landMovement.yawRotationDecelerationMultiplier
                } else {
                    landMovement.yawRotationSpeed
                }
                r - landMovement.yawRotationAcceleration
            } else {
                // no controls -> decelerate
                var r = landMovement.yawRotationSpeed * landMovement.yawRotationDecelerationMultiplier
                if ( r < 0.1 && r > -0.1) { // clamp to 0
                    r = 0.0
                }
                r
            }

            if ( controllingYaw ) {
                landMovement.yawRotationSpeed = newYawSpeed.coerceIn(-landMovement.yawRotationSpeedMax, landMovement.yawRotationSpeedMax)
            } else {
                landMovement.yawRotationSpeed = newYawSpeed
            }

        } else {
            // no player controls -> decelerate
            landMovement.speed *= landMovement.decelerationMultiplier
            if ( landMovement.speed < 0.1 && landMovement.speed > -0.1) { // clamp to 0
                landMovement.speed = 0.0
            }
            landMovement.yawRotationSpeed *= landMovement.yawRotationDecelerationMultiplier
            if ( landMovement.yawRotationSpeed < 0.1 && landMovement.yawRotationSpeed > -0.1) { // clamp to 0
                landMovement.yawRotationSpeed = 0.0
            }
        }

        // APPLY TURNING/ROTATION MOTION
        // NOTE: make sure this happens before translational motion
        // because translation depends on forward vector that
        // needs to be updated from rotation
        if ( landMovement.yawRotationSpeed != 0.0 ) {
            var newYaw = transform.yaw + landMovement.yawRotationSpeed
            
            // constrain yaw angle to [0, 360]
            newYaw = if ( newYaw >= 360.0 ) {
                newYaw - 360.0
            } else if ( newYaw < 0.0 ) {
                newYaw + 360.0
            } else {
                newYaw
            }

            val yawRad = Math.toRadians(newYaw)

            transform.yaw = newYaw
            transform.yawf = newYaw.toFloat()
            transform.yawRad = yawRad
            transform.yawSin = Math.sin(yawRad)
            transform.yawCos = Math.cos(yawRad)
            transform.yawDirty = true
        }

        // TRANSLATIONAL MOTION
        val world = transform.world
        if ( world !== null ) {
            var positionChanged = false
            val xCurr = transform.x
            val yCurr = transform.y
            val zCurr = transform.z

            // center block of body
            val blx = floor(xCurr).toInt()
            val bly = floor(yCurr).toInt()
            val blz = floor(zCurr).toInt()

            // new position values
            var xNew = xCurr
            var yNew = yCurr
            var zNew = zCurr

            // ============================================
            // APPLY GRAVITY
            // ============================================
            // TODO: have flag to do either simple gravity (center point only)
            // or use this more expensive 5-point contact gravity
            // 
            // 5-point gravity:
            // center + 4x diagonal cross contact points
            // (2 at front, 2 at back)
            val blyBelow = bly - 1
            // get y value of each contact point
            val yCenter = if ( world.getBlockAt(blx, blyBelow, blz).isPassable() ) blyBelow else bly
            val yFront0 = getContactPointBlockY(world, bly, xCurr, zCurr, transform.yawSin, transform.yawCos, 2, landMovement.contactPoints[0], landMovement.contactPoints[1], landMovement.contactPoints[2])
            val yFront1 = getContactPointBlockY(world, bly, xCurr, zCurr, transform.yawSin, transform.yawCos, 2, landMovement.contactPoints[3], landMovement.contactPoints[4], landMovement.contactPoints[5])
            val yRear0 = getContactPointBlockY(world, bly, xCurr, zCurr, transform.yawSin, transform.yawCos, 2, landMovement.contactPoints[6], landMovement.contactPoints[7], landMovement.contactPoints[8])
            val yRear1 = getContactPointBlockY(world, bly, xCurr, zCurr, transform.yawSin, transform.yawCos, 2, landMovement.contactPoints[9], landMovement.contactPoints[10], landMovement.contactPoints[11])



            visualizePoints(world, xCurr, yCurr, zCurr, transform.yawSin, transform.yawCos, landMovement.contactPoints)

            if ( yCenter < bly && yFront0 < bly && yFront1 < bly && yRear0 < bly && yRear1 < bly ) {
                yNew = blyBelow.toDouble() // does at most -1 block/tick
                positionChanged = true
            }

            // ALTERNATIVE: simplest 5-point cross-shaped gravity check (5 blocks around center block)
            // val blyBelow = bly - 1
            // val blockBelow = world.getBlockAt(blx, blyBelow, blz)

            // if ( blockBelow.isPassable() &&
            //     world.getBlockAt(blx + 1, blyBelow, blz).isPassable() &&
            //     world.getBlockAt(blx - 1, blyBelow, blz).isPassable() &&
            //     world.getBlockAt(blx, blyBelow, blz + 1).isPassable() &&
            //     world.getBlockAt(blx, blyBelow, blz - 1).isPassable()
            // ) { // fall down
            //     locBody.y = blockBelow.y.toDouble()
            // }

            // ============================================
            // APPLY FORWARD/BACKWARD CONTROLLED TRANSLATIONAL MOTION
            // ============================================
            if ( landMovement.speed != 0.0 ) {
                // next position from forward vector
                val dx = -transform.yawSin * landMovement.speed
                val dz = transform.yawCos * landMovement.speed
                val xNext = xCurr + dx
                val zNext = zCurr + dz
                val yNext = yNew

                // only move to (nextX, nextZ) if location is passable:
                // check if block above next block is not solid
                val bxNew = floor(xNext).toInt()
                val byNew = floor(yNext).toInt()
                val bzNew = floor(zNext).toInt()
                val blNew = world.getBlockAt(bxNew, byNew, bzNew)
                if ( blNew.isPassable() ) {
                    xNew = xNext
                    yNew = byNew.toDouble()
                    zNew = zNext
                    positionChanged = true
                } else {
                    // try block above next block
                    val blNewAbove = world.getBlockAt(bxNew, byNew + 1, bzNew)
                    if ( blNewAbove.isPassable() ) {
                        xNew = xNext
                        yNew = (byNew + 1).toDouble()
                        zNew = zNext
                        positionChanged = true
                    }
                }
            }

            // UPDATE TRANSFORM POSITION
            if ( positionChanged ) {
                transform.x = xNew
                transform.y = yNew
                transform.z = zNew
                transform.positionDirty = true
                
                // mark transform flag if current block + above in water
                val blxNew = floor(xNew).toInt()
                val blyNew = floor(yNew).toInt()
                val blzNew = floor(zNew).toInt()
                if ( world.getBlockAt(blxNew, blyNew, blzNew).type == Material.WATER &&
                    world.getBlockAt(blxNew, blyNew + 1, blzNew).type == Material.WATER
                ) {
                    transform.inWater = true
                } else {
                    transform.inWater = false
                }
            }
        }
    }
}


/**
 * This calculates the y-level of the next passable block
 * underneath a "contact point" offset from the center point
 * by a yaw rotation. Determines:
 * 1. Whether vehicle should fall down (all contact points below
 *    current y level)
 * 2. (Optional, TODO) body rotation, to roughly match terrain.
 *    This can be estimated by getting angle of plane fitted to
 *    contact points
 */
private fun getContactPointBlockY(
    world: World,
    bly: Int,
    currX: Double,
    currZ: Double,
    yawSin: Double, // pre-computed cos(yaw)
    yawCos: Double, // pre-computed sin(yaw)
    maxDepthBelow: Int,
    cx: Double, // contact points are in local, pre-transformed space
    _cy: Double,
    cz: Double,
): Int {
    val blx = floor(currX + yawCos * cx - yawSin * cz).toInt()
    val blz = floor(currZ + yawSin * cx + yawCos * cz).toInt()
    
    // iterate in reverse: starts from lowest y-level in maxDepthBelow
    // range, finds first passable y-level and immediately returns 
    for ( i in maxDepthBelow downTo 1 ) {
        val blyBelow = bly - i
        if ( world.getBlockAt(blx, blyBelow, blz).isPassable() ) {
            return blyBelow
        }
    }

    return bly
}