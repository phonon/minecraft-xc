/**
 * FILE IS GENERATED BY CODEGEN SCRIPT, WHICH IMPLEMENTS ALL 
 * COMPONENT TYPES. DO NOT EDIT THIS FILE DIRECTLY.
 * 
 * Archetype style ECS data storage core.
 * 
 * Contains vehicle component enum + interface and archetype storage.
 * 
 * Since component set is finite, just hard-code optional component
 * storages in each archetype. Engine must ensure we only access
 * valid storages in the archetype
 * 
 * See references:
 * https://github.com/amethyst/legion/blob/master/src/internals/storage/archetype.rs
 */

package phonon.xv.core

import java.util.UUID
import java.util.logging.Logger
import java.util.EnumSet
import com.google.gson.JsonObject
import org.tomlj.Toml
import org.tomlj.TomlTable
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import phonon.xc.XC
import phonon.xv.component.*
import java.util.Stack


/**
 * Helper function to push an element into a dense array.
 * Validates that the index is at the end of the array.
 */
private fun <T> ArrayList<T>.pushAtDenseIndex(index: Int, value: T) {
    val storageSize = this.size
    if ( storageSize == index ) {
        this.add(value)
    } else {
        throw IllegalStateException("Archetype storage attempted to insert an element at a dense index larger than current size: ${index} (size = ${storageSize})")
    }
}

/**
 * Helper function to remove an element by swapping it with the last 
 * element in the array.
 */
private fun <T> ArrayList<T>.swapRemove(index: Int) {
    val storageSize = this.size
    if ( storageSize == 1 ) {
        this.removeAt(0)
    } else {
        // swap with last element
        val last = this[storageSize - 1]
        this[index] = last
        this.removeAt(storageSize - 1)
    }
}

/**
 * Note: keep in alphabetical order.
 */
public enum class VehicleComponentType {
    {%- for c in components %}
    {{ c.enum }},
    {%- endfor %}
    ;

    public companion object {
        /**
         * Converts from compile-time generic vehicle component type. 
         */
        public inline fun <reified T: VehicleComponent<T>> from(): VehicleComponentType {
            return when ( T::class ) {
                {%- for c in components %}
                {{ c.classname }}::class -> VehicleComponentType.{{ c.enum }}
                {%- endfor %}
                else -> throw Exception("Unknown component type")
            }
        }
    }
}

// namespaced keys, for use in toItem()
{%- for c in components %}
val {{ c.enum }}_KEY = NamespacedKey("xv", "{{ c.config_name }}")
{%- endfor %}

/**
 * VehicleComponents contains set of all possible components for a vehicle
 * element and a layout EnumSet that indicates the non-null components.
 */
