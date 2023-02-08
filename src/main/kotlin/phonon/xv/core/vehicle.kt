/**
 * Vehicle object and vehicle element definitions.
 * VehicleElements form a tree of nodes containing a unique set of components.
 * Vehicles are a set of all elements in the tree and the root elements.
 */

package phonon.xv.core

import java.util.EnumSet
import java.util.UUID

// Vehicle id is just wrapper for Int
typealias VehicleId = Int

public const val INVALID_VEHICLE_ID: VehicleId = -1

// Vehicle element id is just wrapper for Int
// this is unique in the ArchetypeStorage that it is stored in
typealias VehicleElementId = Int

/**
 * Vehicle is just a set of vehicle elements. The elements hold
 * component references accessed via element id. Vehicle links
 * together elements to ensure engine can manage/remove all
 * elements together.
 */
public data class Vehicle(
    val name: String,
    // integer id, may vary across restarts
    val id: VehicleId,
    // prototype contains common shared base properties and
    // elements tree hierarchy
    val prototype: VehiclePrototype,
    // elements in this vehicle
    val elements: List<VehicleElement>,
    // for persistence, static across restarts
    val uuid: UUID
) {
    public val rootElements: List<VehicleElement>
    
    init {
        // find root elements from elements without parents
        rootElements = elements.filter { it.parent == null }
    }
}

/**
 * Vehicle elements are a set of unique components.
 * Elements are also nodes that form a N-ary tree. 
 */
public data class VehicleElement(
    val name: String,
    // integer id, may vary across restarts
    val id: VehicleElementId,
    // EnumSet enforces only at most 1 of any component type.
    // Adding this set here simplifies deleting vehicle element.
    // This is similar to a "bitset" ECS data layout.
    val layout: EnumSet<VehicleComponentType>,
    // Reference to prototype, contains base element properties.
    val prototype: VehicleElementPrototype,
    // for persistence, static across restarts
    val uuid: UUID,
) {
    // parent and children hierarchy set lazily after creation
    var parent: VehicleElement? = null
        internal set
    
    var children: List<VehicleElement> = listOf()
        internal set
}

/**
 * Data linking entity with its vehicle and vehicle element.
 */
public data class EntityVehicleData(
    // vehicle id
    val vehicleId: VehicleId,
    // vehicle element id in vehicle
    val elementId: VehicleElementId,
    // vehicle element component layout
    val layout: EnumSet<VehicleComponentType>,
    // specific component this entity is linked to
    val componentType: VehicleComponentType,
)