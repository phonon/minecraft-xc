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
import phonon.xv.util.entity.getVehicleUuid
import phonon.xv.util.entity.hasVehicleUuid

public class ArmorstandListener(val xv: XV): Listener {

    // namespaced key to store id for armorstand reassociation. For now
    // this is just for model components
    // note that this is a VehicleElement's UUID, not their integer id.
    // UUIDs are static over restarts, integer IDs may be reassigned.
    val modelReassociationKey = NamespacedKey(xv.plugin, "element_uuid")

    /**
     * When the entities of a chunk are loaded in, check
     * for any armorstands tagged for use in vehicles, and
     * reassociate with the appropriate component. Also delete
     * the stand if its owning element has been deleted.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public fun onArmorstandLoad(event: EntitiesLoadEvent) {
        for ( entity in event.entities ) {
            // check if armorstand and custom tag is there
            if ( entity.type == EntityType.ARMOR_STAND ) {
                val elementUUID = entity.getVehicleUuid()
                if ( elementUUID !== null ) {
                    val vehicleElement = xv.uuidToElement[elementUUID]
                    if ( vehicleElement != null ) {
                        // use archetype storage to set model component field
                        // to point to this armorstand
                        val archetype = xv.storage.lookup[vehicleElement.layout]
                        if ( archetype != null ) {
                            val modelComponent = archetype.getComponent<ModelComponent>(vehicleElement.id)
                            modelComponent?.armorstand = entity as ArmorStand
                        }
                    } else {
                        if ( xv.config.deleteInvalidArmorStands ) {
                            // vehicle element no longer exists, just delete the stand
                            // (only do if config set, by default avoid because any
                            // error in loading that causes armor stand to vehicle
                            // mappings to not be loaded will cause all vehicles to be
                            // marked as invalid and deleted)
                            entity.remove()
                        }
                    }
                }
            }
        }
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