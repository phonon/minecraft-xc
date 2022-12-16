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
import org.bukkit.persistence.PersistentDataType
import phonon.xv.XV
import phonon.xv.component.FuelComponent
import phonon.xv.component.ModelComponent
import phonon.xv.core.EntityVehicleData
import phonon.xv.core.VehicleComponentType

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
            if ( entity is ArmorStand
                    && entity.persistentDataContainer.has(modelReassociationKey, PersistentDataType.STRING) ) {
                val elementUUID = UUID.fromString(
                        entity.persistentDataContainer.get(modelReassociationKey, PersistentDataType.STRING)
                )
                val vehicleElement = xv.uuidToElement[elementUUID]
                if ( vehicleElement != null ) {
                    // use archetype storage to set model component field
                    // to point to this armorstand
                    val archetype = xv.storage.lookup[vehicleElement.layout()]!!
                    val modelComponent = archetype.getComponent<ModelComponent>(vehicleElement.id)!!
                    modelComponent.armorstand = entity
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

    /**
     * When entities of a chunk are unloaded, tag the
     * armorstands with their respective owning
     * element uuid.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public fun onArmorstandUnload(event: EntitiesUnloadEvent) {
        for ( entity in event.entities ) {
            if ( entity.type == EntityType.ARMOR_STAND ) {
                val entityVehicleData = xv.entityVehicleData[entity.uniqueId]
                if ( entityVehicleData !== null ) {
                    val element = xv.storage.lookup[entityVehicleData.layout]!!.lookup(entityVehicleData.elementId)!!
                    if ( entityVehicleData.componentType == VehicleComponentType.MODEL ) {
                        val element = xv.storage.lookup[entityVehicleData.layout]!!.lookup(entityVehicleData.elementId)!!
                        entity.persistentDataContainer.set(
                            modelReassociationKey,
                            PersistentDataType.STRING,
                            element.uuid.toString()
                        )
                    }
                }
            }
        }
    }
}