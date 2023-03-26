package phonon.xv.system

import java.util.Queue
import org.bukkit.Location
import org.bukkit.entity.Player
import phonon.xc.util.death.XcPlayerDeathEvent
import phonon.xv.XV
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.VehicleStorage
import phonon.xv.core.iter.*
import phonon.xv.component.HealthComponent
import phonon.xv.component.SeatsComponent
import phonon.xv.component.TransformComponent
import phonon.xv.system.DeleteVehicleRequest

/**
 * System for handling vehicle death. Handle death here instead of individual
 * damage events so we can re-use death logic for all vehicle specific damage
 * sources.
 */
public fun XV.systemDeath(
    storage: ComponentsStorage,
    vehicleStorage: VehicleStorage,
    deleteRequests: Queue<DeleteVehicleRequest>,
) {
    val xv = this
    
    for ( (el, seats, health) in ComponentTuple2.query<
        SeatsComponent,
        HealthComponent,
    >(storage) ) {
        try {
            if ( health.current <= 0 && health.death ) {
                // add delete vehicle request
                // create request to destroy vehicle
                val element = storage.getElement(el)!!
                val vehicle = vehicleStorage.getOwningVehicle(element)!!
                deleteRequests.add(DeleteVehicleRequest(vehicle))

                // remove and kill passengers
                val damagePassenger = health.deathPassengerDamage
                val vehicleDeathEvent = health.deathEvent

                for ( i in 0 until seats.count ) {
                    val armorstand = seats.armorstands[i]
                    if ( armorstand !== null ) {
                        armorstand.eject()
                    }

                    val passenger = seats.passengers[i]
                    if ( passenger !== null && passenger.isValid() ) {
                        if ( damagePassenger > passenger.getHealth() ) {
                            if ( vehicleDeathEvent !== null ) {
                                xc.deathEvents[passenger.getUniqueId()] = vehicleDeathEvent.toPlayerDeathEvent(passenger)
                            } else {
                                // TODO: put some kind of default death msg here
                                xc.deathEvents.remove(passenger.getUniqueId())
                            }
                        }
                    
                        passenger.damage(damagePassenger, null)
                    }
                }

                // play sound and particles if element has a transform component
                val deathParticle = health.deathParticle
                if ( deathParticle !== null ) {
                    val transform = element?.components?.transform
                    if ( transform !== null ) {
                        val world = transform.world
                        if ( world !== null ) {
                            world.spawnParticle(
                                deathParticle,
                                transform.x,
                                transform.y,
                                transform.z,
                                health.deathParticleCount,
                                health.deathParticleRandomX,
                                health.deathParticleRandomY,
                                health.deathParticleRandomZ,
                            )

                            val deathSound = health.deathSound
                            if ( deathSound !== null ) {
                                val loc = Location(world, transform.x, transform.y, transform.z)
                                world.playSound(
                                    loc,
                                    deathSound,
                                    1.0f,
                                    1.0f,
                                )
                            }
                        }
                    }
                }
            }
        } catch ( err: Exception ) {
            if ( xv.debug ) {
                xv.logger.severe("Error handling death for element ${el}: ${err}")
            }
        }
    }
}

