package phonon.xv.system

import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.ArmorStand
import org.bukkit.util.Vector
import org.bukkit.util.EulerAngle
import net.kyori.adventure.text.Component
import phonon.xv.XV
import phonon.xv.common.UserInput
import phonon.xv.component.FuelComponent
import phonon.xv.component.HealthComponent
import phonon.xv.component.SeatsComponent
import phonon.xv.component.AirplaneComponent
import phonon.xv.component.TransformComponent
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.iter.*
import phonon.xv.util.math.directionFromPrecomputedYawPitch
import phonon.xv.util.math.EulerOrder
import phonon.xv.util.math.distanceToAngle
import phonon.xv.util.math.moveTowardsAngle

/**
 * System for doing plane movement fuel checks. Should run before plane
 * movement controls system.
 */
public fun systemPlaneFuel(
    storage: ComponentsStorage,
) {
    for ( (_, transform, plane, fuel) in ComponentTuple3.query<
        TransformComponent,
        AirplaneComponent,
        FuelComponent,
    >(storage) ) {
        if ( fuel.current <= 0 && !fuel.ignore ) { // indicate no fuel for land movement controller
            plane.noFuel = true
            continue
        } else {
            plane.noFuel = false
        }

        // for now do simple check: either use idle rate or moving rate
        if ( transform.isMoving ) {
            fuel.timeRemaining -= fuel.burnRateMoving
        } else {
            fuel.timeRemaining -= fuel.burnRateIdle
        }

        if ( fuel.timeRemaining <= 0.0 ) {
            fuel.current = max(0, fuel.current - 1)
            fuel.timeRemaining = fuel.timePerFuelWhenIdle.toDouble()
        }
    }
}


/**
 * System for plane movement. Plane moves towards player's cursor direction.
 */
