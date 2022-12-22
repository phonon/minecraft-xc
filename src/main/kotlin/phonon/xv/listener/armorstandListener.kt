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
import phonon.xv.XV
import phonon.xv.component.FuelComponent
import phonon.xv.component.ModelComponent
import phonon.xv.core.EntityVehicleData
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.entity.hasVehicleUuid
import phonon.xv.util.entity.reassociateEntities

public class ArmorstandListener(val xv: XV): Listener {

    /**
     * When the entities of a chunk are loaded in, check
     * for any armorstands tagged for use in vehicles, and
     * reassociate with the appropriate component. Also delete
     * the stand if its owning element has been deleted.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public fun onArmorstandLoad(event: EntitiesLoadEvent) {
        reassociateEntities(xv, event.entities)
    }

    /**
     * Cancel player manipulating plugin vehicle armor stands models,
     * prevents players from removing armor stand model item.
     */
    @EventHandler
    public fun onPlayerArmorStandManipulate(event: PlayerArmorStandManipulateEvent) {
        val entity = event.getRightClicked()
        if ( entity.type == EntityType.ARMOR_STAND && entity.hasVehicleUuid() ) {
            event.setCancelled(true)
        }
    }
}