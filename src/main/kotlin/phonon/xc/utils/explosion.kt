/**
 * Explosion handler
 */

package phonon.xc.utils.explosion

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Damageable
import phonon.xc.XC
import phonon.xc.event.XCExplosionDamageEvent
import phonon.xc.utils.ChunkCoord3D
import phonon.xc.utils.Hitbox
import phonon.xc.utils.damage.DamageType
import phonon.xc.utils.damage.explosionDamageAfterArmor
import phonon.xc.utils.death.XcPlayerDeathEvent
import phonon.xc.utils.particle.ParticlePacket
import phonon.xc.utils.particle.ParticleExplosion


/**
 * Return damage from explosion based on distance, armor,
 * and blast protection enchant level.
 * 
 * Explosion damage done by a "hat" function:
 *          ___________  baseDamage
 *         /           \
 *     ___/             \____ 0
 *          <--> c <-->
 *                 radius
 * Within center radius, baseDamage is applied.
 * Outside of radius, linearly falloff factor applied, clamped to 0.
 */
public fun baseExplosionDamage(
    baseDamage: Double,
    distance: Double,
    radius: Double,
    falloff: Double,
): Double {
    val distanceBeyondRadius = max(0.0, distance - radius)
    return max(0.0, baseDamage - (distanceBeyondRadius * falloff))
}

/**
 * Create explosion at location.
 */
public fun createExplosion(
    hitboxes: HashMap<ChunkCoord3D, ArrayList<Hitbox>>,
    location: Location,
    source: Entity?,
    maxDistance: Double,
    damage: Double,
    radius: Double,
    falloff: Double, // damage/block falloff
    armorReduction: Double, // damage/armor
    blastProtReduction: Double, // damage/blast protection
    damageType: DamageType,
    blockDamagePower: Float,
    fireTicks: Int, // number of ticks to set targets on fire
    particles: ParticlePacket?,
    weaponType: Int, // metadata for player death tracking
    weaponId: Int,   // metadata for player death tracking
    weaponMaterial: Material, // metadata for player death tracking
) {
    // check if region allows explosions
    if ( !XC.canExplodeAt(location) ) {
        // print to player cannot attack here?
        return
    }

    // get chunks intersecting with explosion radius
    val cxmin = floor(location.x - maxDistance).toInt() shr 4
    val cxmax = ceil(location.x + maxDistance).toInt() shr 4
    val cymin = floor(location.y - maxDistance).toInt() shr 4
    val cymax = ceil(location.y + maxDistance).toInt() shr 4
    val czmin = floor(location.z - maxDistance).toInt() shr 4
    val czmax = ceil(location.z + maxDistance).toInt() shr 4
    
    for ( cx in cxmin..cxmax ) {
        for ( cy in cymin..cymax ) {
            for ( cz in czmin..czmax ) {
                val coord = ChunkCoord3D(cx, cy, cz)
                val hitboxesInChunk = hitboxes[coord] ?: continue

                for ( hitbox in hitboxesInChunk ) {
                    // distance from hitbox center to explosion location,
                    // with hitbox min radius subtracted
                    val distance = (hitbox.distance(location.x.toFloat(), location.y.toFloat(), location.z.toFloat()) - hitbox.radiusMin).toDouble()
                    
                    // base damage based on explosion model.
                    // if base damage > 0.0, then apply damage to entity target
                    val baseDamage = baseExplosionDamage(damage, distance, radius, falloff)
                    if ( baseDamage > 0.0 ) {
                        val target = hitbox.entity
                        if ( target is LivingEntity && target is Damageable ) {
                            val damage = explosionDamageAfterArmor(
                                baseDamage,
                                target,
                                armorReduction,
                                blastProtReduction,
                            )

                            if ( target is Player && target.getHealth() > 0.0 && damage >= target.getHealth() ) {
                                XC.deathEvents[target.getUniqueId()] = XcPlayerDeathEvent(
                                    player = target,
                                    killer = source,
                                    weaponType = weaponType,
                                    weaponId = weaponId,
                                    weaponMaterial = weaponMaterial,
                                )
                            }
                            
                            target.damage(damage, null)
                            target.setNoDamageTicks(0)

                            if ( fireTicks > 0 ) {
                                target.setFireTicks(fireTicks)
                            }
                        }
                        
                        // emit event for external plugins to read
                        Bukkit.getPluginManager().callEvent(XCExplosionDamageEvent(
                            target,
                            baseDamage,
                            damageType,
                            distance,
                            source,
                        ))
                    }
                }
            }
        }
    }
    
    // create block explosion if power > 0
    if ( XC.config.blockDamageExplosion && blockDamagePower > 0.0f ) {
        location.world?.createExplosion(
            location.x,
            location.y,
            location.z,
            blockDamagePower,
        )
    }
    
    // queue explosion particle effect
    if ( particles != null ) {
        XC.particleExplosionQueue.add(ParticleExplosion(
            particles,
            location.world,
            location.x,
            location.y,
            location.z,
            true,
        ))
    }
}
