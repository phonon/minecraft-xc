/**
 * FILE IS GENERATED BY CODEGEN SCRIPT. DO NOT TOUCH!
 *
 * At some point in the vehicle creation process, we need to
 * construct the component objects and inject them into the
 * applicable archetype storage. This file contains a code-genned
 * contains a singe function that handles this "injection" code,
 * along with the relevant init code that is required by each
 * component. Note that different components will require different
 * kinds of intialization, and thus overrides for specific types
 * are required.
 *
 * To be frank the best location for this function would be in the
 * same file as the create system, but I think it's best to contain
 * all the code-genned stuff in the same file.
 */

package phonon.xv.core

import java.util.UUID
import org.bukkit.entity.ArmorStand
import phonon.xv.component.*
import phonon.xv.system.CreateVehicleReason
import phonon.xv.system.CreateVehicleRequest
import phonon.xv.util.CustomArmorStand
import java.util.EnumSet

/**
 * Handles the "injection" of the components of the provided vehicle
 * element. This function will execute the construction, component-specific
 * init procedures, and registration in the appropriate archetype.
 */
public fun injectComponents(
    storage: ComponentsStorage,
    entityVehicleData: HashMap<UUID, EntityVehicleData>,
    element: VehicleElement,
    req: CreateVehicleRequest
) {
    val prototype = element.prototype
    storage.lookup[prototype.layout]!!.inject(
            element, 
            prototype.fuel, 
            prototype.gunTurret, 
            prototype.health, 
            prototype.landMovementControls, 
            if ( prototype.model == null )
                null
            else if ( req.reason == CreateVehicleReason.NEW) {
                val armorstand: ArmorStand = CustomArmorStand.create(req.location.world, req.location)
                armorstand.setGravity(false)
                armorstand.setVisible(true)
                // armorstand.getEquipment()!!.setHelmet(createModel(Tank.modelMaterial, this.modelDataBody))
                armorstand.setRotation(req.location.yaw, 0f)
                entityVehicleData[armorstand.uniqueId] = EntityVehicleData(
                    element.id,
                    element.layout(),
                    VehicleComponentType.MODEL
                )
                element.prototype.model!!.copy(armorstand = armorstand)
            } else {
                element.prototype.model!!.copy()
            }, 
            prototype.seats, 
            prototype.seatsRaycast, 
            prototype.transform?.copy(
                world = req.location.world,
                x = req.location.x,
                y = req.location.y,
                z = req.location.z,
                yaw = req.location.yaw.toDouble()
            ),
    )
}