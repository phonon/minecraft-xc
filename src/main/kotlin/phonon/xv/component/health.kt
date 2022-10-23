package phonon.xv.component

import java.util.logging.Logger
import org.tomlj.TomlTable
import phonon.xv.core.VehicleComponent
import phonon.xv.util.mapToObject

public data class HealthComponent(
    var current: Double,
    val max: Double,
): VehicleComponent {

    init {
        current = current.coerceIn(0.0, max)
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, _logger: Logger? = null): HealthComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getDouble("current")?.let { properties["current"] = it }
            toml.getDouble("max")?.let { properties["max"] = it }

            return mapToObject(properties, HealthComponent::class)
        }
    }
}