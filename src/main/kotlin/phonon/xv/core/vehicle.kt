/**
 * Vehicle object and vehicle element definitions.
 * VehicleElements form a tree of nodes containing a unique set of components.
 * Vehicles are a set of all elements in the tree and the root elements.
 */

package phonon.xv.core

import java.util.EnumSet
import java.util.UUID
import org.bukkit.NamespacedKey
import com.google.gson.JsonObject

// namespaced keys for storing vehicle data in mineman ItemStack
internal val ITEM_KEY_PROTOTYPE = NamespacedKey("xv", "prototype")
internal val ITEM_KEY_ELEMENTS = NamespacedKey("xv", "elements")

// namespaced keys for storing vehicle data in mineman Entity
internal val ENTITY_KEY_COMPONENT = NamespacedKey("xv", "component")

/**
 * Vehicle id is just wrapper for Int
 */
typealias VehicleId = Int

public const val INVALID_VEHICLE_ID: VehicleId = -1

/**
 * Vehicle element id is global element id across all vehicles.
 */
typealias VehicleElementId = Int

public const val INVALID_VEHICLE_ELEMENT_ID: VehicleElementId = -1

/**
 * Vehicle is just a set of vehicle elements. The elements hold
 * component references accessed via element id. Vehicle links
 * together elements to ensure engine can manage/remove all
 * elements together.
 */
public data class Vehicle(
    val name: String,
    // for persistence, static across restarts
    val uuid: UUID,
    // integer id, may vary across restarts
    val id: VehicleId,
    // prototype contains common shared base properties and
    // elements tree hierarchy
    val prototype: VehiclePrototype,
    // elements in this vehicle
    val elements: List<VehicleElement>,
) {
    public val rootElements: List<VehicleElement>
    
    init {
        // find root elements from elements without parents
        rootElements = elements.filter { it.parent == null }
    }

    /**
     * Serialize vehicle into json.
     */
    fun toJson(): JsonObject {
        val json = JsonObject()
        json.addProperty("uuid", this.uuid.toString())
        json.addProperty("prototype", this.prototype.name)

        val elementsJson = JsonObject()
        for ( elem in this.elements ) {
            try {
                elementsJson.add(elem.prototype.name, elem.toJson())
            } catch ( err: Exception ) {
                err.printStackTrace()
            }
        }
        json.add("elements", elementsJson)
        
        return json
    }
}

/**
 * Vehicle elements are a set of unique components.
 * Elements are also nodes that form a N-ary tree. 
 */
public data class VehicleElement(
    val name: String,
    // for persistence, static across restarts
    val uuid: UUID,
    // integer id, may vary across restarts
    val id: VehicleElementId,
    // Reference to prototype, contains base element properties.
    val prototype: VehicleElementPrototype,
    // EnumSet enforces only at most 1 of any component type.
    // Adding this set here simplifies deleting vehicle element.
    // This is similar to a "bitset" ECS data layout.
    val layout: EnumSet<VehicleComponentType>,
    // components in this element
    val components: VehicleComponents,
) {
    // parent and children hierarchy set lazily after creation
    var parent: VehicleElement? = null
        internal set
    
    var children: List<VehicleElement> = listOf()
        internal set

    /**
     * Serialize vehicle into json.
     */
    fun toJson(): JsonObject {
        val json = JsonObject()
        json.addProperty("uuid", this.uuid.toString())
        json.add("components", components.toJson())
        return json
    }
}

/**
 * Data linking entity with its vehicle and vehicle element.
 */
public data class EntityVehicleData(
    // vehicle id
    val vehicle: Vehicle,
    // vehicle element in vehicle
    val element: VehicleElement,
    // specific component this entity is linked to
    val componentType: VehicleComponentType,
)

/**
 * Intermediate class during vehicle creation. Common interface for creating
 * vehicles from different systems (e.g. in-game spawning, json deserialization
 * etc.). Contains final customized components from creation process and
 * creation process dependent properties (e.g. UUIDs).
 */
public data class VehicleBuilder(
    // prototype contains common shared base properties and
    // elements tree hierarchy
    val prototype: VehiclePrototype,
    // for persistence, static across restarts
    val uuid: UUID,
    // elements in this vehicle
    val elements: List<VehicleElementBuilder>,
)

/**
 * Intermediate class during vehicle creation. Common interface for creating
 * vehicle elements from different systems (e.g. in-game spawning, json
 * deserialization, etc.). Contains final customized components from creation
 * process and creation process dependent properties (e.g. UUIDs).
 */
public data class VehicleElementBuilder(
    // Reference to prototype, contains base element properties.
    val prototype: VehicleElementPrototype,
    // for persistence, static across restarts
    val uuid: UUID,
    // final customized components for this element
    val components: VehicleComponents,
)