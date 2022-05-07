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

import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Damageable
import phonon.xc.utils.damage.*

/**
 * Common hit block handler function type. Inputs are
 * (
 *  gun: Gun,
 *  location: Location,
 *  target: Block,
 *  source: Entity,
 * ) -> Unit
 */
typealias GunHitBlockHandler = (Gun, Location, Block, Entity) -> Unit

/**
 * Common hit entity handler function type. Inputs are
 * (
 *  gun: Gun,
 *  location: Location,
 *  target: Entity,
 *  source: Entity,
 * ) -> Unit
 */
typealias GunHitEntityHandler = (Gun, Location, Entity, Entity) -> Unit


/**
 * Map string name to built-in hit block handlers.
 */
public fun getGunHitBlockHandler(name: String): GunHitBlockHandler? {
    return when ( name.lowercase() ) {
        "explosion" -> blockExplosionHitHandler
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
public val noEntityHitHandler: GunHitEntityHandler = {_, _, _, _ -> }

/**
 * Entity hit handler with damage (standard entity damage hit handler).
 */
public val entityDamageHitHandler = fun(
    gun: Gun,
    location: Location,
    target: Entity,
    source: Entity,
) {
    if ( target is LivingEntity && target is Damageable ) {
        val damage = damageAfterArmorAndResistance(
            gun.projectileDamage,
            target,
            gun.projectileArmorReduction,
            gun.projectileResistanceReduction,
        )
        target.damage(damage, source)
        target.setNoDamageTicks(0)
    }
}

/**
 * Entity hit handler with damage and a queued explosion at hit location.
 */
public val entityExplosionHitHandler = fun(
    gun: Gun,
    location: Location,
    target: Entity,
    source: Entity,
) {
    
}


/**
 * Empty entity hit handler.
 */
public val noBlockHitHandler: GunHitBlockHandler = {_, _, _, _ -> }

/**
 * Block hit handler that queues explosion at hit location.
 */
public val blockExplosionHitHandler = fun(
    gun: Gun,
    location: Location,
    block: Block,
    source: Entity,
) {

}