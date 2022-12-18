/**
 * Interface for a vehicle component.
 */

package phonon.xv.core

import com.google.gson.JsonObject
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer

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
     * During creation, inject player specific properties and generate
     * a new instance of this component.
     */
    fun injectSpawnProperties(
        location: Location,
        player: Player?,
    ): T {
        return this.self()
    }

    /**
     * During creation, inject item specific properties and generate
     * a new instance of this component.
     */
    fun injectItemProperties(
        item: ItemStack,
        itemMeta: ItemMeta,
        itemData: PersistentDataContainer,
    ): T {
        return this.self()
    }

    /**
     * Convert this component to a JSON object for serializing state.
     * Used for saving vehicle state to disk.
     */
    fun toJson(): JsonObject?
}
