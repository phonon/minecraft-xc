package phonon.xv.system

import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.max
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.PI
import org.bukkit.World
import phonon.xv.XV
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.iter.*
import phonon.xv.util.item.createCustomModelItem
import phonon.xv.component.ModelComponent
import phonon.xv.component.ModelGroupComponent
import phonon.xv.component.TransformComponent

/**
 * System for updating ArmorStand model transforms from
 * vehicle element transforms.
 */
public fun XV.systemUpdateModels(
    storage: ComponentsStorage,
) {
    val xv = this

    for ( (el, transform, model) in ComponentTuple2.query<
        TransformComponent,
        ModelComponent,
    >(storage) ) {
        try {
            val armorstand = model.armorstand
            if ( armorstand != null && armorstand.isValid() ) {
                val modelPos = armorstand.location
                if (
                    transform.yawDirty ||
                    transform.x != modelPos.x ||
                    transform.y != modelPos.y ||
                    transform.z != modelPos.z
                ) {
                    // world position = transformPosition + Rotation * localPosition
                    // using only yaw (in-plane) rotation, pitch not supported by
                    // default since complicates things a lot...
                    // TODO: either make separate components that support rotations: none, yaw, yawpitch, etc...
                    // or add flags into the current model component
                    modelPos.x = transform.x + transform.yawCos * model.offsetX - transform.yawSin * model.offsetZ
                    modelPos.y = transform.y + model.offsetY
                    modelPos.z = transform.z + transform.yawSin * model.offsetX + transform.yawCos * model.offsetZ
                    modelPos.yaw = transform.yawf
                    armorstand.teleport(modelPos)
                }
            }
        }
        catch ( err: Exception ) {
            if ( xv.debug ) {
                err.printStackTrace()
                xv.logger.severe("Error updating model for element ${el}: ${err.message}")
            }
        }
    }
}

/**
 * System for updating ArmorStand model group component transforms from
 * vehicle element transforms.
 */
public fun XV.systemUpdateModelGroups(
    storage: ComponentsStorage,
) {
    val xv = this

    for ( (el, transform, model) in ComponentTuple2.query<
        TransformComponent,
        ModelGroupComponent,
    >(storage) ) {
        try {
            for ( i in 0 until model.parts.size ) {
                val part = model.parts[i]
                val armorstand = model.armorstands[i]
                val currentModelId = model.currentModelIds[i]

                if ( armorstand != null && armorstand.isValid() ) {
                    val modelPos = armorstand.location
                    if (
                        transform.yawDirty ||
                        transform.x != modelPos.x ||
                        transform.y != modelPos.y ||
                        transform.z != modelPos.z
                    ) {
                        // world position = transformPosition + Rotation * localPosition
                        // using only yaw (in-plane) rotation, pitch not supported by
                        // default since complicates things a lot...
                        // TODO: either make separate components that support rotations: none, yaw, yawpitch, etc...
                        // or add flags into the current model component
                        modelPos.x = transform.x + transform.yawCos * part.offsetX - transform.yawSin * part.offsetZ
                        modelPos.y = transform.y + part.offsetY
                        modelPos.z = transform.z + transform.yawSin * part.offsetX + transform.yawCos * part.offsetZ
                        modelPos.yaw = transform.yawf

                        armorstand.teleport(modelPos)

                        // TODO: add pitch support here
                        // may need to setup separate path to do proper model transforms
                        // to properly adjust pitch
                        // armorstand.setHeadPose(EulerAngle(
                        //     transform.pitchRad,
                        //     0.0, // no yaw, handle yaw in model position itself
                        //     transform.rollRad, // roll?
                        // ))
                    }

                    // update model depending on transform state
                    val newModelId = if ( transform.isMoving ) {
                        part.modelIdMoving
                    } else {
                        part.modelId
                    }

                    if ( currentModelId != newModelId ) {
                        model.currentModelIds[i] = newModelId
                        armorstand.getEquipment().setHelmet(createCustomModelItem(model.material, newModelId))
                    }
                }
            }
        } catch ( err: Exception ) {
            if ( xv.debug ) {
                err.printStackTrace()
                xv.logger.severe("Error updating model group for element ${el}: ${err.message}")
            }
        }
    }

}
