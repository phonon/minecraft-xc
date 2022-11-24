/**
 * Contain gun on block hit and on entity hit handlers.
 * 
 * Separate hit block and hit entity handlers are used to avoid
 * having null optional entity or block in handler inputs.
 * Alternative would be to have a unified hit handler type
 * `hitHandler(ThrowableItem, Location, Entity?, Block?, Entity)` but this
 * would introduce more handler complexity in managing different
 * hit cases. 
 */

package phonon.xc.throwable

import java.util.concurrent.ThreadLocalRandom
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Entity
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
 * Common throwable timer expired handler function type. Inputs are
 * (
 *  xc: XC,
 *  hitboxes: HashMap<ChunkCoord3D, ArrayList<Hitbox>>,
 *  throwable: ThrowableItem,
 *  location: Location,
 *  source: Entity,
 * ) -> Unit
 */
typealias ThrowableTimerExpiredHandler = (XC, HashMap<ChunkCoord3D, ArrayList<Hitbox>>, ThrowableItem, Location, Entity) -> Unit

/**
 * Common hit block handler function type. Inputs are
 * (
 *  xc: XC,
 *  hitboxes: HashMap<ChunkCoord3D, ArrayList<Hitbox>>,
 *  throwable: ThrowableItem,
 *  location: Location,
 *  target: Block,
 *  source: Entity,
 * ) -> Unit
 */
typealias ThrowableBlockHitHandler = (XC, HashMap<ChunkCoord3D, ArrayList<Hitbox>>, ThrowableItem, Location, Block, Entity) -> Unit

/**
 * Common hit entity handler function type. Inputs are
 * (
 *  xc: XC,
 *  hitboxes: HashMap<ChunkCoord3D, ArrayList<Hitbox>>,
 *  throwable: Throwable,
 *  location: Location,
 *  target: Entity,
 *  source: Entity,
 * ) -> Unit
 */
typealias ThrowableEntityHitHandler = (XC, HashMap<ChunkCoord3D, ArrayList<Hitbox>>, ThrowableItem, Location, Entity, Entity) -> Unit


/**
 * Map string name to built-in timer expipred handlers.
 */
public fun getThrowableTimerExpiredHandler(name: String): ThrowableTimerExpiredHandler? {
    return when ( name.lowercase() ) {
        "none" -> noTimerExpiredHandler
        "explosion" -> timerExpiredExplosionHandler
        else -> null
    }
}

/**
 * Map string name to built-in hit block handlers.
 */
public fun getThrowableBlockHitHandler(name: String): ThrowableBlockHitHandler? {
    return when ( name.lowercase() ) {
        "none" -> noBlockHitHandler
        "explosion" -> blockExplosionHitHandler
        else -> null
    }
}

/**
 * Map string name to built-in hit block handlers.
 */
public fun getThrowableEntityHitHandler(name: String): ThrowableEntityHitHandler? {
    return when ( name.lowercase() ) {
        "none" -> noEntityHitHandler
        "damage" -> entityDamageHitHandler
        "explosion" -> entityExplosionHitHandler
        else -> null
    }
}


/**
 * Empty timer expired handler.
 */
public val noTimerExpiredHandler: ThrowableTimerExpiredHandler = {_, _, _, _, _ -> }

/**
 * Handler to create explosion at location after timer expired.
 */
public val timerExpiredExplosionHandler = fun(
    xc: XC,
    hitboxes: HashMap<ChunkCoord3D, ArrayList<Hitbox>>,
    throwable: ThrowableItem,
    location: Location,
    source: Entity,
) {
    // try playing sound
    try {
        location.getWorld()?.playSound(location, throwable.soundExplosion, 1.0f, 1.0f)
    } catch (e: Exception) {
        xc.logger.warning("Failed to play sound ${throwable.soundExplosion} at ${location}")
    }
    
    // summon explosion effect at location
    xc.createExplosion(
        hitboxes,
        location,
        source,
        throwable.explosionMaxDistance,
        throwable.explosionDamage,
        throwable.explosionRadius,
        throwable.explosionFalloff,
        throwable.explosionArmorReduction,
        throwable.explosionBlastProtReduction,
        throwable.explosionDamageType,
        throwable.explosionBlockDamagePower,
        throwable.explosionFireTicks,
        throwable.explosionParticles,
        XC.ITEM_TYPE_THROWABLE,
        throwable.itemModelDefault,
        xc.config.materialThrowable,
    )
}


/**
 * Empty entity hit handler.
 */
public val noEntityHitHandler: ThrowableEntityHitHandler = {_, _, _, _, _, _ -> }

/**
 * Entity hit handler with damage (standard entity damage hit handler).
 */
