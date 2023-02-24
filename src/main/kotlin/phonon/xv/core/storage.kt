package phonon.xv.core

import java.util.UUID
import java.util.EnumSet
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


/**
 * Main vehicle storage.
 */
public class VehicleStorage(
    val maxVehicles: Int
): Iterable<Vehicle> {
    // fixed-size lookup map VehicleId => Vehicle
    private val lookup: Array<Vehicle?> = Array(maxVehicles) { _ -> null }
    // free ids stack
    private val freeIds: ArrayDeque<Int> = ArrayDeque()
    // count of vehicles in storage
    public var size: Int = 0
        private set
    // element uuid => owning vehicle id
    private val elementToVehicle: HashMap<UUID, VehicleId> = HashMap()

    init {
        // initialize free ids stack
        for ( i in maxVehicles - 1 downTo 0 ) {
            freeIds.addLast(i)
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
            freeIds.addLast(i)
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
     * Returns true if there is space for a new vehicle.
     */
    fun hasSpace(): Boolean {
        return size < maxVehicles
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
            freeIds.removeLast()
        }
    }

    /**
     * Mark id as free for use and remove ref from array if it exists.
     */
    internal fun free(id: VehicleId) {
        if ( lookup[id] !== null ) {
            lookup[id] = null
            freeIds.addLast(id)
            size -= 1
        }
    }

    /**
     * Get owning vehicle of element if it exists.
     */
    internal fun getOwningVehicle(element: VehicleElement): Vehicle? {
        val vehicleId = elementToVehicle[element.uuid]
        return if ( vehicleId == null ) {
            null
        } else {
            lookup[vehicleId]
        }
    }

    /**
     * Inserts a vehicle into this storage from its prototype
     * and final created elements.
     */
    fun insert(
        uuid: UUID,
        prototype: VehiclePrototype,
        elements: List<VehicleElement>,
    ): Vehicle? {
        val id = this.newId()
        if ( id == INVALID_VEHICLE_ID ) {
            return null
        }
        val vehicle = Vehicle(
            "${prototype.name}.${id}",
            uuid,
            id,
            prototype,
            elements,
        )
        this.lookup[id] = vehicle
        // TODO these keys will need to be freed upon deletion
        elements.forEach {
            elementToVehicle[it.uuid] = vehicle.id
        }

        return vehicle
    }

    /**
     * Remove a vehicle from the storage.
     */
    fun remove(vehicle: Vehicle) {
        vehicle.elements.forEach {
            elementToVehicle.remove(it.uuid)
        }
        this.free(vehicle.id)
    }

    /**
     * Return array of all vehicles in storage, by returning a copy of
     * the valid occupied range of the vehicles storage array.
     */
    fun getAllVehicles(): Array<Vehicle?> {
        return lookup.copyOfRange(0, size)
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


/**
 * Vehicle elements and components storage.
 */
public class ComponentsStorage(
    val maxElements: Int,
) {
    // global element ids
    // fixed-size lookup map VehicleId => Vehicle
    private val elementsLookup: Array<VehicleElement?> = Array(maxElements) { _ -> null }

    // free ids stack
    private val freeElementIds: ArrayDeque<VehicleElementId> = ArrayDeque()

    // count of element ids in storage
    public var size: Int = 0
        private set
    
    // layout enum set => storage for lookup
    public val lookup: HashMap<EnumSet<VehicleComponentType>, ArchetypeStorage> = HashMap()
    
    // flattened array of archetypes (same objects as in lookup),
    // indices have no meaning, for fast linear iteration across all archetypes
    public val archetypes: ArrayList<ArchetypeStorage> = ArrayList()

    // Cache for mapping components enum set => all archetypes that
    // contain the components set. Used for iterator queries.
    private val matchingArchetypesCache: HashMap<EnumSet<VehicleComponentType>, List<ArchetypeStorage>> = HashMap()

    init {
        // initialize free element ids stack
        for ( i in maxElements - 1 downTo 0 ) {
            freeElementIds.addLast(i)
        }
    }

    /**
     * Add a new archetype to the storage based on its components set,
     * if it does not exist.
     */
    public fun addLayout(layout: EnumSet<VehicleComponentType>) {
        if ( !this.lookup.containsKey(layout) ) {
            val archetype = ArchetypeStorage(layout, maxElements)
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

    /**
     * Return true if there are at least `count` free ids available.
     */
    public fun hasSpaceFor(count: Int): Boolean {
        return this.freeElementIds.size >= count
    }

    /**
     * Get element by id.
     */
    public fun getElement(id: VehicleElementId): VehicleElement? {
        if ( id < 0 || id >= maxElements || id == INVALID_VEHICLE_ELEMENT_ID ) {
            return null
        }
        return elementsLookup[id]
    }

    /**
     * Get new id from stack of free ids, increments size of storage.
     * Returns INVALID_VEHICLE_ID if no free ids are available.
     */
    private fun newId(): VehicleElementId {
        // no freeIds between index 0 and size
        return if ( freeElementIds.isEmpty() ) {
            INVALID_VEHICLE_ELEMENT_ID
        } else {
            size += 1
            freeElementIds.removeLast()
        }
    }

    /**
     * Mark id as free for use and remove ref from array if it exists.
     */
    internal fun free(id: VehicleElementId) {
        if ( elementsLookup[id] !== null ) {
            elementsLookup[id] = null
            freeElementIds.addLast(id)
            size -= 1
        }
    }

    /**
     * Insert set of vehicle element components into storage.
     * Return lookup VehicleElementId if successful, null otherwise.
     */
    public fun insert(
        components: VehicleComponents,
    ): VehicleElementId {
        val id = this.newId()
        if ( id == INVALID_VEHICLE_ELEMENT_ID ) {
            return INVALID_VEHICLE_ELEMENT_ID
        }

        val resultId = this.lookup[components.layout]!!.insert(id, components)
        if ( resultId == INVALID_VEHICLE_ELEMENT_ID ) {
            // failed to insert, free id
            this.free(id)
            return INVALID_VEHICLE_ELEMENT_ID
        }

        return id
    }
}