public data class VehicleComponents(
    val layout: EnumSet<VehicleComponentType>,
    {%- for c in components %}
    val {{ c.storage }}: {{ c.classname }}? = null,
    {%- endfor %}
) {
    /**
     * Deep-clones all components in this components set.
     */
    fun clone(): VehicleComponents {
        return VehicleComponents(
            layout,
            {%- for c in components %}
            {{ c.storage }} = {{ c.storage }}?.deepclone(),
            {%- endfor %}
        )
    }

    /**
     * During creation, inject player specific properties and generate
     * a new instance of components. Delegates injecting property
     * effects to each individual component.
     */
    fun injectSpawnProperties(
        location: Location?,
        player: Player?,
    ): VehicleComponents {
        return copy(
            {%- for c in components %}
            {{ c.storage }} = {{ c.storage }}?.injectSpawnProperties(location, player),
            {%- endfor %}
        )
    }

    /**
     * During creation, inject item specific properties and generate
     * a new instance of this component. Delegates injecting property
     * effects to each individual component.
     */
    fun injectItemProperties(
        itemData: PersistentDataContainer
    ): VehicleComponents {
        return copy(
            {%- for c in components %}
            {{ c.storage }} = {{ c.storage }}?.injectItemProperties(itemData.get({{ c.enum }}_KEY, PersistentDataType.TAG_CONTAINER)),
            {%- endfor %}
        )
    }

    /**
     * Serialize element component data into a Minecraft ItemStack item.
     * Delegates to each individual component, which can set properties
     * in item's meta, lore and persistent data container tree.
     * 
     * This mutates and modifies the input itemMeta, itemLore, and itemData
     * with new properties. So, user must be careful when elements overwrite
     * each other's properties.
     */
    fun toItemData(
        itemMeta: ItemMeta,
        itemLore: ArrayList<String>,
        itemData: PersistentDataContainer,
    ) {
        for ( c in layout ) { // only create data containers for components which exist in layout
            when ( c ) {
                {%- for c in components %}
                VehicleComponentType.{{ c.enum }} -> {
                    val componentDataContainer = itemData.adapterContext.newPersistentDataContainer()
                    {{ c.storage }}!!.toItemData(itemMeta, itemLore, componentDataContainer)
                    itemData.set({{ c.enum }}_KEY, PersistentDataType.TAG_CONTAINER, componentDataContainer)
                }
                {%- endfor %}
                null -> {}
            }
        }
    }

    /**
     * Serialize components set into a json object.
     */
    fun toJson(): JsonObject {
        val json = JsonObject()
        for ( c in this.layout ) {
            // serialize component state in json
            when ( c ) {
                {%- for c in components %}
                VehicleComponentType.{{ c.enum }} -> {
                    json.add("{{ c.storage }}", {{ c.storage }}!!.toJson())
                }
                {%- endfor %}
                null -> {}
            }
        }
        return json
    }
    
    /**
     * During creation, inject json specific properties and generate
     * a new instance of this component. Used to load serialized vehicle
     * state from stored json objects. Delegates injecting property
     * effects to each individual component.
     *
     * The json object passed into this function should be the one
     * storing the data for the singular element, NOT the object
     * storing the entire vehicle. See the serde file for more details
     * on schema.
     */
    fun injectJsonProperties(
        json: JsonObject,
    ): VehicleComponents {
        return copy(
            {%- for c in components %}
            {{ c.storage }} = {{ c.storage }}?.injectJsonProperties( json["{{ c.storage }}"]?.asJsonObject ),
            {%- endfor %}
        )
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
    ) {
        for ( c in layout ) {
            when ( c ) {
                {%- for c in components %}
                VehicleComponentType.{{ c.enum }} -> {{ c.storage }}?.afterVehicleCreated(
                    xc=xc,
                    vehicle=vehicle,
                    element=element,
                    entityVehicleData=entityVehicleData,
                )
                {%- endfor %}
                null -> {}
            }
        }
    }

    fun delete(
        xc: XC,
        vehicle: Vehicle,
        element: VehicleElement,
        entityVehicleData: HashMap<UUID, EntityVehicleData>,
        despawn: Boolean,
    ) {
        for ( c in layout ) {
            when ( c ) {
                {%- for c in components %}
                VehicleComponentType.{{ c.enum }} -> {{ c.storage }}?.delete(xc, vehicle, element, entityVehicleData, despawn)
                {%- endfor %}
                null -> {}
            }
        }
    }

    companion object {
        /**
         * Returns an empty vehicle components object.
         */
        public fun empty(): VehicleComponents {
            return VehicleComponents(
                layout = EnumSet.noneOf(VehicleComponentType::class.java),
            )
        }

        /**
         * Parses a vehicle components object from a toml table.
         */
        public fun fromToml(toml: TomlTable, logger: Logger? = null): VehicleComponents {
            // all possible components to be parsed
            {%- for c in components %}
            var {{ c.storage }}: {{ c.classname }}? = null
            {%- endfor %}

            // parse components from matching keys in toml
            val layout = EnumSet.noneOf(VehicleComponentType::class.java)
            val keys = toml.keySet()
            for ( k in keys ) {
                when ( k ) {
                    "name", "parent" -> continue
                    {%- for c in components %}
                    "{{ c.config_name }}" -> {
                        layout.add(VehicleComponentType.{{ c.enum }})
                        {{ c.storage }} = {{ c.classname }}.fromToml(toml.getTable(k)!!, logger)
                    }
                    {%- endfor %}
                    else -> logger?.warning("Unknown key in vehicle element: $k")
                }
            }
            
            return VehicleComponents(
                layout,
                {%- for c in components %}
                {{ c.storage }},
                {%- endfor %}
            )
        }
    }
}

/**
 * Archetype, contains set of possible component storages which store
 * actual vehicle component instances. Components storages are sparse sets.
 * Archetype implements a packed struct-of-arrays format for fast iteration.
 * Element Id used to lookup an instance's packed array index.
 */
