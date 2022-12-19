package phonon.xv.system

import java.util.Queue
import java.util.Stack
import com.google.gson.JsonObject
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import phonon.xv.XV
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.INVALID_VEHICLE_ID
import phonon.xv.core.EntityVehicleData
import phonon.xv.core.Vehicle
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.VehicleElement
import phonon.xv.core.VehicleElementId
import phonon.xv.core.VehicleElementPrototype
import phonon.xv.core.VehiclePrototype
import java.util.UUID

/**
 * Indicates reason for creating a vehicle. Customizes what creation
 * time specific parameters are injected into the vehicle.
 */
public enum class CreateVehicleReason {
    NEW,
    LOAD,
    ;
}

/**
 * A request to create a vehicle, containing optional creation sources
 * (player, item, json object, etc.) which are used to gather specific
 * creation parameters.
 */
public data class CreateVehicleRequest(
    val prototype: VehiclePrototype,
    val reason: CreateVehicleReason,
    val location: Location? = null,
    val player: Player? = null,
    val item: ItemStack? = null,
    val json: JsonObject? = null,
)

// TODO when we create a vehicle its gonna give the player
// a loading bar, but this functionality is common
// to refueling and reloading a cannon. (when we add that)
// where do we put it?

public fun XV.systemCreateVehicle(
    componentStorage: ComponentsStorage,
    requests: Queue<CreateVehicleRequest>
) {
    val xv = this // alias

    while ( requests.isNotEmpty() ) {
        val (
            prototype,
            reason,
            location,
            player,
            item,
            json,
        ) = requests.remove()

        if ( xv.vehicleStorage.size >= xv.vehicleStorage.maxVehicles ) {
            xv.logger.severe("Failed to create new vehicle ${prototype.name} at ${location}: vehicle storage full")
            continue
        }
        
        try {
            // creation item meta and persistent data container
            val itemMeta = item?.itemMeta
            val itemData = itemMeta?.persistentDataContainer

            // inject creation properties into all element prototypes
            val elementPrototypes: List<VehicleElementPrototype> = prototype.elements.map { elemPrototype ->
                var proto = elemPrototype
                when ( reason ) {
                    // spawning a new vehicle ingame from item or command
                    CreateVehicleReason.NEW -> {
                        // inject creation time properties, keep in this order:

                        // item properties stored in item meta
                        proto = if ( item !== null ) {
                            proto.injectItemProperties(item, itemMeta!!, itemData!!)
                        } else {
                            proto
                        }

                        // main spawn player, location, etc. properties
                        // player properties (this creates armor stands internally)
                        proto = proto.injectSpawnProperties(location, player)

                        proto
                    }
                    
                    // loading from serialized json: only inject json properties
                    CreateVehicleReason.LOAD -> {
                        json!!
                        // current json object is the whole vehicle json, we
                        // want just the json object with our element
                        val elemJson = json["elements"]!!
                                .asJsonObject[proto.name]!!
                                .asJsonObject

                        proto = elemPrototype.injectJsonProperties(elemJson)

                        proto
                    }
                }
            }

            // try to insert each prototype into its archetype
            val elementIds: List<VehicleElementId?> = elementPrototypes.map { elemPrototype ->
                componentStorage.lookup[elemPrototype.layout]!!.insert(elemPrototype)
            }
            
            // if any are null, creation failed. remove non-null created elements
            // from their archetypes
            if ( elementIds.any { it === null } ) {
                xv.logger.severe("Failed to create vehicle ${prototype.name} at ${location}")

                elementIds.forEachIndexed { index, id ->
                    if ( id !== null ) {
                        componentStorage.lookup[elementPrototypes[index].layout]?.free(id)
                    } else {
                        xv.logger.severe("Failed to create element ${elementPrototypes[index].name}")
                    }
                }

                // skip this request
                continue
            }

            // create vehicle elements
            val elements = elementIds.mapIndexed { idx, id ->
                VehicleElement(
                    name="${prototype.name}.${elementPrototypes[idx].name}.${id}",
                    id=id!!,
                    layout=elementPrototypes[idx].layout,
                    elementPrototypes[idx]
                )
            }
            
            // set parent/children hierarchy
            for ( (idx, elem) in elements.withIndex() ) {
                val parentIdx = prototype.parentIndex[idx]
                if ( parentIdx != -1 ) {
                    elem.parent = elements[parentIdx]
                }
                
                val childrenIdx = prototype.childrenIndices[idx]
                if ( childrenIdx.isNotEmpty() ) {
                    elem.children = childrenIdx.map { elements[it] }
                }
            }

            val vehicleUuid = when ( reason ) {
                CreateVehicleReason.NEW -> UUID.randomUUID() // generate random uuid if new
                // use existing if load
                CreateVehicleReason.LOAD -> UUID.fromString( json!!["uuid"]!!.asString )
            }
            // insert new vehicle
            val vehicleId = xv.vehicleStorage.insert(
                vehicleUuid,
                prototype=prototype,
                elements=elements,
            )

            // this should never happen, but check
            if ( vehicleId == INVALID_VEHICLE_ID ) {
                xv.logger.severe("Failed to create vehicle ${prototype.name} at ${location}: vehicle storage full")
                // free elements
                elements.forEach { elem -> componentStorage.lookup[elem.layout]?.free(elem.id) }
                continue
            }

            // for elements with components that have armorstands,
            // add entity -> element mapping
            for ( (idx, elem) in elementPrototypes.withIndex() ) {
                if ( elem.layout.contains(VehicleComponentType.MODEL) ) {
                    val armorstand = elem.model?.armorstand?.let { armorstand -> 
                        entityVehicleData[armorstand.uniqueId] = EntityVehicleData(
                            vehicleId,
                            elements[idx].id,
                            elem.layout,
                            VehicleComponentType.MODEL
                        )
                    }
                }
            }

            // test stuff
            player?.sendMessage("Created your vehicle at x:${location}")
        }
        catch ( e: Exception ) {
            xv.logger.severe("Failed to create vehicle ${prototype.name} at ${location}")
            e.printStackTrace()
        }
    }
}