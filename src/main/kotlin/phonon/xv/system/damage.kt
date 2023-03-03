package phonon.xv.system

import java.util.Queue
import kotlin.math.max
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import phonon.xc.XC
import phonon.xc.gun.Gun
import phonon.xc.util.death.XcPlayerDeathEvent
import phonon.xc.util.damage.DamageType
import phonon.xv.XV
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.Vehicle
import phonon.xv.core.VehicleElement
import phonon.xv.core.iter.*
import phonon.xv.component.HealthComponent
import phonon.xv.component.VehicleKilledEvent
import phonon.xv.component.SeatsComponent
import phonon.xv.system.DeleteVehicleRequest
import phonon.xv.util.drain

data class VehicleDamageRequest(
    val vehicle: Vehicle,
    val element: VehicleElement,
    val damage: Double,
    val damageType: DamageType,
    val source: Entity?,
    val weaponType: Int,
    val weaponId: Int,
    val weaponMaterial: Material,
)

/**
 * System for handling vehicle damage events.
 */
public fun XV.systemDamage(
    storage: ComponentsStorage,
    damageRequests: Queue<VehicleDamageRequest>,
) {
    val xv = this
    for ( request in damageRequests.drain() ) {
        val (
            vehicle,
            element,
            damage,
            damageType,
            source,
            weaponType,
            weaponId,
            weaponMaterial,
        ) = request

        // get vehicle health component
        val healthComponent = element.components.health
        if ( healthComponent !== null ) {
            val damageFinal = damage * healthComponent.damageMultiplier[damageType]
            healthComponent.current = max(0.0, healthComponent.current - damageFinal)
            
            println("damage ${damageFinal}, health: ${healthComponent.current} / ${healthComponent.max}")
            
            // if vehicle destroyed by this, bind a death event to health component
            if ( healthComponent.current <= 0.0 && healthComponent.deathEvent === null ) {
                healthComponent.deathEvent = VehicleKilledEvent(
                    killer = source,
                    weaponType = weaponType,
                    weaponId = weaponId,
                    weaponMaterial = weaponMaterial,
                )
            }
        }
    }
}
