/**
 * Contain gun on block hit and on entity hit handlers.
 * 
 * Separate hit block and hit entity handlers are used to avoid
 * having null optional entity or block in handler inputs.
 * Alternative would be to have a unified hit handler type
 * `hitHandler(Gun, Location, Entity?, Block?, Entity)` but this
 * would introduce more handler complexity in managing different
 * hit cases. 
 */

package phonon.xc.gun

import java.util.concurrent.ThreadLocalRandom
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Damageable
import phonon.xc.XC
import phonon.xc.util.damage.*
import phonon.xc.util.ChunkCoord3D
import phonon.xc.util.Hitbox
import phonon.xc.util.death.XcPlayerDeathEvent
import phonon.xc.util.explosion.createExplosion
import phonon.xc.event.XCProjectileDamageEvent


/**
 * Common hit block handler function type. Inputs are
 * (
 *  xc: XC,
 *  hitboxes: Map<ChunkCoord3D, List<Hitbox>>,
 *  gun: Gun,
 *  location: Location,
 *  target: Block,
 *  source: Entity,
 * ) -> Unit
 */
typealias GunHitBlockHandler = (XC, Map<ChunkCoord3D, List<Hitbox>>, Gun, Location, Block, Entity) -> Unit

/**
 * Common hit entity handler function type. Inputs are
 * (
 *  xc: XC,
 *  hitboxes: Map<ChunkCoord3D, List<Hitbox>>,
 *  gun: Gun,
 *  location: Location,
 *  target: Entity,
 *  source: Entity,
 *  distance: Double,
 * ) -> Unit
 */
typealias GunHitEntityHandler = (XC, Map<ChunkCoord3D, List<Hitbox>>, Gun, Location, Entity, Entity, Double) -> Unit


/**
 * Map string name to built-in hit block handlers.
 */
public fun getGunHitBlockHandler(name: String): GunHitBlockHandler? {
    return when ( name.lowercase() ) {
        "explosion" -> blockExplosionHitHandler
        "fire" -> blockFireHitHandler
        else -> null
    }
}

/**
 * Map string name to built-in hit block handlers.
 */
public fun getGunHitEntityHandler(name: String): GunHitEntityHandler? {
    return when ( name.lowercase() ) {
        "damage" -> entityDamageHitHandler
        "explosion" -> entityExplosionHitHandler
        else -> null
    }
}


/**
 * Empty entity hit handler.
 */
public val noEntityHitHandler: GunHitEntityHandler = {_, _, _, _, _, _, _ -> }

/**
 * Entity hit handler with damage (standard entity damage hit handler).
 */
public val entityDamageHitHandler = fun(
    xc: XC,
    hitboxes: Map<ChunkCoord3D, List<Hitbox>>,
    gun: Gun,
    location: Location,
    target: Entity,
    source: Entity,
    distance: Double,
) {
    if ( target is LivingEntity ) {
        // for armor stands, typically are vehicles, emit event for
        // external vehicle plugin to read
        if ( target.type == EntityType.ARMOR_STAND ) {
            try {
                Bukkit.getPluginManager().callEvent(XCProjectileDamageEvent(
                    gun = gun,
                    location = location,
                    target = target,
                    source = source,
                    damage = gun.projectileDamageAtDistance(distance),
                    distance = distance,
                ))
            } catch ( err: Exception ) {
                xc.logger.warning("Error calling XCProjectileDamageEvent for ${target}: ${err.message}")
            }
            return
        }

        if ( target is Player && !xc.canPvpAt(location) ) {
            return
        }

        // FOR DEBUGGING
        // val baseDamage = gun.projectileDamageAtDistance(distance)
        // println("baseDamage: $baseDamage, distance: $distance")

        // final damage after 
        // 1. gun damage drop: gun.damageAtDistance(distance)
        // 2. applying armor/resistance
        val damage = xc.damageAfterArmorAndResistance(
            gun.projectileDamageAtDistance(distance),
            target,
            gun.projectileArmorReduction,
            gun.projectileResistanceReduction,
        )

        // player specific target handling
        if ( target is Player ) {
            // mark player entering combat
            xc.addPlayerToCombatLogging(target)

            // player died -> custom death event
            if ( target.getHealth() > 0.0 && damage >= target.getHealth() ) {
                xc.deathEvents[target.getUniqueId()] = XcPlayerDeathEvent(
                    player = target,
                    killer = source,
                    weaponType = XC.ITEM_TYPE_GUN,
                    weaponId = gun.itemModelDefault,
                    weaponMaterial = xc.config.materialGun,
                )
            }

            // play sound for shooter indicating succesful player hit
            if ( xc.config.soundOnHitEnabled && source is Player ) {
                source.playSound(source.getLocation(), xc.config.soundOnHit, xc.config.soundOnHitVolume, 1.0f)
            }
        }
        
        target.damage(damage, null)
        target.setNoDamageTicks(0)

        // add fire ticks
        if ( gun.hitFireTicks > 0 ) {
            target.setFireTicks(gun.hitFireTicks)
        }
    }
}

