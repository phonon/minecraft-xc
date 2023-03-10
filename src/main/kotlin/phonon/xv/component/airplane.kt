package phonon.xv.component

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.util.logging.Logger
import org.tomlj.TomlTable
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*
import phonon.xv.util.math.EulerOrder
import phonon.xv.util.math.Mat3
import phonon.xv.util.math.directionFromPrecomputedYawPitch


/**
 * Plane controls component.
 */
public data class AirplaneComponent(
    // seat index that controls vehicle
    val seatController: Int = 0,

    // fast flying speed (hold W), TODO: higher fuel burn
    val speedFast: Double = 1.6,
    // steady state flying speed
    val speedSteady: Double = 1.2,
    // slow flying speed for (hold S)
    val speedSlow: Double = 0.8,
    // absolute minimum speed for flying before crashing down
    val speedFlyMin: Double = 0.7,
    // speed needed to initially liftoff
    val speedLiftoff: Double = 1.0,
    // speed acceleration
    val acceleration: Double = 0.02,
    // speed deceleration
    val deceleration: Double = 0.02,

    // yaw turning acceleration rate per tick
    val yawAcceleration: Double = 0.05,
    // yaw max turning rate per tick
    val yawSpeedMax: Double = 1.0,
    // yaw turning allowed when on ground
    val yawSpeedOnGround: Double = 1.0,
    // pitch turning acceleration rate per tick
    val pitchAcceleration: Double = 0.05,
    // pitch max turning rate per tick
    val pitchSpeedMax: Double = 1.0,
    // max/min pitch angles
    val pitchMax: Double = 50.0,
    val pitchMin: Double = -80.0,

    // model pitch rotation when on ground
    val groundPitch: Double = 20.0,
    // max pitch allowed for landing safely
    val safeLandingPitch: Double = -30.0,

    // max y level allowed (height in world)
    val yHeightMax: Double = 300.0,

    // minimum health such that plane can still be controlled
    // TODO: decouple into health component instead?
    val healthControllable: Double = 2.0,
    // how many health to deal when plane crashes into something
    val healthDamagePerCrash: Double = 4.0,

    // material for model
    // @prop material = "BONE"
    val material: Material = Material.BONE, // @skip
    // custom model data for the armorstands
    val modelId: Int = 0,
    // name of skins variant set to use instead of single model id (optional)
    var skin: String? = null,
    val skinDefaultVariant: String? = null,
    // @prop model_offset = [0.0, 1.0, 0.0]
    val modelOffsetX: Double = 0.0, // @skip
    val modelOffsetY: Double = 1.0, // @skip
    val modelOffsetZ: Double = 0.0, // @skip
    // whether to show the armor stand (for debugging)
    val armorstandVisible: Boolean = false, // @skip
    // hitbox size in blocks, at local position
    // @prop hitbox = [1.0, 1.0, 1.0]
    val hitboxX: Double = 0.0, // @skip
    val hitboxY: Double = 0.0, // @skip
    val hitboxZ: Double = 0.0, // @skip
    val hitboxYOffset: Double = 0.0,

    // explosion particle when crashing into blocks
    val particleExplosion: Particle = Particle.EXPLOSION_HUGE,
    val particleExplosionOffsetY: Double = 0.5,

    // bullet spawn base offset (will be rotated by plane rotation)
    // @prop bullet_offset = [0.0, 2.5, 1.4]
    val bulletOffsetX: Double = 0.0,
    val bulletOffsetY: Double = 2.5,
    val bulletOffsetZ: Double = 1.4,
    // fire rate in ticks
    val firerate: Int = 1,

    // @skipall
    // armor stand entity
    var armorstand: ArmorStand? = null,
    // current motion state
    var speed: Double = 0.0,
    var yawRotationSpeed: Double = 0.0,
    var pitchRotationSpeed: Double = 0.0,
): VehicleComponent<AirplaneComponent> {
    override val type = VehicleComponentType.AIRPLANE

    override fun self() = this

    // system specific state that should reset when vehicle recreated
    var noFuel = false // flag that plane's associated vehicle has no fuel
    var infoTick: Int = 0 // tick counter for info messages
    var fireDelayCounter: Int = 0 // tick counter for firing delay

    // plane rotation matrix
    var rotMatrix: Mat3 = Mat3.zero()

    // bullet offset as vector
    val bulletOffset: Vector = Vector(bulletOffsetX, bulletOffsetY, bulletOffsetZ)
    
    // forward vector (normalized)
    var direction: Vector = Vector(0.0, 0.0, 1.0)

    // position where bullets will spawn
    var bulletSpawnOffsetX: Double = 0.0
    var bulletSpawnOffsetY: Double = 0.0
    var bulletSpawnOffsetZ: Double = 0.0

    override fun deepclone(): AirplaneComponent {
        return this.copy()
    }
    
    override fun toJson(): JsonObject {
        val json = JsonObject()
        json.add("speed", JsonPrimitive(speed))
        return json
    }

    override fun injectJsonProperties(json: JsonObject?): AirplaneComponent {
        if ( json === null ) return this.copy()
        return this.copy(
            speed = json["speed"].asDouble,
        )
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, logger: Logger? = null): AirplaneComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            // seat controller
            toml.getLong("seat_controller")?.let { properties["seatController"] = it.toInt() }

            // translational motion
            toml.getNumberAs<Double>("speed_fast")?.let { properties["speedFast"] = it }
            toml.getNumberAs<Double>("speed_steady")?.let { properties["speedSteady"] = it }
            toml.getNumberAs<Double>("speed_slow")?.let { properties["speedSlow"] = it }
            toml.getNumberAs<Double>("speed_fly_min")?.let { properties["speedFlyMin"] = it }
            toml.getNumberAs<Double>("speed_liftoff")?.let { properties["speedLiftoff"] = it }
            toml.getNumberAs<Double>("acceleration")?.let { properties["acceleration"] = it }
            toml.getNumberAs<Double>("deceleration")?.let { properties["deceleration"] = it }
            
            // rotational motion
            toml.getNumberAs<Double>("yaw_acceleration")?.let { properties["yawAcceleration"] = it }
            toml.getNumberAs<Double>("yaw_speed_max")?.let { properties["yawSpeedMax"] = it }
            toml.getNumberAs<Double>("yaw_speed_on_ground")?.let { properties["yawSpeedOnGround"] = it }
            toml.getNumberAs<Double>("pitch_acceleration")?.let { properties["pitchAcceleration"] = it }
            toml.getNumberAs<Double>("pitch_speed_max")?.let { properties["pitchSpeedMax"] = it }
            toml.getNumberAs<Double>("pitch_max")?.let { properties["pitchMax"] = it }
            toml.getNumberAs<Double>("pitch_min")?.let { properties["pitchMin"] = it }

            // ground/landing settings
            toml.getNumberAs<Double>("ground_pitch")?.let { properties["groundPitch"] = it }
            toml.getNumberAs<Double>("safe_landing_pitch")?.let { properties["safeLandingPitch"] = it }
            
            // max y height
            toml.getNumberAs<Double>("y_height_max")?.let { properties["yHeightMax"] = it }
            
            // health settings
            toml.getNumberAs<Double>("health_controllable")?.let { properties["healthControllable"] = it }
            toml.getNumberAs<Double>("health_damage_per_crash")?.let { properties["healthDamagePerCrash"] = it }

            // model settings
            toml.getString("material")?.let { s ->
                Material.getMaterial(s)?.let { properties["material"] = it } ?: run {
                    logger?.warning("[AirplaneComponent] Invalid material: ${s}")
                }
            }

            toml.getString("skin")?.let { properties["skin"] = it }
            toml.getString("skin_default_variant")?.let { properties["skinDefaultVariant"] = it }
            
            toml.getLong("model_id")?.let { properties["modelId"] = it.toInt() }
            
            toml.getArray("model_offset")?.let { arr ->
                properties["modelOffsetX"] = arr.getNumberAs<Double>(0)
                properties["modelOffsetY"] = arr.getNumberAs<Double>(1)
                properties["modelOffsetZ"] = arr.getNumberAs<Double>(2)
            }

            toml.getBoolean("armorstand_visible")?.let { properties["armorstandVisible"] = it }
            
            toml.getArray("hitbox")?.let { arr ->
                properties["hitboxX"] = arr.getNumberAs<Double>(0)
                properties["hitboxY"] = arr.getNumberAs<Double>(1)
                properties["hitboxZ"] = arr.getNumberAs<Double>(2)
            }

            toml.getNumberAs<Double>("hitbox_y_offset")?.let { properties["hitboxYOffset"] = it }

            // particles
            toml.getParticle("particle_explosion")?.let { properties["particleExplosion"] = it }
            toml.getNumberAs<Double>("particle_explosion_offset_y")?.let { properties["particleExplosionOffsetY"] = it }

            // bullet settings
            toml.getArray("bullet_offset")?.let { arr ->
                properties["bulletOffsetX"] = arr.getNumberAs<Double>(0)
                properties["bulletOffsetY"] = arr.getNumberAs<Double>(1)
                properties["bulletOffsetZ"] = arr.getNumberAs<Double>(2)
            }
            toml.getNumberAs<Int>("firerate")?.let { properties["firerate"] = it }

            return mapToObject(properties, AirplaneComponent::class)
        }
    }
}