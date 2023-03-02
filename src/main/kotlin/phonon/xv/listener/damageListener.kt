package phonon.xv.listener

import java.util.UUID
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.event.world.EntitiesUnloadEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.persistence.PersistentDataType
import phonon.xc.XC
import phonon.xc.event.XCProjectileDamageEvent
import phonon.xc.event.XCThrowableDamageEvent
import phonon.xc.event.XCExplosionDamageEvent
import phonon.xv.XV
import phonon.xv.core.EntityVehicleData
import phonon.xv.core.VehicleComponentType
import phonon.xv.system.VehicleDamageRequest
import phonon.xv.util.entity.hasVehicleUuid
import phonon.xv.util.entity.reassociateEntities

/**
 * Listener for XC combat plugin events when projectiles or explosions
 * hit vehicles hitboxes. Event listener here must route these events into
 * queues for XV vehicle systems to process damage.
 */
public class DamageListener(val xv: XV): Listener {
    /**
     * When projectile hits vehicle hitbox.
     */
    @EventHandler
    public fun onProjectileDamage(e: XCProjectileDamageEvent) {
        xv.logger.info("Projectile hit vehicle: ${e}")
        val target = e.target
        val vehicleData = xv.entityVehicleData[target.uniqueId]
        if ( vehicleData !== null ) {
            xv.logger.info("Projectile hit vehicle: ${target.type} ${target.uniqueId}")
            xv.damageQueue.add(VehicleDamageRequest(
                vehicle = vehicleData.vehicle,
                element = vehicleData.element,
                damage = e.damage,
                damageType = e.gun.projectileDamageType,
                source = e.source,
                weaponType = XC.ITEM_TYPE_GUN,
                weaponId = e.gun.itemModelDefault,
                weaponMaterial = xv.xc.config.materialGun,
            ))
        }
    }

    /**
     * When throwable item hits vehicle hitbox.
     */
    @EventHandler
    public fun onThrowableDamage(e: XCThrowableDamageEvent) {
        xv.logger.info("Throwable hit vehicle: ${e}")
        val target = e.target
        val vehicleData = xv.entityVehicleData[target.uniqueId]
        if ( vehicleData !== null ) {
            xv.logger.info("Throwable hit vehicle: ${target.type} ${target.uniqueId}")
            xv.damageQueue.add(VehicleDamageRequest(
                vehicle = vehicleData.vehicle,
                element = vehicleData.element,
                damage = e.throwable.throwDamage,
                damageType = e.throwable.throwDamageType,
                source = e.source,
                weaponType = XC.ITEM_TYPE_THROWABLE,
                weaponId = e.throwable.itemModelDefault,
                weaponMaterial = xv.xc.config.materialThrowable,
            ))
        }
    }

    /**
     * When explosion hits vehicle hitbox.
     */
    @EventHandler
    public fun onExplosionDamage(e: XCExplosionDamageEvent) {
        xv.logger.info("Explosion hit vehicle: ${e}")
        val target = e.target
        val vehicleData = xv.entityVehicleData[target.uniqueId]
        if ( vehicleData !== null ) {
            xv.logger.info("Explosion hit vehicle: ${target.type} ${target.uniqueId}")
            xv.damageQueue.add(VehicleDamageRequest(
                vehicle = vehicleData.vehicle,
                element = vehicleData.element,
                damage = e.damage,
                damageType = e.damageType,
                source = e.source,
                weaponType = e.weaponType,
                weaponId = e.weaponId,
                weaponMaterial = e.weaponMaterial,
            ))
        }
    }
}