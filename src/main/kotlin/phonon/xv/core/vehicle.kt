/**
 * Vehicle object and vehicle element definitions.
 * VehicleElements form a tree of nodes containing a unique set of components.
 * Vehicles are a set of all elements in the tree and the root elements.
 */

package phonon.xv.core

import java.util.EnumSet

// Vehicle id is just wrapper for Int
typealias VehicleId = Int

public const val INVALID_VEHICLE_ID: VehicleId = -1

// Vehicle element id is just wrapper for Int
// this is unique in the ArchetypeStorage that it is stored in
typealias VehicleElementId = Int

// Constant placeholder to indicate invalid id (equivalent to a null)
public const val INVALID_ELEMENT_ID: VehicleElementId = -1

/**
 * Vehicle is just a set of vehicle elements. The elements hold
 * component references accessed via element id. Vehicle links
 * together elements to ensure engine can manage/remove all
 * elements together.
 */
public data class Vehicle(
        val name: String,
        val id: VehicleId,
        val prototype: VehiclePrototype,
        val elements: Array<VehicleElement>
) {
    public val rootElements: Array<VehicleElement>
    
    init {
        // find root elements from elements without parents
        rootElements = elements.filter { it.parent == null }.toTypedArray()
    }
}

/**
 * Vehicle elements are a set of unique components.
 * Elements are also nodes that form a N-ary tree. 
 */
public data class VehicleElement(
    val name: String,
    val id: VehicleElementId,
    // EnumSet enforces only at most 1 of any component type.
    // Adding this set here simplifies deleting vehicle element.
    // This is similar to a "bitset" ECS data layout.
    val prototype: VehicleElementPrototype,
    // Element parent-child tree hierarchy
    val children: Array<VehicleElement>,
) {
    // parents are set lazily, but are set immediately after init
    var parent: VehicleElement? = null
        internal set

    fun layout(): EnumSet<VehicleComponentType> = prototype.layout
}

/**
 * Data linking entity with its vehicle element.
 */
public data class EntityVehicleData(
    val elementId: VehicleElementId,
    val layout: EnumSet<VehicleComponentType>,
    val componentType: VehicleComponentType,
)