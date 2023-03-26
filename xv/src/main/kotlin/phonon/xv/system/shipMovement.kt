package phonon.xv.system

import java.util.*
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.block.Block
import phonon.xv.XV
import phonon.xv.common.UserInput
import phonon.xv.component.SeatsComponent
import phonon.xv.component.ShipMovementControlsComponent
import phonon.xv.component.TransformComponent
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.iter.ComponentTuple3
import phonon.xv.util.Message
import kotlin.math.floor

/**
 * System for ship movement + collisions
 */
public fun XV.systemShipMovement(
    storage: ComponentsStorage,
    userInputs: Map<UUID, UserInput>,
) {
    val xv = this
    for ( (_, transform, seats, shipMovement) in ComponentTuple3.query<
        TransformComponent,
        SeatsComponent,
        ShipMovementControlsComponent
    >(storage) ) {
        // clear transform dirty flags
        transform.positionDirty = false
        transform.yawDirty = false
        transform.isMoving = false

        // calculate ground contact points first, and do gravity. We need
        // to figure out if ship can move. If it can't, speed=0 and we
        // can skip all the movement calculations.
        val world = transform.world
        if ( world === null ) continue

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
        //
        // 5-point gravity:
        // center + 4x diagonal cross contact points
        // (2 at front, 2 at back)
        val blyBelow = bly - 1
        // get y value of each contact point
        val yCenter = if ( world.getBlockAt(blx, blyBelow, blz).isPassable() ) blyBelow else bly
        val yFront0 = getHighestContactPointBlock(world, bly, xCurr, zCurr, transform.yawSin, transform.yawCos, 2, shipMovement.groundContactPoints, 0).y
        val yFront1 = getHighestContactPointBlock(world, bly, xCurr, zCurr, transform.yawSin, transform.yawCos, 2, shipMovement.groundContactPoints, 3).y
        val yRear0 = getHighestContactPointBlock(world, bly, xCurr, zCurr, transform.yawSin, transform.yawCos, 2, shipMovement.groundContactPoints, 6).y
        val yRear1 = getHighestContactPointBlock(world, bly, xCurr, zCurr, transform.yawSin, transform.yawCos, 2, shipMovement.groundContactPoints, 9).y
        // FOR TESTING
        // visualize front and ground contacts
        if ( xv.config.debugContactPoints ) {
            visualizePoints(world, xCurr, yCurr, zCurr, transform.yawSin, transform.yawCos, shipMovement.groundContactPoints)
            visualizePoints(world, xCurr, yCurr, zCurr, transform.yawSin, transform.yawCos, shipMovement.frontContactPoints)
        }
        
        if ( yCenter < bly && yFront0 < bly && yFront1 < bly && yRear0 < bly && yRear1 < bly ) {
            yNew = blyBelow.toDouble() // does at most -1 block/tick
            positionChanged = true
        }

        // we also want to allow boats to climb up
        // 1 block height changes in water
        val blCenter = getContactPointBlock(world, xCurr, yCurr, zCurr, transform.yawSin, transform.yawCos, shipMovement.groundContactPoints, 0)
        val blFront0 = getContactPointBlock(world, xCurr, yCurr, zCurr, transform.yawSin, transform.yawCos, shipMovement.groundContactPoints, 0)
        val blFront1 = getContactPointBlock(world, xCurr, yCurr, zCurr, transform.yawSin, transform.yawCos, shipMovement.groundContactPoints, 3)
        val blRear0 = getContactPointBlock(world, xCurr, yCurr, zCurr, transform.yawSin, transform.yawCos, shipMovement.groundContactPoints, 6)
        val blRear1 = getContactPointBlock(world, xCurr, yCurr, zCurr, transform.yawSin, transform.yawCos, shipMovement.groundContactPoints, 9)

        // we want our ground contact points to hover right above
        // the water that the boat is sitting on.
        // if any of the contact points is submerged, then we can move
        // up 1 y level
        if (
            blCenter.isShipTraversable()
            || blFront0.isShipTraversable()
            || blFront1.isShipTraversable()
            || blRear0.isShipTraversable()
            || blRear1.isShipTraversable()
        ) {
            yNew = (bly + 1).toDouble()
            positionChanged = true
        }

        // our ground contact points hover just above the surface that our
        // ship is moving across.
        val blBelowCenter = getContactPointBlock(world, xCurr, yCurr - 1, zCurr, transform.yawSin, transform.yawCos, shipMovement.groundContactPoints, 0)
        val blBelowFront0 = getContactPointBlock(world, xCurr, yCurr - 1, zCurr, transform.yawSin, transform.yawCos, shipMovement.groundContactPoints, 0)
        val blBelowFront1 = getContactPointBlock(world, xCurr, yCurr - 1, zCurr, transform.yawSin, transform.yawCos, shipMovement.groundContactPoints, 3)
        val blBelowRear0 = getContactPointBlock(world, xCurr, yCurr - 1, zCurr, transform.yawSin, transform.yawCos, shipMovement.groundContactPoints, 6)
        val blBelowRear1 = getContactPointBlock(world, xCurr, yCurr - 1, zCurr, transform.yawSin, transform.yawCos, shipMovement.groundContactPoints, 9)

        // check for our 2 conditions to see if ship can move
        // 1. NONE of the ship's ground contact points are touching a grounded block
        // 2. AT LEAST ONE of the ship's ground contact points is touching a
        //    ship-traversable block
        val notGrounded = (
             !blBelowCenter.isGrounded()
             && !blBelowFront0.isGrounded()
             && !blBelowFront1.isGrounded()
             && !blBelowRear0.isGrounded()
             && !blBelowRear1.isGrounded()
        )
        val isTraversable = (
            blBelowCenter.isShipTraversable()
            || blBelowFront0.isShipTraversable()
            || blBelowFront1.isShipTraversable()
            || blBelowRear0.isShipTraversable()
            || blBelowRear1.isShipTraversable()
        )

        val player = seats.passengers[shipMovement.seatController]

        if ( xv.debug ) {
            player?.sendMessage("notGrounded: ${notGrounded}\n isTraversable: ${isTraversable}")
        }

        if ( notGrounded && isTraversable ) {
            if (player !== null) {
                val controls = userInputs[player.uniqueId] ?: UserInput()

                // copied from land movement
                // DRIVING FORWARD/BACKWARD CONTROLS
                var controllingSpeed = false // flag that user is controlling speed
                val newSpeed = if (controls.forward) {
                    controllingSpeed = true
                    val s = if (shipMovement.speed < 0) {
                        // apply deceleration first if inversing current motion
                        shipMovement.speed * shipMovement.decelerationMultiplier
                    } else {
                        shipMovement.speed
                    }
                    s + shipMovement.acceleration
                } else if (controls.backward) {
                    controllingSpeed = true
                    val s = if (shipMovement.speed > 0) {
                        // apply deceleration first if inversing current motion
                        shipMovement.speed * shipMovement.decelerationMultiplier
                    } else {
                        shipMovement.speed
                    }
                    s - shipMovement.acceleration
                } else {
                    // no controls -> decelerate
                    var s = shipMovement.speed * shipMovement.decelerationMultiplier
                    if (s < 0.1 && s > -0.1) { // clamp to 0
                        s = 0.0
                    }
                    s
                }

                if (controllingSpeed) {
                    shipMovement.speed = newSpeed.coerceIn(-shipMovement.speedMaxReverse, shipMovement.speedMaxForward)
                } else {
                    shipMovement.speed = newSpeed
                }

                // copied from land movement
                // TURNING ROTATION CONTROLS
                var controllingYaw = false
                val newYawSpeed = if (controls.right) {
                    // control factor is fraction of ship's speed / effective speed required
                    // for full yaw accel to take effect
                    // effectively Math.min(1.0, ...) call, just didn't want to do unnecessary double division
                    val controlFactor = if (shipMovement.speed >= shipMovement.yawRotationEffectiveSpeed) {
                        shipMovement.speed / shipMovement.yawRotationEffectiveSpeed
                    } else {
                        1.0
                    }
                    controllingYaw = true
                    val r = if (shipMovement.yawRotationSpeed < 0) {
                        // apply deceleration first if inversing current motion
                        shipMovement.yawRotationSpeed * shipMovement.yawRotationDecelerationMultiplier
                    } else {
                        shipMovement.yawRotationSpeed
                    }
                    if (controlFactor >= 1.0) {
                        r + shipMovement.yawRotationAcceleration
                    } else {
                        r + shipMovement.yawRotationAcceleration * controlFactor
                    }
                } else if (controls.left) {
                    // same as above
                    val controlFactor = if (shipMovement.speed >= shipMovement.yawRotationEffectiveSpeed) {
                        shipMovement.speed / shipMovement.yawRotationEffectiveSpeed
                    } else {
                        1.0
                    }
                    controllingYaw = true
                    val r = if (shipMovement.yawRotationSpeed > 0) {
                        // apply deceleration first if inversing current motion
                        shipMovement.yawRotationSpeed * shipMovement.yawRotationDecelerationMultiplier
                    } else {
                        shipMovement.yawRotationSpeed
                    }
                    if (controlFactor >= 1.0) {
                        r - shipMovement.yawRotationAcceleration
                    } else {
                        r - shipMovement.yawRotationAcceleration * controlFactor
                    }
                } else {
                    // no controls -> decelerate
                    var r = shipMovement.yawRotationSpeed * shipMovement.yawRotationDecelerationMultiplier
                    if (r < 0.1 && r > -0.1) { // clamp to 0
                        r = 0.0
                    }
                    r
                }

                if (controllingYaw) {
                    shipMovement.yawRotationSpeed =
                        newYawSpeed.coerceIn(-shipMovement.yawRotationSpeedMax, shipMovement.yawRotationSpeedMax)
                } else {
                    shipMovement.yawRotationSpeed = newYawSpeed
                }

            } else {
                // no player controls -> decelerate
                shipMovement.speed *= shipMovement.decelerationMultiplier
                if (shipMovement.speed < 0.1 && shipMovement.speed > -0.1) { // clamp to 0
                    shipMovement.speed = 0.0
                }
                shipMovement.yawRotationSpeed *= shipMovement.yawRotationDecelerationMultiplier
                if (shipMovement.yawRotationSpeed < 0.1 && shipMovement.yawRotationSpeed > -0.1) { // clamp to 0
                    shipMovement.yawRotationSpeed = 0.0
                }
            }

        } else {
            var newSpeed = 0.0

            if (player !== null) {
                val controls = userInputs[player.uniqueId] ?: UserInput()
                // allow small amount of speed, so players can "unstuck" the vehicle
                if ( controls.forward ) {
                    newSpeed = shipMovement.speedGrounded
                } else if ( controls.backward ) {
                    newSpeed = -shipMovement.speedGrounded
                }
            }

            shipMovement.speed = newSpeed

            // dont allow rotation when grounded
            shipMovement.yawRotationSpeed = 0.0
        }

        // APPLY TURNING/ROTATION MOTION
        // NOTE: make sure this happens before translational motion
        // because translation depends on forward vector that
        // needs to be updated from rotation
        val newYaw = if ( shipMovement.yawRotationSpeed != 0.0 ) {
            val newYaw = transform.yaw + shipMovement.yawRotationSpeed

            // constrain yaw angle to [0, 360]
            if (newYaw >= 360.0) {
                newYaw - 360.0
            } else if (newYaw < 0.0) {
                newYaw + 360.0
            } else {
                newYaw
            }

            // at this point we differ from the land movement impl
            // rotating our boat may cause any of the front or back
            // contact points to collide. We don't want to finalize our
            // yaw changes for transform just yet because we may need to
            // undo/modify these changes based on if we collide

            // replace w/ transform.updateYaw(newYaw)
            // val yawRad = Math.toRadians(newYaw)
            // transform.yaw = newYaw
            // transform.yawf = newYaw.toFloat()
            // transform.yawRad = yawRad
            // transform.yawSin = Math.sin(yawRad)
            // transform.yawCos = Math.cos(yawRad)
            // transform.yawDirty = true
        } else {
            transform.yaw
        }
        val yawRad = Math.toRadians(newYaw)
        val yawSin = Math.sin(yawRad)
        val yawCos = Math.cos(yawRad)

        // translational motion
        // ============================================
        // APPLY FORWARD/BACKWARD CONTROLLED TRANSLATIONAL MOTION
        // ============================================
        if ( shipMovement.speed != 0.0 ) {
            transform.isMoving = true

            // next position from forward vector
            val dx = -yawSin * shipMovement.speed
            val dz = yawCos * shipMovement.speed
            val xNext = xCurr + dx
            val zNext = zCurr + dz
            var yNext = yNew
            // we do the 1 block step up check here
            // do it w/ ground contact points
            val blCenter = world.getBlockAt(xNext.toInt(), yNext.toInt() + 1, zNext.toInt())
            if ( shipMovement.speed > 0 ) {
                // if moving forward, check front 2 points and center
                // test to see if 1 block up is still traversable. if any of
                // contact points are in traversable blocks then move up 1
                val blFront0 = getContactPointBlock(world, xNext, yNext + 1, zNew, yawSin, yawCos, shipMovement.groundContactPoints, 0)
                val blFront1 = getContactPointBlock(world, xNext, yNext + 1, zNew, yawSin, yawCos, shipMovement.groundContactPoints, 1)

                if ( blCenter.isShipTraversable() || blFront0.isShipTraversable() || blFront1.isShipTraversable() )
                    yNext += 1
            } else if ( shipMovement.speed < 0 ) {
                val blRear0 = getContactPointBlock(world, xNext, yNext + 1, zNew, yawSin, yawCos, shipMovement.groundContactPoints, 0)
                val blRear1 = getContactPointBlock(world, xNext, yNext + 1, zNew, yawSin, yawCos, shipMovement.groundContactPoints, 1)

                if ( blCenter.isShipTraversable() || blRear0.isShipTraversable() || blRear1.isShipTraversable() )
                    yNext += 1
            }

            // ===========================================
            // COLLISION CHECK
            // ===========================================

            // front collision check
            val collision = if ( shipMovement.speed > 0 ) {
                val blCenter = getContactPointBlock(world, xNext, yNext, zNext, yawSin, yawCos, shipMovement.frontContactPoints, 0)
                val blTop0 = getContactPointBlock(world, xNext, yNext, zNext, yawSin, yawCos, shipMovement.frontContactPoints, 3)
                val blTop1 = getContactPointBlock(world, xNext, yNext, zNext, yawSin, yawCos, shipMovement.frontContactPoints, 6)
                val blBottom0 = getContactPointBlock(world, xNext, yNext, zNext, yawSin, yawCos, shipMovement.frontContactPoints, 9)
                val blBottom1 = getContactPointBlock(world, xNext, yNext, zNext, yawSin, yawCos, shipMovement.frontContactPoints, 12)

                blCenter.isGrounded() || blTop0.isGrounded() || blTop1.isGrounded() || blBottom0.isGrounded() || blBottom1.isGrounded()
            } else if ( shipMovement.speed < 0 ) {
                val blCenter = getContactPointBlock(world, xNext, yNext, zNext, yawSin, yawCos, shipMovement.backwardContactPoints, 0)
                val blTop0 = getContactPointBlock(world, xNext, yNext, zNext, yawSin, yawCos, shipMovement.backwardContactPoints, 3)
                val blTop1 = getContactPointBlock(world, xNext, yNext, zNext, yawSin, yawCos, shipMovement.backwardContactPoints, 6)
                val blBottom0 = getContactPointBlock(world, xNext, yNext, zNext, yawSin, yawCos, shipMovement.backwardContactPoints, 9)
                val blBottom1 = getContactPointBlock(world, xNext, yNext, zNext, yawSin, yawCos, shipMovement.backwardContactPoints, 12)

                blCenter.isGrounded() || blTop0.isGrounded() || blTop1.isGrounded() || blBottom0.isGrounded() || blBottom1.isGrounded()
            } else {
                false
            }


            if ( collision ) {
                // TODO check our collision settings to qualify this as collision
                //  play explosion, send request to health system to reduce health
                shipMovement.speed = 0.0
                shipMovement.yawRotationSpeed = 0.0
                if ( xv.debug && player !== null ) {
                    Message.print(player, "Collision detected!")
                }
            } else {
                // no collision so we can move forward
                xNew = xNext
                yNew = yNext
                zNew = zNext
                positionChanged = true
                // update yaw
                transform.updateYaw(newYaw)
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
            // water check
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

/**
 * List of ship passable block types
 */
val SHIP_TRAVERSABLE_BLOCKS = setOf(
    Material.WATER,
    Material.TALL_SEAGRASS,
    Material.SEAGRASS,
    Material.SEA_PICKLE,
    Material.KELP_PLANT,
)

/**
 * We say a block is ship traversable if a ship is supposed
 * to traverse through it, but does not fall through it
 */
public fun Block.isShipTraversable(): Boolean {
    return SHIP_TRAVERSABLE_BLOCKS.contains(this.type)
}

/**
 * We say a block is ship passable if a ship is supposed
 * to fall through it. Note that ship traversable and ship
 * passable are mutually exclusive.
 */
public fun Block.isShipPassable(): Boolean {
    return !isShipTraversable() && isPassable()
}

public fun Block.isGrounded(): Boolean {
    return !isShipTraversable() && !isShipPassable()
}

/**
 * impl copied from land movement system
 *
 * This gets the block of the next passable block
 * underneath a "contact point" offset from the center point
 * by a yaw rotation. Determines:
 * 1. Whether vehicle should fall down (all contact points below
 *    current y level)
 * 2. (Optional, TODO) body rotation, to roughly match terrain.
 *    This can be estimated by getting angle of plane fitted to
 *    contact points
 */
private fun getHighestContactPointBlock(
    world: World,
    bly: Int,
    currX: Double,
    currZ: Double,
    yawSin: Double, // pre-computed cos(yaw)
    yawCos: Double, // pre-computed sin(yaw)
    maxDepthBelow: Int,
    contactPoints: DoubleArray,
    offset: Int
    //cx: Double, // contact points are in local, pre-transformed space
    //_cy: Double,
    //cz: Double,
): Block {
    val cx = contactPoints[offset]
    val cz = contactPoints[offset + 2]
    val blx = floor(currX + yawCos * cx - yawSin * cz).toInt()
    val blz = floor(currZ + yawSin * cx + yawCos * cz).toInt()

    // iterate in reverse: starts from lowest y-level in maxDepthBelow
    // range, finds first passable y-level and immediately returns
    for ( i in maxDepthBelow downTo 1 ) {
        val blyBelow = bly - i
        val bl = world.getBlockAt(blx, blyBelow, blz)
        if ( bl.isShipPassable() ) {
            return bl
        }
    }

    return world.getBlockAt(blx, bly, blz)
}

val ZERO_OFFSET = doubleArrayOf(0.0, 0.0, 0.0)

/**
 * Just gets the block that the contact block
 * is currently within.
 */
private fun getContactPointBlock(
    world: World,
    currX: Double,
    currY: Double,
    currZ: Double,
    yawSin: Double, // pre-computed cos(yaw)
    yawCos: Double, // pre-computed sin(yaw)
    contactPoints: DoubleArray,
    indexOffset: Int,
    positionOffset: DoubleArray = ZERO_OFFSET
): Block {
    val cx = contactPoints[indexOffset]
    val cy = contactPoints[indexOffset + 1]
    val cz = contactPoints[indexOffset + 2]
    val blx = floor(currX + yawCos * cx - yawSin * cz).toInt()
    val bly = floor(currY + cy).toInt()
    val blz = floor(currZ + yawSin * cx + yawCos * cz).toInt()
    return world.getBlockAt(blx, bly, blz)
}

fun visualizePoints(
    world: World,
    currX: Double,
    currY: Double,
    currZ: Double,
    yawSin: Double, // pre-computed cos(yaw)
    yawCos: Double, // pre-computed sin(yaw)
    contactPoints: DoubleArray,
) {
    val numPoints = contactPoints.size / 3
    for (i in 0 until numPoints) {
        val cx = contactPoints[i * 3]
        val cy = contactPoints[i * 3 + 1]
        val cz = contactPoints[i * 3 + 2]
        val x = currX + yawCos * cx - yawSin * cz
        val y = currY + cy
        val z = currZ + yawSin * cx + yawCos * cz
        world.spawnParticle(Particle.VILLAGER_HAPPY, x, y, z, 1)
    }
}