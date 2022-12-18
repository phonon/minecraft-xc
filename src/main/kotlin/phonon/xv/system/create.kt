package phonon.xv.system

import com.google.gson.JsonObject
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import phonon.xv.XV
import phonon.xv.core.*
import java.util.Queue
import java.util.Stack

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
    val location: Location,
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

    // recursive inline function
    fun buildElement(prototype: VehicleElementPrototype): VehicleElement {
        val childrenElts = ArrayList<VehicleElement>()
        // build children first
        for ( childPrototype in prototype.children!! ) {
            val elt = buildElement(childPrototype)
            childrenElts.add(elt)
        }
        val id = componentStorage.lookup[prototype.layout]!!.newId()
        val elt = VehicleElement(
            "${prototype.vehicleName}.${prototype.name}.${id}",
            id,
            prototype,
            childrenElts.toTypedArray()
        )
        // go for another pass thru and set parent of children
        for ( child in childrenElts ) {
            child.parent = elt
        }
        return elt
    }

    while ( requests.isNotEmpty() ) {
        val (
            prototype,
            reason,
            location,
            player,
            item,
            json,
        ) = requests.remove()

        // creation item meta and persistent data container
        val itemMeta = item?.itemMeta
        val itemData = itemMeta?.persistentDataContainer

        // inject creation properties into all element prototypes
        val elementPrototypes: Array<VehicleElementPrototype> = prototype.elements.map { elemPrototype ->
            when ( reason ) {
                CreateVehicleReason.NEW -> {
                    var proto = elemPrototype
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
    
                CreateVehicleReason.LOAD -> {
                    var proto = elemPrototype
                    // TODO: inject json load properties
                    proto
                }
            }
        }.toTypedArray()

        // TODO: for elements with components that have armorstands,
        // add entity -> element mapping
        // for ( elem in elementPrototypes ) {
        //     if ( elem.layout.contains(VehicleComponentType.MODEL) ) {
        //         val armorstand = elem.model?.armorstand?.let { armorstand -> 
        //             entityVehicleData[armorstand.uniqueId] = EntityVehicleData(
        //                 elem.id,
        //                 elem.layout(),
        //                 VehicleComponentType.MODEL
        //             )
        //         }
        //     }
        // }

        val vehicleId = xv.vehicleStorage.newId()
        val elements = HashSet<VehicleElement>(prototype.elements.size)

        val stack = Stack<VehicleElement>()
        for ( rootPrototype in prototype.rootElements ) {
            val rootElt = buildElement(rootPrototype)
            // traverse tree and add elts
            stack.push(rootElt)
            while ( !stack.isEmpty() ) {
                val elt = stack.pop()
                elements.add(elt)
                for ( child in elt.children ) {
                    stack.push(child)
                }
            }
        }

        val vehicle = Vehicle(
            "${prototype.name}${vehicleId}",
            vehicleId,
            prototype,
            elements.toTypedArray()
        )
        xv.uuidToVehicle[vehicle.uuid] = vehicle

        // for ( elt in vehicle.elements ) {
        //     injectComponents(componentStorage, xv.entityVehicleData, elt, req)
        // }

        // test stuff
        player?.sendMessage("Created your vehicle at x:${location.x} y:${location.y} z:${location.z}")
    }


}