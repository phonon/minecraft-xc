package phonon.xv.component

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
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

    fun toJson(): JsonObject? {
        val json = JsonObject()
        json.add("current", JsonPrimitive(current))
        return json
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

        public fun fromJson(json: JsonObject, copy: FuelComponent) {
            copy.current = json["current"].asDouble
        }
    }
}