/**
 * Entity hit handler with damage and a queued explosion at hit location.
 */
public val entityExplosionHitHandler = fun(
    xc: XC,
    hitboxes: Map<ChunkCoord3D, List<Hitbox>>,
    gun: Gun,
    location: Location,
    target: Entity,
    source: Entity,
    distance: Double,
) {
    // do main damage directly to target
    entityDamageHitHandler(
        xc,
        hitboxes,
        gun,
        location,
        target,
        source,
        distance,
    )

    // try playing sound
    try {
        location.getWorld()?.playSound(location, gun.soundExplosion, gun.soundExplosionVolume, gun.soundExplosionPitch)
    } catch (err: Exception) {
        xc.logger.warning("Failed to play sound ${gun.soundExplosion} at ${location}")
    }

    // summon explosion effect at location
    xc.createExplosion(
        hitboxes,
        location,
        source,
        gun.explosionMaxDistance,
        gun.explosionDamage,
        gun.explosionRadius,
        gun.explosionFalloff,
        gun.explosionArmorReduction,
        gun.explosionBlastProtReduction,
        gun.explosionDamageType,
        gun.explosionBlockDamagePower,
        gun.explosionFireTicks,
        gun.explosionParticles,
        XC.ITEM_TYPE_GUN,
        gun.itemModelDefault,
        xc.config.materialGun,
    )
}

/**
 * Empty block hit handler.
 */
public val noBlockHitHandler: GunHitBlockHandler = {_, _, _, _, _, _ -> }

/**
 * Block hit handler that queues explosion at hit location.
 */
public val blockExplosionHitHandler = fun(
    xc: XC,
    hitboxes: Map<ChunkCoord3D, List<Hitbox>>,
    gun: Gun,
    location: Location,
    _block: Block,
    source: Entity,
) {
    // try playing sound
    try {
        location.getWorld()?.playSound(location, gun.soundExplosion, gun.soundExplosionVolume, gun.soundExplosionPitch)
    } catch (err: Exception) {
        xc.logger.warning("Failed to play sound ${gun.soundExplosion} at ${location}")
    }
    
    xc.createExplosion(
        hitboxes,
        location,
        source,
        gun.explosionMaxDistance,
        gun.explosionDamage,
        gun.explosionRadius,
        gun.explosionFalloff,
        gun.explosionArmorReduction,
        gun.explosionBlastProtReduction,
        gun.explosionDamageType,
        gun.explosionBlockDamagePower,
        gun.explosionFireTicks,
        gun.explosionParticles,
        XC.ITEM_TYPE_GUN,
        gun.itemModelDefault,
        xc.config.materialGun,
    )
}

/**
 * Block hit handler that creates fire on top of hit location.
 */
public val blockFireHitHandler = fun(
    xc: XC,
    _hitboxes: Map<ChunkCoord3D, List<Hitbox>>,
    gun: Gun,
    location: Location,
    block: Block,
    source: Entity,
) {
    if ( !xc.canCreateFireAt(location) ) {
        return
    }
    
    if ( ThreadLocalRandom.current().nextDouble() < gun.hitBlockFireProbability ) {
        val blType = block.getType()

        // set block below on fire
        if ( blType == Material.AIR ) {
            val blBelow = block.getRelative(0, -1, 0);
            if ( blBelow.getType().isSolid() ) {
                block.setType(Material.FIRE);
            }
        }
        else if ( blType.isSolid() ) {
            val blAbove = block.getRelative(0, 1, 0);
            if ( blAbove.getType() == Material.AIR ) {
                blAbove.setType(Material.FIRE);
            }
        }
    }
}