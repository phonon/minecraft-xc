package phonon.xv.component

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.bukkit.Bukkit
import java.util.logging.Logger
import org.tomlj.TomlTable
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
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
    val world: World? = null,
    // current world position @skip runtime state
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0,
    // rotation
    var yaw: Double = 0.0,
    var pitch: Double = 0.0,
): VehicleComponent<TransformComponent> {
    override val type = VehicleComponentType.TRANSFORM

    override fun self() = this

    override fun deepclone(): TransformComponent {
        return this.copy()
    }

    // dirty flag
    var positionDirty: Boolean = false

    // yaw derived state
    var yawf: Float = yaw.toFloat()
    var yawRad: Double = Math.toRadians(yaw)
    var yawSin: Double = Math.sin(yawRad)
    var yawCos: Double = Math.cos(yawRad)
    var yawDirty: Boolean = false // dirty flag
    
    // pitch derived state
    var pitchf: Float = pitch.toFloat()
    var pitchRad: Double = Math.toRadians(pitch)
    var pitchSin: Double = Math.sin(pitchRad)
    var pitchCos: Double = Math.cos(pitchRad)
    var pitchDirty: Boolean = false // dirty flag

    // for now roll is NOT saved into json
    // most vehicles only use yaw/pitch, roll is used by some (planes)
    // for aesthetics but no real vehicle physics.
    var roll: Double = 0.0
    var rollf: Float = roll.toFloat()
    var rollRad: Double = Math.toRadians(roll)
    var rollSin: Double = Math.sin(rollRad)
    var rollCos: Double = Math.cos(rollRad)
    var rollDirty: Boolean = false // dirty flag

    // flag that vehicle in water
    var inWater: Boolean = false

    // flag that vehicle is being moving actively
    // used by some systems that need checks if vehicle is moving
    // like fuel consumption
    var isMoving: Boolean = false

    /**
     * Helper to update yaw and its derived values.
     */
    fun updateYaw(
        newYaw: Double,
    ) {
        val newYawRad = Math.toRadians(newYaw)
        val newYawSin = Math.sin(newYawRad)
        val newYawCos = Math.cos(newYawRad)
        this.yaw = newYaw
        this.yawf = newYaw.toFloat()
        this.yawRad = newYawRad
        this.yawSin = newYawSin
        this.yawCos = newYawCos
        this.yawDirty = true
    }

    /**
     * Helper to update pitch and its derived values.
     */
    fun updatePitch(
        newPitch: Double,
    ) {
        val newPitchRad = Math.toRadians(newPitch)
        val newPitchSin = Math.sin(newPitchRad)
        val newPitchCos = Math.cos(newPitchRad)
        this.pitch = newPitch
        this.pitchf = newPitch.toFloat()
        this.pitchRad = newPitchRad
        this.pitchSin = newPitchSin
        this.pitchCos = newPitchCos
        this.pitchDirty = true
    }

    /**
     * Helper to update roll and its derived values.
     */
    fun updateRoll(
        newRoll: Double,
    ) {
        val newRollRad = Math.toRadians(newRoll)
        val newRollSin = Math.sin(newRollRad)
        val newRollCos = Math.cos(newRollRad)
        this.roll = newRoll
        this.rollf = newRoll.toFloat()
        this.rollRad = newRollRad
        this.rollSin = newRollSin
        this.rollCos = newRollCos
        this.rollDirty = true
    }

    /**
     * Helper to set yaw to zero.
     */
    fun zeroYaw() {
        this.yaw = 0.0
        this.yawf = 0f
        this.yawRad = 0.0
        this.yawSin = 0.0
        this.yawCos = 1.0
        this.yawDirty = true
    }

    /**
     * Helper to set roll to zero.
     */
    fun zeroPitch() {
        this.pitch = 0.0
        this.pitchf = 0f
        this.pitchRad = 0.0
        this.pitchSin = 0.0
        this.pitchCos = 1.0
        this.pitchDirty = true
    }
    /**
     * Helper to set roll to zero.
     */
    fun zeroRoll() {
        this.roll = 0.0
        this.rollf = 0f
        this.rollRad = 0.0
        this.rollSin = 0.0
        this.rollCos = 1.0
        this.rollDirty = true
    }

    /**
     * Inject world position and rotation.
     */
    override fun injectSpawnProperties(
        location: Location?,
        player: Player?,
    ): TransformComponent {
        if ( location === null ) {
            return this.self()
        }
        return this.copy(
            world = location.world,
            x = location.x,
            y = location.y,
            z = location.z,
            yaw = location.yaw.toDouble(),
            pitch = location.pitch.toDouble(),
        )
    }

    override fun toJson(): JsonObject {
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

    override fun injectJsonProperties(json: JsonObject?): TransformComponent {
        if ( json === null ) return this.self()
        return this.copy(
            world = Bukkit.getWorld( UUID.fromString(json["world"].asString) ),
            x = json["x"].asDouble,
            y = json["y"].asDouble,
            z = json["z"].asDouble,
            yaw = json["yaw"].asDouble,
            pitch = json["pitch"].asDouble,
        )
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
    }
}