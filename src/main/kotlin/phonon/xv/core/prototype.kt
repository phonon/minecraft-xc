/**
 * Implements a "prototype" which is a cached base configuration
 * for a vehicle. This is used to create new vehicles. Rough process
 * involves using prototype as base then injecting specific data
 * for vehicle creation.
 * 
 *               Spawning                 Spawning from
 *              from item:                  save data
 * 
 *              prototype                   prototype 
 *                  |                           |     
 *                  |                           |     
 *     item         v              save         v     
 *     data ------> +              data ------> +     
 *                  |                           |     
 *                  |                           |     
 *    player        v                           |     
 *    event ------> +                           |     
 *     data         |                           |     
 *                  |                           |     
 *                  v                           v     
 *              components                  components
 */

package phonon.xv.core

import com.google.gson.JsonObject
import java.nio.file.Path
import java.util.EnumSet
import java.util.UUID
import java.util.logging.Logger
import java.util.LinkedList
import java.util.ArrayDeque
import java.util.Collections
import kotlin.math.max
import org.tomlj.Toml
import org.tomlj.TomlTable
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import phonon.xv.XV
import phonon.xv.component.*
import phonon.xv.util.toml.*


/**
 * VehiclePrototype defines elements in a vehicle. Used as a base
 * object to create new vehicles. The elements form a tree and must
 * always be depth sorted.
 */
