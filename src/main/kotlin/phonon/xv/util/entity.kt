/**
 * Utility functions for entities.
 */

package phonon.xv.util.entity

import java.util.UUID
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.persistence.PersistentDataType

@Suppress("Deprecated")
public val entityReassociationKey = NamespacedKey("xv", "element_uuid")

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