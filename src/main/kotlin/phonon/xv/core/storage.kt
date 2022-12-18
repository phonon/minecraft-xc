

package phonon.xv.core

import java.util.EnumSet
import java.util.ArrayDeque

public const val MAX_VEHICLE_ELEMENTS: Int = 10000

public class VehicleStorage(
    val maxVehicles: Int
) {
    // fixed-size lookup map VehicleId => Vehicle
    private val lookup: Array<Vehicle?> = Array(maxVehicles) { _ -> null }
    // free ids stack
    private val freeIds: ArrayDeque <Int> = ArrayDeque()
    // count of vehicles in storage
    public var size: Int = 0
        private set
    
    init {
        // initialize free ids stack
        for ( i in maxVehicles - 1 downTo 0 ) {
            freeIds.push(i)
        }
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
    internal fun newId(): VehicleId {
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
    internal fun freeId(id: VehicleId) {
        if ( lookup[id] !== null ) {
            lookup[id] = null
            freeIds.push(id)
            size -= 1
        }
    }

    /**
     * Inserts a vehicle into this storage from its prototype
     * and final created elements.
     */
    fun insert(
        prototype: VehiclePrototype,
        elements: List<VehicleElement>,
    ): VehicleId {
        val id = this.newId()
        if ( id == INVALID_VEHICLE_ID ) {
            return INVALID_VEHICLE_ID
        }

        this.lookup[id] = Vehicle(
            "${prototype.name}.${id}",
            id,
            prototype,
            elements,
        )

        return id
    }
}

public class ComponentsStorage {
    // layout enum set => storage for lookup
    public val lookup: HashMap<EnumSet<VehicleComponentType>, ArchetypeStorage> = HashMap()
    
    // just an array of archetypes, indices have no meaning, for iteration
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