public data class VehiclePrototype(
    val name: String,
    // item name
    val itemName: String,
    // item base lore description
    val itemLore: List<String>,
    // tree depth-sorted elements
    val elements: List<VehicleElementPrototype>,
    // index of parent element in elements list
    val parentIndex: IntArray,
    // depth of each element in the tree
    val elementsDepth: IntArray,
    // vehicle element tree max depth
    val maxDepth: Int,
    // vehicle spawn time in seconds
    val spawnTimeSeconds: Double = 0.0,
    // vehicle despawn time in seconds
    val despawnTimeSeconds: Double = 0.0,
) {
    val uuid: UUID = UUID.randomUUID()

    // get spawn/despawn time in milliseconds
    val spawnTimeMillis: Long = max(0, (spawnTimeSeconds * 1000).toLong())
    val despawnTimeMillis: Long = max(0, (despawnTimeSeconds * 1000).toLong())

    // root elements have no parent
    val rootElements: List<VehicleElementPrototype> = elements.filter { e -> e.parent == null }.toList()

    // pre-computed children indices for each element
    // (referenced by the element index in elements list)
    val childrenIndices: Array<IntArray>

    init {
        val children = Array<ArrayList<Int>>(elements.size, { ArrayList() })
        for ( (idx, p) in parentIndex.withIndex() ) {
            if ( p != -1 ) {
                children[p].add(idx)
            }
        }
        childrenIndices = Array(children.size, { children[it].toIntArray() })
    }

    /**
     * Convert this prototype to an item stack with given material.
     * Inputs:
     * - material: Material to use for item stack
     * - elements: Optional list of actual vehicle element instances to
     *      serialize. This is so we can re-use this function for both
     *      a default prototype item as well as serializing actual spawned
     *      vehicle instances. If `elements === null` this will use
     *      components data in the element prototypes. If `elements`
     *      specified this will use instance elements components data.
     */
    public fun toItemStack(
        material: Material,
        elements: List<VehicleElement>? = null,
    ): ItemStack {
        val item = ItemStack(material, 1)
        val itemMeta = item.getItemMeta()
        val itemData = itemMeta.getPersistentDataContainer()

        // attach prototype name
        itemData.set(ITEM_KEY_PROTOTYPE, PersistentDataType.STRING, this.name)

        // item name
        itemMeta.setDisplayName(this.itemName)

        // item lore (initialized with base prototype lore)
        val itemLore = ArrayList<String>(this.itemLore)
        
        // attach element data
        val elementsData = itemData.adapterContext.newPersistentDataContainer()
        for ( elem in this.elements ) {
            val elemData = elementsData.adapterContext.newPersistentDataContainer()
            elem.components.toItemData(itemMeta, itemLore, elemData)
            elementsData.set(elem.itemKey(), PersistentDataType.TAG_CONTAINER, elemData)
        }
        itemData.set(ITEM_KEY_ELEMENTS, PersistentDataType.TAG_CONTAINER, elementsData)

        itemMeta.setLore(itemLore)
        item.setItemMeta(itemMeta)
        return item
    }

    companion object {
        /**
         * Tries to create a vehicle prototype from a list of unsorted
         * element prototypes. This returns null if any cycle is detected
         * between elements. This uses Kahn's algorithm to sort the elements
         * topologically, so that we can sort and detect cycles at same time
         * (even though topological sort normally unneeded since only 1 parent
         * per node).
         */
        public fun fromUnsortedElements(
            name: String,
            itemName: String,
            itemLore: List<String>,
            unsortedElements: List<VehicleElementPrototype>,
            spawnTimeSeconds: Double,
            despawnTimeSeconds: Double,
            logger: Logger? = null,
        ): VehiclePrototype? {
            
            // build map from name -> unsorted index
            // also validates and makes sure no duplicate names
            val nameToIndex = HashMap<String, Int>()
            for ( (idx, e) in unsortedElements.withIndex() ) {
                if ( nameToIndex.put(e.name, idx) !== null ) { // fails if name already in set
                    logger?.warning("VehiclePrototype.fromUnsortedElements: duplicate element name ${e.name} while creating prototype ${name}")
                    return null
                }
            }

            // build parent-children adjacency list as arrays, each index
            // 'n' corresponds to the element at unsortedElements[n]'s parent
            // and children:
            //
            // parents =  [ - ]   [ 0 ] [ 0 ] ...
            // children = [[1,2]] [[] ] [[] ] ...
            //
            //              ^             ^
            //              |             |
            //            node0          node2
            //        parent = -1        parent = 0
            //        children = [1,2]   children = []
            val children = Array<ArrayList<Int>>(unsortedElements.size, { ArrayList() })
            val unsortedParents = IntArray(unsortedElements.size, { -1 })
            for ( (idx, e) in unsortedElements.withIndex() ) {
                val parentName = e.parent
                if ( parentName != null ) {
                    val parentIdx = nameToIndex[parentName]
                    if ( parentIdx == null ) {
                        logger?.warning("VehiclePrototype.fromUnsortedElements: element ${e.name} has parent ${parentName} which does not exist in prototype ${name}")
                        return null
                    }
                    unsortedParents[idx] = parentIdx
                    children[parentIdx].add(idx)
                }
            }

            // first topologically sort using kahn's algorithm
            // (in retrospect, unnecessary since we only allow 1 parent,
            // but at least this is non-recursive)

            // queue of nodes with no incoming edges (e.g. no children)
            val queue = ArrayDeque<Int>()
            for ( (idx, c) in children.withIndex() ) {
                if ( c.isEmpty() ) {
                    queue.add(idx)
                }
            }

            // make sure queue size > 0, otherwise no root elements
            if ( queue.isEmpty() ) {
                logger?.warning("VehiclePrototype.fromUnsortedElements: no root elements in prototype ${name}")
                return null
            }
            
            // sorted elements and sorted parents
            val sorted = ArrayList<VehicleElementPrototype>(unsortedElements.size)
            val parents = ArrayList<Int>(unsortedElements.size)

            while ( !queue.isEmpty() ) {
                val n = queue.removeFirst()
                sorted.add(unsortedElements[n])
                parents.add(unsortedParents[n])

                // remove node n from tree
                val parentIdx = unsortedParents[n]
                if ( parentIdx != -1 ) {
                    children[parentIdx].remove(n) // removes n, not the index n

                    // if parent has no more children, add to queue
                    if ( children[parentIdx].isEmpty() ) {
                        queue.add(parentIdx)
                    }
                }
            }
            
            // if we didn't add all elements to sorted, then there was a cycle
            if ( sorted.size != unsortedElements.size ) {
                logger?.warning("VehiclePrototype.fromUnsortedElements: cycle detected in prototype ${name} (sorted.size ${sorted.size} != unsortedElements.size ${unsortedElements.size}})")
                return null
            }
            
            // reverse sorted list so that it iterates from parents to children
            Collections.reverse(sorted)
            Collections.reverse(parents)

            // re-order topologically sorted list to be tree depth-sorted
            // just iterate and increment depth of parent
            val depth = IntArray(unsortedElements.size, { 0 })
            var maxDepth = 0
            for ( (idx, p) in parents.withIndex() ) {
                if ( p != -1 ) {
                    depth[idx] = depth[p] + 1
                    maxDepth = max(maxDepth, depth[idx])
                }
            }
            
            // scatter the topologically sorted elements into depth-sorted bin indices
            // 1. create bin sizes and current bin indices 
            // 2. scatter elements into bin at offset, then increment offset
            val depthSortedIndices = IntArray(unsortedElements.size, { -1 })
            val depthSortedParents = IntArray(unsortedElements.size, { -1 })
            val depthSortedDepths = IntArray(unsortedElements.size, { -1 })
            val depthBinSizes = IntArray(maxDepth + 1, { 0 })
            val depthBinOffsets = IntArray(maxDepth + 1, { 0 })
            for ( d in depth ) {
                depthBinSizes[d] += 1
            }
            for ( d in 1..maxDepth ) {
                depthBinOffsets[d] = depthBinOffsets[d-1] + depthBinSizes[d-1]
            }
            for ( (idx, d) in depth.withIndex() ) {
                val i = depthBinOffsets[d] // output scatter index
                depthSortedIndices[i] = idx
                depthSortedParents[i] = parents[idx]
                depthSortedDepths[i] = d

                depthBinOffsets[d] += 1
            }

            // use depth sorted indices to scatter sort elements
            val depthSortedElements = depthSortedIndices.map { unsortedElements[it] }
            
            return VehiclePrototype(
                name,
                itemName,
                itemLore,
                depthSortedElements,
                depthSortedParents,
                depthSortedDepths,
                maxDepth,
                spawnTimeSeconds,
                despawnTimeSeconds,
            )
        }
        
        /**
         * Try to load a vehicle prototype from a toml file.
         * If any of the internal elements fails to parse, this will
         * print an error and return a null.
         */
        public fun fromTomlFile(source: Path, logger: Logger? = null): VehiclePrototype? {
            try {
                val toml = Toml.parse(source)

                // vehicle prototype name
                val name = toml.getString("name") ?: ""

                // item properties
                val itemName = toml.getString("item_name") ?: name
                val itemLore = toml.getArrayOrEmpty("item_lore").toList().map { it.toString() }
                val spawnTimeSeconds = toml.getNumberAs<Double>("spawn_time") ?: 4.0
                val despawnTimeSeconds = toml.getNumberAs<Double>("despawn_time") ?: 4.0

                // vehicle elements
                val unsortedElements = ArrayList<VehicleElementPrototype>()

                // if this contains an elements table, parse each element
                // else, parse entire doc as single toml table
                toml.getArray("elements")?.let { elems ->
                    for ( i in 0 until elems.size() ) {
                        unsortedElements.add(VehicleElementPrototype.fromToml(elems.getTable(i), vehicleName = name))
                    }
                } ?: unsortedElements.add(VehicleElementPrototype.fromToml(toml, vehicleName = name))

                return VehiclePrototype.fromUnsortedElements(
                    name,
                    itemName,
                    itemLore,
                    unsortedElements,
                    spawnTimeSeconds,
                    despawnTimeSeconds,
                )
            } catch (e: Exception) {
                logger?.warning("Failed to parse landmine file: ${source.toString()}, ${e}")
                e.printStackTrace()
                return null
            }
        }
    }
}

/**
 * VehicleElementPrototype defines a vehicle element's initial components.
 * Contains all possible components, but only the ones in layout should
 * be non-null.
 */
public data class VehicleElementPrototype(
    val name: String,
    val parent: String?,
    val vehicleName: String,
    val layout: EnumSet<VehicleComponentType>,
    val components: VehicleComponents,
) {
    /**
     * Return an item namespace key.
     */
    fun itemKey(): NamespacedKey {
        return NamespacedKey("xv", this.name)
    }

    companion object {
        public fun fromToml(toml: TomlTable, logger: Logger? = null, vehicleName: String): VehicleElementPrototype {
            // element built-in properties
            val name = toml.getString("name") ?: ""
            val parent = toml.getString("parent")
            
            // load components from toml
            val components = VehicleComponents.fromToml(toml, logger)
            
            return VehicleElementPrototype(
                name,
                parent,
                vehicleName,
                components.layout,
                components,
            )
        }
    }
}