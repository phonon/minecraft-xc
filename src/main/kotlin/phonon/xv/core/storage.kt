

package phonon.xv.core

import java.util.EnumSet
import java.util.Stack

public const val MAX_VEHICLE_ELEMENTS: Int = 10000

public class VehicleStorage(
        maxVehicles: Int
) {
    // lookup array VehicleId => Vehicle
    // encapsulated to do type safety w/ VehicleId
    private val lookup: Array<Vehicle?> = Array(maxVehicles) { _ -> null }
    // stack to keep track of free id between 0 and size (exclusive)
    private val freeIds: Stack<Int> = Stack()
    // keep track of "size" of arr, equivalent to largest id + 1
    private var size: Int = 0

    fun lookup(id: VehicleId): Vehicle? {
        return this.lookup[id]
    }

    // get new id either from implicit list or
    // -1 if we've reached MAX_VEHICLE_ELEMENTS
    fun newId(): VehicleId {
        // no freeIds between index 0 and size
        return if ( freeIds.isEmpty() ) {
            // we're at max capacity, just return invalid
            if ( size >= MAX_VEHICLE_ELEMENTS ) {
                INVALID_VEHICLE_ID
            } else { // otherwise just use size as index
                size++
            }
        } else {
            size++
            freeIds.pop()
        }
    }

    // mark id as free for use , and remove ref from array
    fun freeId(id: VehicleId) {
        lookup[id] = null
        freeIds.push(id)
    }
}

public class ComponentsStorage {
    // layout enum set => storage for lookup
    public val lookup: HashMap<EnumSet<VehicleComponentType>, ArchetypeStorage> = HashMap()
    // just an array of archetypes, indices have no meaning, for iteration
    public val archetypes: ArrayList<ArchetypeStorage> = ArrayList()

    public fun addLayout(layout: EnumSet<VehicleComponentType>) {
        if ( !this.lookup.containsKey(layout) ) {
            val archetype = ArchetypeStorage(layout, MAX_VEHICLE_ELEMENTS)
            this.archetypes.add(archetype)
            this.lookup[layout] = archetype
        }
    }

    // delete all component + archetype storage
    // make sure you dont have dangling vehicles
    // when you call
    public fun clear() {
        lookup.clear()
        archetypes.forEach { it.clear() }
        archetypes.clear()
        // TODO
        // I imagine there's some extra stuff that needs to be done
        // to clean up each ArchetypeStorage
    }
}
