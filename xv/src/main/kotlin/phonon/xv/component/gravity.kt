package phonon.xv.component

import com.google.gson.JsonObject
import java.util.logging.Logger
import org.tomlj.TomlTable
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*

/**
 * Component indicating vehicle transform should have gravity. This
 * calculates if block underneath current transform position is open,
 * then applies gravity and forces the vehicle to fall.
 * 
 * Used for stationary weapons like mortars and cannons which simply
 * rotate in place.
 * 
 * Do not use with other motion controllers
 * (e.g. LandMovementControlsComponent) as these internally implement
 * gravity.
 */
public data class GravityComponent(
    // roughly area of vehicle, if area > 1 will do a 5 point cross pattern
    val area: Int = 1,
    // how many ticks before running gravity
    val delay: Int = 1,
): VehicleComponent<GravityComponent> {
    override val type = VehicleComponentType.GRAVITY

    override fun self() = this

    override fun deepclone(): GravityComponent {
        return this.copy()
    }

    // counter for delay
    var delayCounter = 0

    // delay counter for gravity before entering "sleep" mode with
    // reduced rate of gravity checks
    var didGravityCounter = 0

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, _logger: Logger? = null): GravityComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getNumberAs<Int>("area")?.let { properties["area"] = it }
            toml.getNumberAs<Int>("delay")?.let { properties["delay"] = it }

            return mapToObject(properties, GravityComponent::class)
        }
    }
}