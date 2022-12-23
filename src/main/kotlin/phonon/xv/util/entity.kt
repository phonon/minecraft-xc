/**
 * Utility functions for entities.
 */

package phonon.xv.util.entity

import java.util.UUID
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.persistence.PersistentDataType
import phonon.xv.XV
import phonon.xv.component.ModelComponent
import phonon.xv.core.EntityVehicleData
import phonon.xv.core.VehicleComponentType

@Suppress("Deprecated")
public val entityReassociationKey = NamespacedKey("xv", "element_model_uuid")

/**
 * Helper function to set a vehicle element identifying UUID key into
 * an entity's persistent data container.
 */
public fun Entity.setVehicleUuid(uuid: UUID) {
    this.persistentDataContainer.set(
        entityReassociationKey,
        PersistentDataType.STRING,
        uuid.toString(),
    )
}

/**
 * Helper function to get a vehicle element identifying UUID key
 * from its persistent data container.
 */
public fun Entity.getVehicleUuid(): UUID? {
    if ( this.persistentDataContainer.has(entityReassociationKey, PersistentDataType.STRING) ) {
        return UUID.fromString(
            this.persistentDataContainer.get(entityReassociationKey, PersistentDataType.STRING)
        )
    } else {
        return null
    }
}

/**
 * Helper function to check if an entity has a vehicle element UUID
 * identifier. Returns true if present.
 */
public fun Entity.hasVehicleUuid(): Boolean {
    return this.persistentDataContainer.has(entityReassociationKey, PersistentDataType.STRING)
}

/**
 * Reassociate the following entity with the appropriate components
 */
public fun reassociateEntities(xv: XV, entities: Collection<Entity>) {
    entities.forEach { entity ->
        if ( entity.type == EntityType.ARMOR_STAND ) {
            // see the model component class to see the issue w/ this
            val elementUUID = entity.getVehicleUuid()
            if ( elementUUID !== null ) {
                val vehicleElement = xv.uuidToElement[elementUUID]
                val invalid = if ( vehicleElement != null ) {
                    // use archetype storage to set model component field
                    // to point to this armorstand
                    val archetype = xv.storage.lookup[vehicleElement.layout]!!
                    val modelComponent = archetype.getComponent<ModelComponent>(vehicleElement.id)
                    val invalid = if ( modelComponent === null ) {
                        true
                    } else {
                        modelComponent.armorstand = entity as ArmorStand
                        false
                    }
                    invalid
                } else {
                    true
                }
                if ( invalid ) {
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