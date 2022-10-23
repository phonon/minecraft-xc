package phonon.xv.component

import org.bukkit.entity.Entity
import phonon.xv.core.VehicleComponent

/**
 * Represents an ArmorStand model
 */
public data class ModelComponent(
    var armorstand: Entity? = null,
): VehicleComponent {
    
}