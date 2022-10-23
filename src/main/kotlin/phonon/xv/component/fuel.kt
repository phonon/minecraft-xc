package phonon.xv.component

import java.util.logging.Logger
import org.tomlj.TomlTable
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*

public data class FuelComponent(
    var current: Double,
    val max: Double,
): VehicleComponent {
    override val type = VehicleComponentType.FUEL

    init {
        current = current.coerceIn(0.0, max)
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, logger: Logger? = null): FuelComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getNumberAs<Double>("current")?.let { properties["current"] = it }
            toml.getNumberAs<Double>("max")?.let { properties["max"] = it }

            return mapToObject(properties, FuelComponent::class)
        }
    }
}