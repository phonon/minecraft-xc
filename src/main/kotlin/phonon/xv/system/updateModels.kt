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
                modelPos.x = transform.x
                modelPos.y = transform.y
                modelPos.z = transform.z
                modelPos.yaw = transform.yawf

                armorstand.teleport(modelPos)
            }
        }
    }

}
