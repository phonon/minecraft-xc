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
import phonon.xv.core.ENTITY_KEY_COMPONENT
import phonon.xv.core.EntityVehicleData
import phonon.xv.core.VehicleComponentType

/**
 * Key for storing a vehicle element UUID in an entity's persistent
 * data container.
 */
@Suppress("Deprecated")
public val ENTITY_KEY_ELEMENT = NamespacedKey("xv", "element_uuid")

/**
 * Key for storing a vehicle UUID in an entity's persistent
 * data container.
 */
@Suppress("Deprecated")
public val ENTITY_KEY_VEHICLE = NamespacedKey("xv", "vehicle_uuid")

/**
 * Helper function to set a vehicle UUID identifying key into
 * an entity's persistent data container.
 */
public fun Entity.setVehicleUuid(vehicleUuid: UUID, elementUuid: UUID) {
    this.persistentDataContainer.set(
        ENTITY_KEY_VEHICLE,
        PersistentDataType.STRING,
        vehicleUuid.toString(),
    )
    this.persistentDataContainer.set(
        ENTITY_KEY_ELEMENT,
        PersistentDataType.STRING,
        elementUuid.toString(),
    )
}

/**
 * Helper function to get a vehicle element identifying UUID key
 * from its persistent data container.
 */
public fun Entity.getVehicleUuid(): UUID? {
    if ( this.persistentDataContainer.has(ENTITY_KEY_VEHICLE, PersistentDataType.STRING) ) {
        return UUID.fromString(
            this.persistentDataContainer.get(ENTITY_KEY_VEHICLE, PersistentDataType.STRING)
        )
    } else {
        return null
    }
}

/**
 * Helper function to get a vehicle element identifying UUID key
 * from its persistent data container.
 */
public fun Entity.getElementUuid(): UUID? {
    if ( this.persistentDataContainer.has(ENTITY_KEY_ELEMENT, PersistentDataType.STRING) ) {
        return UUID.fromString(
            this.persistentDataContainer.get(ENTITY_KEY_ELEMENT, PersistentDataType.STRING)
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
    return this.persistentDataContainer.has(ENTITY_KEY_VEHICLE, PersistentDataType.STRING)
}

/**
 * Reassociate the following entity with the appropriate components
 */
public fun reassociateEntities(xv: XV, entities: Collection<Entity>) {
    for ( entity in entities ) {
        try {
            val vehicleUuid = entity.getVehicleUuid()
            val elementUuid = entity.getElementUuid()

            val invalid = if ( elementUuid !== null && vehicleUuid !== null ) {
                val vehicle = xv.uuidToVehicle[vehicleUuid]
                val vehicleElement = xv.uuidToElement[elementUuid]
                if ( vehicle !== null && vehicleElement != null ) {
                    val componentName = entity.persistentDataContainer.get(ENTITY_KEY_COMPONENT, PersistentDataType.STRING)
                    if ( componentName !== null ) {
                        val componentType = try {
                            VehicleComponentType.valueOf(componentName)
                        } catch ( err: Exception ) {
                            null
                        }

                        when ( componentType ) {
                            VehicleComponentType.MODEL -> vehicleElement.components.model?.reassociateArmorstand(entity, vehicle, vehicleElement, xv.entityVehicleData)
                            VehicleComponentType.GUN_BARREL -> vehicleElement.components.gunBarrel?.reassociateArmorstand(entity, vehicle, vehicleElement, xv.entityVehicleData)
                            VehicleComponentType.GUN_TURRET -> vehicleElement.components.gunTurret?.reassociateArmorstand(entity, vehicle, vehicleElement, xv.entityVehicleData)
                            else -> {}
                        }
                        false
                    } else {
                        true
                    }
                } else {
                    true
                }
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
        } catch ( err: Exception ) {
            xv.logger.severe("Failed to reassociate XV entity ${entity.getUniqueId()}, ${err.message}")
            err.printStackTrace()
        }
    }
}