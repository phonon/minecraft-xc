/**
 * Contains various particle effects sytems.
 */
package phonon.xv.system

import java.util.Queue
import java.util.concurrent.ThreadLocalRandom
import org.bukkit.Location
import org.bukkit.entity.Player
import phonon.xv.XV
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.iter.*
import phonon.xv.component.HealthComponent
import phonon.xv.component.ParticlesComponent
import phonon.xv.component.SmokeParticlesComponent
import phonon.xv.component.TransformComponent
import phonon.xv.system.DeleteVehicleRequest

/**
 * System for creating periodic particles for elements.
 * For now, this requires health component for built-in health threshold
 * check before spawning particles. Example usage of this system is to
 * spawn in burning particles (e.g. Particle.LAVA) when a vehicle like a
 * tank or plane is at low health.
 */
public fun XV.systemPeriodicParticles(
    storage: ComponentsStorage,
) {
    val xv = this

    for ( (el, transform, health, particles) in ComponentTuple3.query<
        TransformComponent,
        HealthComponent,
        ParticlesComponent,
    >(storage) ) {
        try {
            if ( health.current <= particles.healthThreshold ) {
                if ( particles.tickCounter <= 0 ) {
                    particles.tickCounter = particles.tickPeriod

                    val world = transform.world
                    if ( world !== null ) {
                        val extraData = particles.extraData ?: 0.0
                        world.spawnParticle(
                            particles.particle,
                            transform.x + particles.offsetX,
                            transform.y + particles.offsetY,
                            transform.z + particles.offsetZ,
                            particles.count,
                            particles.randomX,
                            particles.randomY,
                            particles.randomZ,
                            extraData,
                            null,
                            particles.force,
                        )
                    }
                } else {
                    particles.tickCounter -= 1
                }
            }
        } catch ( err: Exception ) {
            xv.logger.severe("Error handling periodic particles for element ${el}: ${err}")
        }
    }
}

/**
 * System for creating specifically periodic smoke type particles for
 * elements. These have a different function format than regular particles
 * For now, this requires health component for built-in health threshold
 * check before spawning particles.
 * 
 * Example usage of this system is to spawn in smoke particles
 * (e.g. Particle.CAMPFIRE_SIGNAL_SMOKE) when a vehicle like a tank or plane
 * is at low health.
 */
public fun XV.systemPeriodicSmokeParticles(
    storage: ComponentsStorage,
) {
    val xv = this

    // use shared random for all particles
    val rng = ThreadLocalRandom.current()
    val randX = rng.nextDouble()
    val randY = rng.nextDouble()
    val randZ = rng.nextDouble()

    for ( (el, transform, health, particles) in ComponentTuple3.query<
        TransformComponent,
        HealthComponent,
        SmokeParticlesComponent,
    >(storage) ) {
        try {
            if ( health.current <= particles.healthThreshold ) {
                if ( particles.tickCounter <= 0 ) {
                    particles.tickCounter = particles.tickPeriod

                    val world = transform.world
                    if ( world !== null ) {
                        val extraData = particles.extraData ?: 0.0
                        world.spawnParticle(
                            particles.particle,
                            transform.x + particles.offsetX + randX * particles.randomX,
                            transform.y + particles.offsetY + randY * particles.randomY,
                            transform.z + particles.offsetZ + randZ * particles.randomZ,
                            particles.count,
                            0.0,
                            particles.speed,
                            0.0,
                            extraData,
                            null,
                            particles.force,
                        )
                    }
                } else {
                    particles.tickCounter -= 1
                }
            }
        } catch ( err: Exception ) {
            xv.logger.severe("Error handling periodic particles for element ${el}: ${err}")
        }
    }
}