public val entityDamageHitHandler = fun(
    xc: XC,
    _hitboxes: HashMap<ChunkCoord3D, ArrayList<Hitbox>>,
    throwable: ThrowableItem,
    location: Location,
    target: Entity,
    source: Entity,
) {
    // do main damage directly to target
    if ( target is LivingEntity ) {
        if ( target is Player && !xc.canPvpAt(location) ) {
            return
        }

        val damage = damageAfterArmorAndResistance(
            throwable.throwDamage,
            target,
            throwable.throwDamageArmorReduction,
            throwable.throwDamageResistanceReduction,
        )

        if ( target is Player ) {
            // mark player entering combat
            xc.addPlayerToCombatLogging(target)

            // player died -> custom death event
            if ( target.getHealth() > 0.0 && damage >= target.getHealth() ) {
                xc.deathEvents[target.getUniqueId()] = XcPlayerDeathEvent(
                    player = target,
                    killer = source,
                    weaponType = XC.ITEM_TYPE_THROWABLE,
                    weaponId = throwable.itemModelDefault,
                    weaponMaterial = xc.config.materialThrowable,
                )
            }
        }

        target.damage(damage, null)
        target.setNoDamageTicks(0)

        // add fire ticks
        if ( throwable.throwFireTicks > 0 ) {
            target.setFireTicks(throwable.throwFireTicks)
        }
    }

    // TODO: emit event for target hit
    // e.g. for vehicles
}

/**
 * Entity hit handler with damage and a queued explosion at hit location.
 */
public val entityExplosionHitHandler = fun(
    xc: XC,
    hitboxes: HashMap<ChunkCoord3D, ArrayList<Hitbox>>,
    throwable: ThrowableItem,
    location: Location,
    target: Entity,
    source: Entity,
) {
    // do main damage directly to target
    if ( target is LivingEntity ) {
        if ( target is Player && !xc.canPvpAt(location) ) {
            return
        }

        val damage = damageAfterArmorAndResistance(
            throwable.throwDamage,
            target,
            throwable.throwDamageArmorReduction,
            throwable.throwDamageResistanceReduction,
        )

        if ( target is Player ) {
            // mark player entering combat
            xc.addPlayerToCombatLogging(target)

            // player died -> custom death event
            if ( target.getHealth() > 0.0 && damage >= target.getHealth() ) {
                xc.deathEvents[target.getUniqueId()] = XcPlayerDeathEvent(
                    player = target,
                    killer = source,
                    weaponType = XC.ITEM_TYPE_THROWABLE,
                    weaponId = throwable.itemModelDefault,
                    weaponMaterial = xc.config.materialThrowable,
                )
            }
        }

        target.damage(damage, null)
        target.setNoDamageTicks(0)

        // add fire ticks
        if ( throwable.throwFireTicks > 0 ) {
            target.setFireTicks(throwable.throwFireTicks)
        }
    }

    // TODO: emit event for target hit
    // e.g. for vehicles

    // try playing sound
    try {
        location.getWorld()?.playSound(location, throwable.soundExplosion, 1.0f, 1.0f)
    } catch (e: Exception) {
        xc.logger.warning("Failed to play sound ${throwable.soundExplosion} at ${location}")
    }

    // summon explosion effect at location
    xc.createExplosion(
        hitboxes,
        location,
        source,
        throwable.explosionMaxDistance,
        throwable.explosionDamage,
        throwable.explosionRadius,
        throwable.explosionFalloff,
        throwable.explosionArmorReduction,
        throwable.explosionBlastProtReduction,
        throwable.explosionDamageType,
        throwable.explosionBlockDamagePower,
        throwable.explosionFireTicks,
        throwable.explosionParticles,
        XC.ITEM_TYPE_THROWABLE,
        throwable.itemModelDefault,
        xc.config.materialThrowable,
    )
}

/**
 * Empty block hit handler.
 */
public val noBlockHitHandler: ThrowableBlockHitHandler = {_, _, _, _, _, _ -> }

/**
 * Block hit handler that queues explosion at hit location.
 */
public val blockExplosionHitHandler = fun(
    xc: XC,
    hitboxes: HashMap<ChunkCoord3D, ArrayList<Hitbox>>,
    throwable: ThrowableItem,
    location: Location,
    _block: Block,
    source: Entity,
) {
    // try playing sound at location
    try {
        location.getWorld()?.playSound(location, throwable.soundExplosion, 1.0f, 1.0f)
    } catch (e: Exception) {
        xc.logger.warning("Failed to play sound ${throwable.soundExplosion} at ${location}")
    }

    // summon explosion effect at location
    xc.createExplosion(
        hitboxes,
        location,
        source,
        throwable.explosionMaxDistance,
        throwable.explosionDamage,
        throwable.explosionRadius,
        throwable.explosionFalloff,
        throwable.explosionArmorReduction,
        throwable.explosionBlastProtReduction,
        throwable.explosionDamageType,
        throwable.explosionBlockDamagePower,
        throwable.explosionFireTicks,
        throwable.explosionParticles,
        XC.ITEM_TYPE_THROWABLE,
        throwable.itemModelDefault,
        xc.config.materialThrowable,
    )
}
