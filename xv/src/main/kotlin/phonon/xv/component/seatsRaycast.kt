package phonon.xv.component

import com.google.gson.JsonObject
import java.util.logging.Logger
import org.tomlj.TomlTable
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*

/**
 * Component indicating seats should use custom raycasting system
 * for mounting. Player interaction will do a raycast to detect
 * which seat in the vehicle element should be mounted.
 * Each seat in the vehicle is given a hitbox with sizes defined
 * in this component.
 */
public data class SeatsRaycastComponent(
    val hitboxWidth: Double = 1.25,
    val hitboxHeight: Double = 1.50,
    val hitboxHeightOffset: Double = 0.25,
): VehicleComponent<SeatsRaycastComponent> {
    val hitboxHalfWidth = hitboxWidth / 2.0
    val hitboxYMin = hitboxHeightOffset
    val hitboxYMax = hitboxHeightOffset + hitboxHeight

    override val type = VehicleComponentType.SEATS_RAYCAST

    override fun self() = this

    override fun deepclone(): SeatsRaycastComponent {
        return this.copy()
    }
    
    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, _logger: Logger? = null): SeatsRaycastComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getNumberAs<Double>("hitbox_width")?.let { properties["hitboxWidth"] = it }
            toml.getNumberAs<Double>("hitbox_height")?.let { properties["hitboxHeight"] = it }
            toml.getNumberAs<Double>("hitbox_height_offset")?.let { properties["hitboxHeightOffset"] = it }

            return mapToObject(properties, SeatsRaycastComponent::class)
        }
    }
}