public fun XV.systemPlaneMovement(
    storage: ComponentsStorage,
    userInputs: Map<UUID, UserInput>,
    despawnRequests: ConcurrentLinkedQueue<DespawnVehicleFinish>,
) {
    val xv = this
    for ( (el, transform, seats, health, plane) in ComponentTuple4.query<
        TransformComponent,
        SeatsComponent,
        HealthComponent, // TODO: make this optional component
        AirplaneComponent,
    >(storage) ) {
        try {
            // clear transform dirty flags
            transform.positionDirty = false
            transform.pitchDirty = false
            transform.yawDirty = false
            transform.rollDirty = false

            val world = transform.world
            if ( world === null ) {
                continue
            }

            val pilot = seats.passengers[plane.seatController]
            var isFiring = false // will update with pilot controls spacebar
            
            // current block location of plane
            val blx = floor(transform.x).toInt()
            val bly = floor(transform.y).toInt()
            val blz = floor(transform.z).toInt()

            var targetDirection = plane.direction.clone()
            val planeEntity = plane.armorstand

            // print plane info message to pilot
            var printInfo = false
            
            // block
            val block = world.getBlockAt(blx, bly, blz)
            val blockType = block.type
            val blockPassable = block.isPassable()
            val inWater = (blockType == Material.WATER)
            
            // if in water check if fully submerged, then immediately kill or despawn
            if ( inWater ) {
                // checks if adjacent blocks in water
                val blockX1 = block.getRelative(1, 0, 0)
                val blockX2 = block.getRelative(-1, 0, 0)
                val blockZ1 = block.getRelative(0, 0, 1)
                val blockZ2 = block.getRelative(0, 0, -1)
                val blockY1 = block.getRelative(0, 1, 0)
                val blockY2 = block.getRelative(0, -1, 0)

                if ( blockX1.type == Material.WATER &&
                    blockX2.type == Material.WATER &&
                    blockZ1.type == Material.WATER &&
                    blockZ2.type == Material.WATER &&
                    blockY1.type == Material.WATER &&
                    blockY2.type == Material.WATER
                ) {
                    if ( health.current < plane.healthControllable ) {
                        health.current = 0.0 // kill when hit water at low hp
                    } else { // despawn
                        val element = xv.storage.getElement(el)
                        if ( element !== null ) {
                            val vehicle = xv.vehicleStorage.getOwningVehicle(element)
                            if ( vehicle !== null ) {
                                despawnRequests.add(DespawnVehicleFinish(
                                    vehicle = vehicle,
                                    dropItem = true,
                                    force = true,
                                ))
                            }
                        }
                    }
                    continue
                }
            }
            
            // check if in air
            val blockBelow = block.getRelative(0, -1, 0)
            val blockBelowType = blockBelow.getType()
            val blockBelowPassable = blockBelow.isPassable()
            val inAir = blockPassable && blockBelowPassable
            
            // check
            val blockAbove = block.getRelative(0, 1, 0)
            val blockAboveType = blockAbove.getType()
            val blockAbovePassable = blockAbove.isPassable()
            val inSolid = !blockPassable && !blockBelowPassable && !blockAbovePassable
            
            // if inside solid blocks too long, force despawn
            if ( inSolid ) {
                plane.inSolidDespawnCounter += 1
                if ( plane.inSolidDespawnCounter > 40 ) { // ~2 seconds, force despawn
                    val element = xv.storage.getElement(el)
                    if ( element !== null ) {
                        val vehicle = xv.vehicleStorage.getOwningVehicle(element)
                        if ( vehicle !== null ) {
                            despawnRequests.add(DespawnVehicleFinish(
                                vehicle = vehicle,
                                dropItem = true,
                                force = true,
                            ))
                        }
                    }
                }
            } else {
                plane.inSolidDespawnCounter = 0
            }

            val prevYaw = transform.yaw
            val prevPitch = transform.pitch
            val prevYawSpeed = plane.yawRotationSpeed
            
            // handle plane interaction
            if ( pilot !== null && health.current >= plane.healthControllable && !plane.noFuel ) {
                // get user input
                val controls = userInputs[pilot.getUniqueId()] ?: UserInput()

                // set isFiring flag from spacebar
                isFiring = controls.jump

                // ground / run way controls, need to lift off
                if ( !inAir ) {
                    // zero yaw, pitch rotation speed
                    plane.yawRotationSpeed = 0.0
                    plane.pitchRotationSpeed = 0.0

                    if ( plane.speed < plane.speedLiftoff ) {
                        if ( controls.forward ) {
                            plane.speed = plane.speed + plane.groundAcceleration
                        }
                        else {
                            plane.speed = max(0.0, plane.speed - plane.groundDeceleration)
                        }
                        
                        // allow rotating plane on ground with [A]/[D] keys
                        if ( plane.speed < 0.05 ) {
                            val yawTurn = if ( controls.right ) {
                                plane.yawSpeedOnGround
                            } else if ( controls.left ) {
                                -plane.yawSpeedOnGround
                            } else {
                                0.0
                            }

                            if ( yawTurn != 0.0 ) {
                                // update yaw, constrain angle to [0, 360]
                                var newYaw = transform.yaw + yawTurn
                                newYaw = if ( newYaw >= 360.0 ) {
                                    newYaw - 360.0
                                } else if ( newYaw < 0.0 ) {
                                    newYaw + 360.0
                                } else {
                                    newYaw
                                }

                                transform.updateYaw(newYaw)
                            }
                        }
        
                        if ( !block.isPassable() ) {
                            val blockAbove = block.getRelative(0, 1, 0)
                            if ( blockAbove.isPassable() ) {
                                transform.y = blockAbove.y.toDouble()
                            }
                        }
                        
                        // when speed low enough, reset pitch rotation to fixed ground pitch
                        // and zero roll rotation
                        if ( transform.pitch != -plane.groundPitch && plane.speed < 0.5 ) {
                            transform.updatePitch(-plane.groundPitch)
                            transform.zeroRoll()
                        }
        
                        plane.direction = directionFromPrecomputedYawPitch(
                            plane.direction,
                            transform.yawSin,
                            transform.yawCos,
                            0.0,
                            1.0,
                        )
                    }
                    // lifting-off mechanics
                    else {
                        val blockAbove = block.getRelative(0, 1, 0)
                        if ( blockAbove.isPassable() ) {
                            transform.y += 1.0
                        }
                    }
                }
                // in air, speed too low, crash down
                else if ( plane.speed < plane.speedFlyMin ) { // not enough speed, plane to crash down
                    // force yaw speed to 0 (no in plane turning anymore)
                    plane.yawRotationSpeed = 0.0

                    // update pitch speed
                    val prevPitchSpeed = plane.pitchRotationSpeed
                    val newPitchSpeed = max(-plane.pitchSpeedMax, plane.pitchRotationSpeed - plane.pitchAcceleration)
                    plane.pitchRotationSpeed = newPitchSpeed

                    // update pitch (note negative due to minecraft graphics convention)
                    val newPitch = max(-plane.pitchMin, transform.pitch - newPitchSpeed)
                    if ( newPitch != prevPitch ) {
                        transform.updatePitch(newPitch)
                        plane.direction = directionFromPrecomputedYawPitch(
                            plane.direction,
                            transform.yawSin,
                            transform.yawCos,
                            transform.pitchSin,
                            transform.pitchCos
                        )
                    }
                }
                // in air, speed steady
                else {
                    // pilot facing direction
                    val pilotLoc = pilot.location
                    val pilotViewPitch = pilotLoc.pitch.toDouble()
                    val pilotViewYaw = if ( pilotLoc.yaw < 0.0 ) { // minecraft yaw is in range [-180, 180], convert to [0, 360]
                        pilotLoc.yaw.toDouble() + 360.0
                    } else {
                        pilotLoc.yaw.toDouble()
                    }
                    var pilotViewDir = pilotLoc.getDirection()
                    
                    // control speed
                    if ( controls.forward ) {
                        plane.speed = min(plane.speedFast, plane.speed + plane.acceleration)
                    }
                    else if (controls.backward) {
                        plane.speed = max(plane.speedSlow, plane.speed - plane.acceleration)
                    } else { // mean revert to speedSteady
                        if ( plane.speed > plane.speedSteady ) {
                            plane.speed = max(plane.speedSteady, plane.speed - plane.deceleration)
                        }
                        else if ( plane.speed < plane.speedSteady ) {
                            plane.speed = min(plane.speedSteady, plane.speed + plane.deceleration)
                        }

                        val speedDiff = abs(plane.speed - plane.speedSteady)
                        if ( speedDiff < 0.1 ) {
                            plane.speed = plane.speedSteady
                        }
                    }
                    
                    // interpolate pitch
                    val targetPitch = pilotViewPitch.coerceIn(-plane.pitchMax, -plane.pitchMin)
                    if ( transform.pitch != targetPitch ) {
                        val newPitch = if ( Math.abs(transform.pitch - targetPitch) < 0.5 ) {
                            targetPitch
                        }
                        else { // exponential averaging
                            0.4 * targetPitch + 0.6 * transform.pitch
                        }
                        transform.updatePitch(newPitch)
                        transform.pitchDirty = true
                    }

                    // turning yaw rotation
                    val yawDistanceToTarget = prevYaw.distanceToAngle(pilotViewYaw)
                    if ( yawDistanceToTarget > 0 ) {
                        plane.yawRotationSpeed = min(plane.yawSpeedMax, plane.yawRotationSpeed + plane.yawAcceleration)
                        val newYaw = min(pilotViewYaw, prevYaw + plane.yawRotationSpeed)
                        transform.updateYaw(newYaw)
                        transform.yawDirty = true
                    } else if ( yawDistanceToTarget < 0 ) {
                        plane.yawRotationSpeed = max(-plane.yawSpeedMax, plane.yawRotationSpeed - plane.yawAcceleration)
                        val newYaw = max(pilotViewYaw, prevYaw + plane.yawRotationSpeed)
                        transform.updateYaw(newYaw)
                        transform.yawDirty = true
                    } else {
                        // force yaw speed to 0 (no in plane turning anymore)
                        plane.yawRotationSpeed = if ( plane.yawRotationSpeed > 0.1 ) {
                            max(0.0, plane.yawRotationSpeed - 2*plane.yawAcceleration)
                        } else if ( plane.yawRotationSpeed < 1.0 ) {
                            min(0.0, plane.yawRotationSpeed + 2*plane.yawAcceleration)
                        } else {
                            0.0
                        }
                    }

                    if ( transform.pitchDirty || transform.yawDirty ) {
                        plane.direction = directionFromPrecomputedYawPitch(
                            plane.direction,
                            transform.yawSin,
                            transform.yawCos,
                            transform.pitchSin,
                            transform.pitchCos
                        )
                    }
                }
            }
            else {
                // update position without player controls
                if ( inAir ) {
                    if ( plane.speed > plane.speedFlyMin ) {
                        plane.speed = plane.speedSteady
                    }

                    // force yaw speed to 0 (no in plane turning anymore)
                    plane.yawRotationSpeed = 0.0

                    // update pitch speed
                    val prevPitchSpeed = plane.pitchRotationSpeed
                    val newPitchSpeed = max(-plane.pitchSpeedMax, plane.pitchRotationSpeed - plane.pitchAcceleration)
                    plane.pitchRotationSpeed = newPitchSpeed

                    // update pitch (note negative due to minecraft graphics convention)
                    val newPitch = max(-plane.pitchMin, transform.pitch - newPitchSpeed)
                    if ( newPitch != prevPitch ) {
                        transform.updatePitch(newPitch)
                        plane.direction = directionFromPrecomputedYawPitch(
                            plane.direction,
                            transform.yawSin,
                            transform.yawCos,
                            transform.pitchSin,
                            transform.pitchCos
                        )
                    }
                }
                else {
                    plane.yawRotationSpeed = 0.0
                    plane.pitchRotationSpeed = 0.0

                    // not in air, slow down plane
                    if ( plane.speed > 0.0 ) {
                        plane.speed = max(0.0, plane.speed - plane.deceleration)
                    }
                }
            }
            
            // ===================================
            // movement: update location
            // ===================================
            //// DEBUG
            // println("inAir=${inAir}, speed: ${plane.speed}, plane.direction=${plane.direction}, x: ${transform.x}, y: ${transform.y}, z: ${transform.z}, pitch: ${transform.pitch}, yaw: ${transform.yaw}")
            if ( plane.speed > 0.0 ) {
                val dx = plane.direction.x * plane.speed
                val dy = plane.direction.y * plane.speed
                val dz = plane.direction.z * plane.speed

                transform.x = transform.x + dx
                transform.y = min(transform.y + dy, plane.yHeightMax)
                transform.z = transform.z + dz
                transform.positionDirty = true
                
                // block collision check: check next block plane entering, if solid do 
                // collision damage 
                val newBlx = floor(transform.x).toInt()
                val newBly = floor(transform.y).toInt()
                val newBlz = floor(transform.z).toInt()
                val newBlockAbove = world.getBlockAt(newBlx, newBly + 1, newBlz) // block above location is actual block in front
                if ( plane.speed > 0.3 && !newBlockAbove.isPassable() ) {
                    createPlaneExplosionParticle(world, plane, transform)
                    health.damage(plane.healthDamagePerCrash)

                    // with two speed thresholds, this slows down plane
                    // but ensures two damage events can occur
                    if ( plane.speed > 0.9 ) {
                        plane.speed = 0.9
                    }
                    else {
                        plane.speed = 0.0
                    }
                }
            }

            // ===================================
            // update plane model
            // ===================================
            val rollRad = -plane.yawRotationSpeed / plane.yawSpeedMax * plane.rollRadPerYawSpeed
            transform.rollRad = rollRad
            transform.rollSin = Math.sin(rollRad)
            transform.rollCos = Math.cos(rollRad)

            if ( transform.positionDirty || transform.pitchDirty || transform.yawDirty) {
                plane.rotMatrix = plane.rotMatrix.makeRotationFromPrecomputed(
                    transform.pitchSin,
                    transform.pitchCos,
                    -transform.yawSin, // negate yaw
                    transform.yawCos,
                    -transform.rollSin, // negate roll
                    transform.rollCos,
                    EulerOrder.YXZ,
                )
                
                // update bullet spawn location
                val newBulletPosition: Vector = plane.rotMatrix.transformVector(plane.bulletOffset, Vector())
                val newBulletPosition2: Vector = plane.rotMatrix.transformVector(plane.bulletOffset2, Vector())
                plane.bulletSpawnOffsetX = transform.x + newBulletPosition.x
                plane.bulletSpawnOffsetY = transform.y + newBulletPosition.y
                plane.bulletSpawnOffsetZ = transform.z + newBulletPosition.z
                plane.bulletSpawnOffset2X = transform.x + newBulletPosition2.x
                plane.bulletSpawnOffset2Y = transform.y + newBulletPosition2.y
                plane.bulletSpawnOffset2Z = transform.z + newBulletPosition2.z

                if ( xv.config.debugContactPoints ) {
                    world.spawnParticle(
                        Particle.VILLAGER_HAPPY,
                        plane.bulletSpawnOffsetX,
                        plane.bulletSpawnOffsetY,
                        plane.bulletSpawnOffsetZ,
                        1,
                    )
                    world.spawnParticle(
                        Particle.VILLAGER_HAPPY,
                        plane.bulletSpawnOffset2X,
                        plane.bulletSpawnOffset2Y,
                        plane.bulletSpawnOffset2Z,
                        1,
                    )
                }

                if ( planeEntity !== null ) {
                    planeEntity.teleport(Location(
                        world,
                        transform.x,
                        transform.y,
                        transform.z,
                        transform.yawf,
                        0f,
                    ))

                    planeEntity.setHeadPose(EulerAngle(
                        transform.pitchRad,
                        0.0,
                        transform.rollRad,
                    ))
                }
            }

            // =================================
            // firing controls
            // =================================
            if ( pilot !== null && isFiring && plane.fireDelayCounter <= 0 ) {
                xv.infoMessage.put(pilot, 1, "SHOOTING (TODO)")
                plane.fireDelayCounter = plane.firerate
            }

            // reduce firing reload tick
            plane.fireDelayCounter = max(0, plane.fireDelayCounter - 1)

            // // final pilot handling TODO: migrate
            // if ( pilot !== null ) {
                
            //     // tick time to print info to pilot
            //     plane.printInfoTick -= 1
            //     if ( plane.printInfoTick < 0 ) {
            //         printInfo = true
            //         plane.printInfoTick = plane.timePrintInfo
            //     }
                
            //     // message pilot
            //     if ( printInfo ) {
            //         val fuelProgress = plane.fuelBurn.toDouble() / plane.fuelBurnRate.toDouble()
            //         xv.infoMessage.put(pilot, "${ChatColor.GRAY}Ammo: [${plane.ammo}/${plane.ammoMax}] | Fuel: ${plane.fuel}/${plane.fuelMax} ${progressBarSmall(fuelProgress)}")
            //     }

            //     // if seat passenger empty, then remove passenger reference
            //     if ( plane.planeElement.entity?.getPassengers()?.getOrNull(0) === null ) {
            //         plane.removePassenger()
            //     }
            // }
        } catch ( err: Exception ) {
            if ( xv.debug ) {
                err.printStackTrace()
                xv.logger.severe("Error in plane tick: ${err.message}")
            }
        }
    }
}

/**
 * Helper to create a plane explosion particles and sound at location
 * when plane is crashing into blocks.
 */
private fun createPlaneExplosionParticle(
    world: World,
    plane: AirplaneComponent,
    transform: TransformComponent,
) {
    val loc = Location(world, transform.x, transform.y, transform.z)

    world.spawnParticle(
        plane.particleExplosion,
        transform.x,
        transform.y,
        transform.z,
        1,
        0.0,
        0.0,
        0.0,
        0.0,
        null,
        true
    )

    world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 4f, 1f)
}
