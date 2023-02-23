/**
 * Contains systems for creating and destroying vehicles.
 * 
 * These are separate from "spawn" and "despawn" which involves waiting
 * and progress bar. These are the actual creation and destruction.
 * 
 * Destroy process is final deletion of vehicle and all its elements.
 * A destroy request occurs after despawn or after a vehicle is killed
 * and is finalized when this system runs.
 */
package phonon.xv.system

import java.util.Stack
import java.util.Queue
import java.util.UUID
import com.google.gson.JsonObject
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import phonon.xv.XV
import phonon.xv.core.ITEM_KEY_ELEMENTS
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.INVALID_VEHICLE_ID
import phonon.xv.core.EntityVehicleData
import phonon.xv.core.Vehicle
import phonon.xv.core.VehicleBuilder
import phonon.xv.core.VehicleElementBuilder
import phonon.xv.core.VehiclePrototype
import phonon.xv.core.VehicleStorage

/**
 * A request to create a vehicle, containing optional creation sources
 * (player, item, json object, etc.) which are used to gather specific
 * creation parameters.
 */
public data class CreateVehicleRequest(
    val prototype: VehiclePrototype,
    val location: Location? = null,
    val player: Player? = null,
    val item: ItemStack? = null,
)

public data class DestroyVehicleRequest(
    val vehicle: Vehicle,
)

/**
 * Creates vehicles from requests.
 */
public fun XV.systemCreateVehicle(
    componentStorage: ComponentsStorage,
    requests: Queue<CreateVehicleRequest>
) {
    val xv = this // alias

    while ( requests.isNotEmpty() ) {
        val (
            prototype,
            location,
            player,
            item,
        ) = requests.remove()

        if ( xv.vehicleStorage.size >= xv.vehicleStorage.maxVehicles ) {
            xv.logger.severe("Failed to create new vehicle ${prototype.name} at ${location}: vehicle storage full")
            continue
        }
        
        try {
            // creation item meta and persistent data container
            val itemMeta = item?.itemMeta
            val itemData = itemMeta?.persistentDataContainer
            val itemElementsDataContainer = itemData?.get(ITEM_KEY_ELEMENTS, PersistentDataType.TAG_CONTAINER)

            // inject creation properties into all element prototypes
            val elementBuilders: List<VehicleElementBuilder> = prototype.elements.map { elemPrototype ->
                var components = elemPrototype.components.clone()
                val itemElementsData = itemElementsDataContainer?.get(elemPrototype.itemKey(), PersistentDataType.TAG_CONTAINER)

                // item properties stored in item meta persistent data container
                components = if ( itemElementsData !== null ) {
                    components.injectItemProperties(itemElementsData)
                } else {
                    components
                }

                // main spawn player, location, etc. properties
                // player properties (this creates armor stands internally)
                components = components.injectSpawnProperties(location, player)

                VehicleElementBuilder(
                    prototype = elemPrototype,
                    uuid = UUID.randomUUID(),
                    components = components,
                )
            }

            xv.createVehicle(VehicleBuilder(
                prototype = prototype,
                uuid = UUID.randomUUID(),
                elements = elementBuilders,
            ))

            // test stuff
            player?.sendMessage("Created ${prototype.name} at ${location}")
        }
        catch ( e: Exception ) {
            xv.logger.severe("Failed to create vehicle ${prototype.name} at ${location}")
            e.printStackTrace()
        }
    }
}

/**
 * Destroys vehicles from requests.
 */
public fun XV.systemDestroyVehicle(
    vehicleStorage: VehicleStorage,
    componentStorage: ComponentsStorage,
    requests: Queue<DestroyVehicleRequest>
) {
    val xv = this

    while ( requests.isNotEmpty() ) {
        val (vehicle,) = requests.remove()
        xv.deleteVehicle(vehicle)
    }
}