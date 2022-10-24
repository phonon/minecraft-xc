package phonon.xv.system

import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.max
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.PI
import org.bukkit.World
import phonon.xv.core.*
import phonon.xv.component.ModelComponent
import phonon.xv.component.TransformComponent

/**
 * System for updating ArmorStand model transforms from
 * vehicle element transforms.
 */
public fun systemUpdateModels(
    storage: ComponentsStorage,
) {
    for ( (_, transform, model) in ComponentTuple2.query<
        TransformComponent,
        ModelComponent,
    >(storage) ) {
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

}
