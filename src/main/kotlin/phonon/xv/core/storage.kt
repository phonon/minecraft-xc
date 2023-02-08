

package phonon.xv.core

import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


public const val MAX_VEHICLE_ELEMENTS: Int = 10000


public class VehicleStorage(
    val maxVehicles: Int
): Iterable<Vehicle> {
    // fixed-size lookup map VehicleId => Vehicle
    private val lookup: Array<Vehicle?> = Array(maxVehicles) { _ -> null }
    // free ids stack
    private val freeIds: ArrayDeque <Int> = ArrayDeque()
    // count of vehicles in storage
    public var size: Int = 0
        private set
    // element id => owning vehicle id
    private val elementToVehicle: HashMap<VehicleElementId, VehicleId> = HashMap()

    init {
        // initialize free ids stack
        for ( i in maxVehicles - 1 downTo 0 ) {
            freeIds.push(i)
        }
    }

    /**
     * Clear the vehicle storage back to initial state
     */
    internal fun clear() {
        // clear vehicles lookup
        for ( i in lookup.indices ) {
            lookup[i] = null
        }
        // initialize free ids stack
        for ( i in maxVehicles - 1 downTo 0 ) {
            freeIds.push(i)
        }
        elementToVehicle.clear()
        size = 0
    }

    /**
     * Get a vehicle by its id if it exists.
     */
    fun get(id: VehicleId): Vehicle? {
        return this.lookup[id]
    }

    /**
     * Get new id from stack of free ids, increments size of storage.
     * Returns INVALID_VEHICLE_ID if no free ids are available.
     */
    private fun newId(): VehicleId {
        // no freeIds between index 0 and size
        return if ( freeIds.isEmpty() ) {
            INVALID_VEHICLE_ID
        } else {
            size += 1
            freeIds.pop()
        }
    }

    /**
     * Mark id as free for use and remove ref from array if it exists.
     */
    internal fun free(id: VehicleId) {
        if ( lookup[id] !== null ) {
            lookup[id] = null
            freeIds.push(id)
            size -= 1
        }
    }

    internal fun getOwningVehicle(id: VehicleElementId): VehicleId? = elementToVehicle[id]

    /**
     * Inserts a vehicle into this storage from its prototype
     * and final created elements.
     */
    fun insert(
        uuid: UUID,
        prototype: VehiclePrototype,
        elements: List<VehicleElement>,
    ): VehicleId {
        val id = this.newId()
        if ( id == INVALID_VEHICLE_ID ) {
            return INVALID_VEHICLE_ID
        }
        val vehicle = Vehicle(
            "${prototype.name}.${id}",
            id,
            prototype,
            elements,
            uuid
        )
        this.lookup[id] = vehicle
        // TODO these keys will need to be freed upon deletion
        elements.forEach {
            elementToVehicle[it.id] = vehicle.id
        }

        return id
    }

    /**
     * Iterator for non-null values of lookup array.
     * Used for iterating over vehicle elements when
     * saving all vehicles.
     */
    override fun iterator(): Iterator<Vehicle> {
        return VehicleStorageIterator(this)
    }

    inner class VehicleStorageIterator(
            val storage: VehicleStorage
    ): Iterator<Vehicle> {
        var nextIdx = 0
        init {
            while ( nextIdx < lookup.size && lookup[nextIdx] === null ) {
                nextIdx++
            }
        }

        override fun hasNext(): Boolean {
            return nextIdx < lookup.size
        }

        override fun next(): Vehicle {
            val retVal = lookup[nextIdx++]
            while ( nextIdx < lookup.size && lookup[nextIdx] === null ) {
                nextIdx++
            }
            return retVal!!
        }

    }
}

public class ComponentsStorage {
    // layout enum set => storage for lookup
    public val lookup: HashMap<EnumSet<VehicleComponentType>, ArchetypeStorage> = HashMap()
    
    // flattened array of archetypes (same objects as in lookup),
    // indices have no meaning, for fast linear iteration across all archetypes
    public val archetypes: ArrayList<ArchetypeStorage> = ArrayList()

    // Cache for mapping components enum set => all archetypes that
    // contain the components set. Used for iterator queries.
    private val matchingArchetypesCache: HashMap<EnumSet<VehicleComponentType>, List<ArchetypeStorage>> = HashMap()

    /**
     * Add a new archetype to the storage based on its components set,
     * if it does not exist.
     */
    public fun addLayout(layout: EnumSet<VehicleComponentType>) {
        if ( !this.lookup.containsKey(layout) ) {
            val archetype = ArchetypeStorage(layout, MAX_VEHICLE_ELEMENTS)
            this.archetypes.add(archetype)
            this.lookup[layout] = archetype
        }
    }

    /**
     * Delete all component + archetype storages.
     * Make sure there are no dangling vehicles before calling.
     */
    public fun clear() {
        lookup.clear()
        archetypes.forEach { it.clear() }
        archetypes.clear()
        matchingArchetypesCache.clear()
        // TODO
        // I imagine there's some extra stuff that needs to be done
        // to clean up each ArchetypeStorage
    }

    /**
     * Get list of all archetypes that contain the components set.
     * i.e. all archetypes that are a superset of the components set.
     *     Example:
     *     - Archetype 1: [A, B, C]
     *     - Archetype 2: [A, B]
     *     - Archetype 3: [A, C]
     *     getMatchingArchetypes([A, B]) => [Archetype 1, Archetype 2]
     * This is used for iterator queries. The matching archetypes are
     * cached when this is called.
     */
    public fun getMatchingArchetypes(components: EnumSet<VehicleComponentType>): List<ArchetypeStorage> {
        return this.matchingArchetypesCache.getOrPut(components) {
            // if not in cache, runs filter to find matching archetypes,
            // this result is stored in cache
            this.archetypes.filter { it.layout.containsAll(components) }
        }
    }
}
