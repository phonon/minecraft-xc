package phonon.xv.component

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.bukkit.Bukkit
import java.util.logging.Logger
import org.tomlj.TomlTable
import org.bukkit.World
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*
import java.util.*
import kotlin.collections.HashMap

/**
 * Contains a vehicle elements world position and rotation.
 * 
 * Vehicles are rigid bodies, so no scale.
 */
public data class TransformComponent(
    // offset from parent element
    // @prop offset = [0.0, 0.0, 0.0]
    val offsetX: Double = 0.0, // @skip
    val offsetY: Double = 0.0, // @skip
    val offsetZ: Double = 0.0, // @skip

    // @skipall
    // RUNTIME MOTION STATE BELOW
    // minecraft world, immutable, don't allow moving between worlds :^(
    var world: World? = null, // need to change 2 var for it to work w/ serde system
    // current world position @skip runtime state
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0,
    // rotation
    var yaw: Double = 0.0,
    var pitch: Double = 0.0,
): VehicleComponent {
    override val type = VehicleComponentType.TRANSFORM
    // dirty flag
    var positionDirty: Boolean = false

    var yawf: Float = yaw.toFloat()
    var yawRad: Double = Math.toRadians(yaw)
    var yawSin: Double = Math.sin(yawRad)
    var yawCos: Double = Math.cos(yawRad)
    // dirty flag
    var yawDirty: Boolean = false

    // flag that vehicle in water
    var inWater: Boolean = false

    public fun toJson(): JsonObject {
        val json = JsonObject()
        // properties
        json.add("world", JsonPrimitive(world!!.uid.toString())) // this shouldn't be an issue
        json.add("x", JsonPrimitive(x))
        json.add("y", JsonPrimitive(y))
        json.add("z", JsonPrimitive(z))
        json.add("yaw", JsonPrimitive(yaw))
        json.add("pitch", JsonPrimitive(pitch))
        return json
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, _logger: Logger? = null): TransformComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getArray("offset")?.let { arr ->
                properties["offsetX"] = arr.getNumberAs<Double>(0)
                properties["offsetY"] = arr.getNumberAs<Double>(1)
                properties["offsetZ"] = arr.getNumberAs<Double>(2)
            }

            return mapToObject(properties, TransformComponent::class)
        }

        public fun fromJson(json: JsonObject, copy: TransformComponent) {
            copy.world = Bukkit.getWorld( UUID.fromString(json["world"].asString) )
            copy.x = json["x"].asDouble
            copy.y = json["y"].asDouble
            copy.z = json["z"].asDouble
            copy.yaw = json["yaw"].asDouble
            copy.pitch = json["pitch"].asDouble
        }
    }
}