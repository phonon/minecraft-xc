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
        if ( fuel.current <= 0 ) { // indicate no fuel for land movement controller
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
        // clear transform dirty flags
        transform.positionDirty = false
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
        val inAir = block.isPassable() && blockBelow.isPassable()

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
                
                if ( plane.speed < plane.speedLiftoff ) {
                    // DEAL DAMAGE CRAShiNG PLANE WITH NO SURVIVORS
                    if ( transform.pitch > plane.safeLandingPitch && plane.speed > plane.speedFlyMin ) {
                        createPlaneExplosionParticle(world, plane, transform)
                        health.damage(plane.healthDamagePerCrash)
                        plane.speed = 0.0
                    }
                    else {
                        if ( controls.forward ) {
                            plane.speed = plane.speed + plane.acceleration
                        }
                        else {
                            plane.speed = max(0.0, plane.speed - plane.deceleration)
                        }
                        
                        // allow rotating plane on ground with [A]/[D] keys
                        if ( plane.speed < 0.05 ) {
                            val yawTurn = if ( controls.left ) {
                                plane.yawSpeedOnGround
                            } else if ( controls.right ) {
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
                        if ( transform.pitch != plane.groundPitch && plane.speed < 0.5 ) {
                            transform.updatePitch(plane.groundPitch)
                            transform.zeroRoll()
                        }
        
                        plane.direction = directionFromPrecomputedYawPitch(
                            plane.direction,
                            transform.yawSin,
                            transform.yawCos,
                            0.0,
                            1.0
                        )
                    }
                }
                // lifting-off mechanics
                else {
                    val blockAbove = block.getRelative(0, 1, 0)
                    if ( blockAbove.isPassable() ) {
                        transform.y = blockAbove.y.toDouble()
                        // plane.speed = max(0.0, plane.speed - plane.deceleration)
                    }
                    else { // hitting solid block, do damage on hit
                        createPlaneExplosionParticle(world, plane, transform)
                        health.damage(plane.healthDamagePerCrash)

                        if ( plane.speed > plane.speedSlow ) {
                            plane.speed = plane.speedFlyMin
                        }
                        else {
                            plane.speed = 0.0
                        }
                    }
                }
            }
            // in air, speed too low, crash
            else if ( plane.speed < plane.speedFlyMin ) { // not enough speed, plane to crash down
                val targetPitch = plane.pitchSpeedMax

                // interp pitch
                if ( transform.pitch != plane.pitchSpeedMax ) {
                    transform.pitch = min(transform.pitch + 1.0, plane.pitchSpeedMax)
                    transform.pitchf = transform.pitch.toFloat()
                    
                    val pitchRad = Math.toRadians(transform.pitch)
                    transform.pitchRad = pitchRad
                    transform.pitchSin = Math.sin(pitchRad)
                    transform.pitchCos = Math.cos(pitchRad)

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
                val pilotPitch = pilotLoc.pitch.toDouble()
                var pilotDir = pilotLoc.getDirection()
                
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

                val targetPitch = pilotPitch.coerceIn(-plane.pitchSpeedMax, plane.pitchSpeedMax)
                
                // interp pitch
                if ( transform.pitch != targetPitch ) {
                    if ( Math.abs(transform.pitch - targetPitch) < 0.5 ) {
                        transform.pitch = targetPitch
                        transform.pitchf = targetPitch.toFloat()
                    }
                    else {
                        transform.pitch = 0.05 * targetPitch + 0.95 * transform.pitch
                        transform.pitchf = transform.pitch.toFloat()
                    }

                    // pre-compute
                    val pitchRad = Math.toRadians(transform.pitch)
                    transform.pitchRad = pitchRad
                    transform.pitchSin = Math.sin(pitchRad)
                    transform.pitchCos = Math.cos(pitchRad)
                }

                // turning yaw rotation
                plane.yawRotationSpeed = if ( controls.left ) {
                    if ( plane.yawRotationSpeed < 0.0 ) {
                        plane.yawRotationSpeed + 0.14
                    }
                    else {
                        plane.yawRotationSpeed + plane.yawAcceleration
                    }
                } else if ( controls.right ) {
                    if ( plane.yawRotationSpeed > 0.0 ) {
                        plane.yawRotationSpeed - 0.14
                    }
                    else {
                        plane.yawRotationSpeed - plane.yawAcceleration
                    }
                } else if ( plane.yawRotationSpeed != 0.0 ) {
                    if ( plane.yawRotationSpeed > 0.0 ) {
                        if ( plane.yawRotationSpeed < 0.1 ) {
                            0.0
                        }
                        else {
                            plane.yawRotationSpeed - plane.yawAcceleration
                        }
                    }
                    else {
                        if ( plane.yawRotationSpeed > -0.1 ) {
                            0.0
                        }
                        else {
                            plane.yawRotationSpeed + plane.yawAcceleration
                        }
                    }
                }
                else {
                    0.0
                }

                // interp yaw
                if ( plane.yawRotationSpeed != 0.0 ) {

                    plane.yawRotationSpeed = plane.yawRotationSpeed.coerceIn(-plane.yawSpeedMax, plane.yawSpeedMax)
        
                    // update yaw, constrain angle to [0, 360]
                    transform.yaw += plane.yawRotationSpeed
                    transform.yaw = if ( transform.yaw >= 360.0 ) {
                        transform.yaw - 360.0
                    } else if ( transform.yaw < 0.0 ) {
                        transform.yaw + 360.0
                    } else {
                        transform.yaw
                    }

                    transform.yawf = transform.yaw.toFloat()
                    val yawRad = Math.toRadians(transform.yaw)
                    transform.yawRad = yawRad
                    transform.yawSin = Math.sin(yawRad)
                    transform.yawCos = Math.cos(yawRad)
                }

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
            // update position without player controls
            if ( inAir ) {
                val targetPitch = plane.pitchSpeedMax
                plane.speed = min(plane.speedFast, plane.speed + plane.acceleration)

                // interp pitch
                if ( transform.pitch != plane.pitchSpeedMax ) {
                    transform.pitch = min(transform.pitch + 1.0, plane.pitchSpeedMax)
                    transform.pitchf = transform.pitch.toFloat()
                    
                    val pitchRad = Math.toRadians(transform.pitch)
                    transform.pitchRad = pitchRad
                    transform.pitchSin = Math.sin(pitchRad)
                    transform.pitchCos = Math.cos(pitchRad)

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
                if ( plane.speed > 0.0 ) {
                    val blockAbove = block.getRelative(0, 1, 0)
                    if ( plane.speed > 0.8 && !blockAbove.isPassable() ) {
                        createPlaneExplosionParticle(world, plane, transform)
                        health.damage(plane.healthDamagePerCrash)

                        if ( plane.speed > 0.9 ) {
                            plane.speed = 0.9
                        }
                        else {
                            plane.speed = 0.0
                        }
                    }
                    else {
                        plane.speed = max(0.0, plane.speed - plane.deceleration)
                    }
                }
            }
        }
        
        // ===================================
        // movement: update location
        // ===================================
        if ( plane.speed > 0.0 ) {

            val dx = plane.direction.x * plane.speed
            val dy = plane.direction.y * plane.speed
            val dz = plane.direction.z * plane.speed

            transform.x = transform.x + dx
            transform.y = min(transform.y + dy, plane.yHeightMax)
            transform.z = transform.z + dz

            planeEntity?.teleport(Location(
                world,
                transform.x,
                transform.y,
                transform.z,
                transform.yawf,
                transform.pitchf,
            ))
        }

        // ===================================
        // update plane rotation
        // ===================================
        val yawTurningChanged = (prevYawSpeed != plane.yawRotationSpeed)
        val rotationChanged = (transform.yaw != prevYaw) || (transform.pitch != prevPitch) || yawTurningChanged
        
        // update plane roll
        if ( yawTurningChanged ) {
            val rollRad = plane.yawRotationSpeed / plane.yawSpeedMax * 0.7
            transform.rollRad = rollRad
            transform.rollSin = Math.sin(rollRad)
            transform.rollCos = Math.cos(rollRad)
        }

        if ( rotationChanged ) {
            plane.rotMatrix = plane.rotMatrix.makeRotationFromPrecomputed(
                transform.pitchSin,
                transform.pitchCos,
                -transform.yawSin,
                transform.yawCos,
                transform.rollSin,
                transform.rollCos,
                EulerOrder.YXZ,
            )
            
            // update bullet spawn location
            val newBulletPosition: Vector = plane.rotMatrix.transformVector(plane.bulletOffset, Vector())
            plane.bulletSpawnOffsetX = newBulletPosition.x
            plane.bulletSpawnOffsetY = newBulletPosition.y
            plane.bulletSpawnOffsetZ = newBulletPosition.z

            if ( planeEntity !== null ) {
                if ( transform.rollRad != 0.0 ) {
                    val euler = plane.rotMatrix.toEuler(EulerOrder.ZYX)

                    planeEntity.setHeadPose(EulerAngle(
                        euler.x,
                        -euler.y,
                        -euler.z
                    ))
                }
                else {
                    planeEntity.setHeadPose(EulerAngle(
                        transform.pitchRad,
                        transform.yawRad,
                        0.0
                    ))
                }
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