public class ArchetypeStorage(
    val layout: EnumSet<VehicleComponentType>,
    val maxElements: Int,
) {
    public var size: Int = 0
        internal set

    // sparse lookup from element id => element components dense index
    // (note vehicle element id is just a typealias for int)
    private val lookup: IntArray = IntArray(maxElements, { _ -> INVALID_VEHICLE_ELEMENT_ID })

    // reverse lookup from dense array index => vehicle element id
    // only internal cuz iterator classes need it
    internal val elements: IntArray = IntArray(maxElements) { _ -> INVALID_VEHICLE_ELEMENT_ID }

    // dense packed components storages
    // only components in layout will be non-null
    {%- for c in components %}
    internal val {{ c.storage }}: ArrayList<{{ c.classname }}>? = if ( layout.contains(VehicleComponentType.{{ c.enum }}) ) ArrayList() else null
    {%- endfor %}

    // public getter "view"s: only expose immutable List interface
    {%- for c in components %}
    public val {{ c.storage }}View: List<{{ c.classname }}>?
        get() = this.{{ c.storage }}
    {%- endfor %}

    /**
     * Get component by id. Returns null if component is not in archetype.
     */
    inline fun <reified T: VehicleComponent<T>> getComponent(id: VehicleElementId): T? {
        val denseIndex = this.getDenseIndex(id)
        if ( denseIndex == INVALID_VEHICLE_ELEMENT_ID ) {
            return null
        }
        
        return when ( T::class ) {
            {%- for c in components %}
            {{ c.classname }}::class -> this.{{ c.storage }}View?.get(denseIndex) as T
            {%- endfor %}
            else -> throw Exception("Unknown component type.")
        }
    }

    /**
     * Get dense index from id.
     */
    public fun getDenseIndex(id: VehicleElementId): Int {
        return this.lookup[id]
    }

    /**
     * Insert components into the archetype. Returns invalid element id
     * if insertion failed.
     */
    public fun insert(
        id: VehicleElementId,
        components: VehicleComponents,
    ): VehicleElementId {
        if ( this.layout != components.layout ) {
            return INVALID_VEHICLE_ELEMENT_ID
        }

        // if id > maxElements, then we can't insert
        if ( id >= maxElements || size >= maxElements ) {
            return INVALID_VEHICLE_ELEMENT_ID
        }

        // check if id is already in use
        if ( lookup[id] != INVALID_VEHICLE_ELEMENT_ID ) {
            return INVALID_VEHICLE_ELEMENT_ID
        }

        // get dense index
        val denseIndex = size
        size += 1
        
        // set sparse <-> dense element mappings
        lookup[id] = denseIndex
        elements[denseIndex] = id

        // push components into storages
        for ( c in components.layout ) {
            when ( c ) {
                {%- for c in components %}
                VehicleComponentType.{{ c.enum }} -> {
                    this.{{ c.storage }}?.pushAtDenseIndex(denseIndex, components.{{ c.storage }}!!)
                }
                {% endfor %}
                null -> {}
            }
        }

        return id
    }

    /**
     * Frees an element from the archetype. Removes all components
     * and frees element id.
     */
    public fun free(id: VehicleElementId, logger: Logger? = null) {
        // validate id is inside storage
        if ( id < 0 || id >= maxElements ) {
            logger?.severe("Archetype.remove() invalid element id out of range: $id")
            return
        }

        // validate id inside dense array == id
        val denseIndex = lookup[id]
        if ( denseIndex == INVALID_VEHICLE_ELEMENT_ID || elements[denseIndex] != id ) {
            logger?.severe("Archetype.remove() element id not in array: $id")
            return
        }

        // swap values in dense array with last element and update lookup index
        // Note: to handle case that denseIndex == lastIndex, the operation order
        // below deliberately sets indices to INVALID_VEHICLE_ELEMENT_ID as
        // the last operation 
        val lastIndex = size - 1
        val swappedId = elements[lastIndex]
        elements[denseIndex] = swappedId
        elements[lastIndex] = INVALID_VEHICLE_ELEMENT_ID
        lookup[swappedId] = denseIndex
        lookup[id] = INVALID_VEHICLE_ELEMENT_ID

        // swap remove elements in component arrays
        for ( c in layout ) {
            when ( c ) {
                {%- for c in components %}
                VehicleComponentType.{{ c.enum }} -> {{ c.storage }}?.swapRemove(denseIndex)
                {%- endfor %}
                null -> {}
            }
        }
        
        // decrement archetype size
        size -= 1
    }
    
    /**
     * Remove all elements from archetype.
     */
    public fun clear() {
        size = 0

        // reset lookup
        for ( i in 0 until maxElements ) {
            lookup[i] = INVALID_VEHICLE_ELEMENT_ID
            elements[i] = INVALID_VEHICLE_ELEMENT_ID
        }

        {%- for c in components %}
        {{ c.storage }}?.clear()
        {%- endfor %}
    }

    public companion object {
        /**
         * Higher order function that returns a function that gets a
         * Vehicle component type's storage within the archetype.
         * Needed to allows compile-time access to a type's component
         * storage in the archetype. Used for generic tuple iterators.
         * 
         * Internally does unsafe cast, since storages may be null.
         * Client caller must make sure archetypes have the storages.
         * 
         * Note: this may be generating a new lambda object at each call.
         * May want to cache the lambda object, or hard-code each function
         * in future.
         */
        @Suppress("UNCHECKED_CAST")
        public inline fun <reified T> accessor(): (ArchetypeStorage) -> List<T> {
            return when ( T::class ) {
                {%- for c in components %}
                {{ c.classname }}::class -> { archetype -> archetype.{{ c.storage }}View as List<T> }
                {%- endfor %}
                else -> throw Exception("Unknown component type")
            }
        }
    }
}
