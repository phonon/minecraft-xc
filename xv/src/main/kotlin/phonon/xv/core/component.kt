/**
 * Interface for a vehicle component.
 */

package phonon.xv.core

import java.util.EnumSet
import java.util.UUID
import com.google.gson.JsonObject
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import phonon.xc.XC

/**
 * Component interface. Recursive interface so we can use self
 * as a type, "F-bounded type", see:
 * https://stackoverflow.com/questions/29565043/how-to-specify-own-type-as-return-type-in-kotlin
 * https://stackoverflow.com/questions/2413829/java-interfaces-and-return-types
 */
public interface VehicleComponent<T: VehicleComponent<T>> {
    // Vehicle component type enum.
    val type: VehicleComponentType

    /**
     * Return self as correct type T. Because `this` is VehicleComponent<T>
     * but we want T and java generics cant imply self type.
     * 
     * Each component should trivially implement with:
     *  `override fun self() = this` 
     * 
     * Java moment. >:^(
     */
    fun self(): T 

    /**
     * Create a deep clone of this component. Used when inserting
     * vehicle into archetype. Component must manually implement deep clone
     * so components with objects like `Array` or `IntArray` are properly
     * cloned.
     */
    fun deepclone(): T

    /**
     * During creation, inject spawn specific properties and generate
     * a new instance of this component. Such as spawn location, player
     * who spawned the vehicle, etc.
     */
    fun injectSpawnProperties(
        location: Location?,
        player: Player?,
    ): T {
        return this.self()
    }

    /**
     * During creation, inject item specific properties and generate
     * a new instance of this component. Such as properties stored in
     * the item, such as vehicle skin, health remaining, etc.
     */
    fun injectItemProperties(
        itemData: PersistentDataContainer?,
    ): T {
        return this.self()
    }

    /**
     * During deletion, create a persistent data container that
     * stores the state of this component.
     */
    fun toItemData(
        itemMeta: ItemMeta,
        itemLore: ArrayList<String>,
        itemData: PersistentDataContainer,
    ) {
        return
    }

    /**
     * During creation, inject json specific properties and generate
     * a new instance of this component. Used to load serialized vehicle
     * state from stored json objects.
     */
    fun injectJsonProperties(
        json: JsonObject?,
    ): T {
        return this.self()
    }

    /**
     * Convert this component to a JSON object for serializing state.
     * Used for saving vehicle state to disk.
     */
    fun toJson(): JsonObject? {
        return null
    }
    
    /**
     * During creation, for each component, send post creation properties,
     * for post-processing after the vehicle has been created. Such as
     * setting up entity to vehicle mappings for armor stands.
     */
    fun afterVehicleCreated(
        xc: XC,
        vehicle: Vehicle,
        element: VehicleElement,
        entityVehicleData: HashMap<UUID, EntityVehicleData>,
    ) {}

    /**
     * Handler for 
     */
    fun delete(
        xc: XC,
        vehicle: Vehicle,
        element: VehicleElement,
        entityVehicleData: HashMap<UUID, EntityVehicleData>,
        despawn: Boolean,
    ) {}
}
