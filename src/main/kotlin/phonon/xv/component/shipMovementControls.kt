package phonon.xv.component

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.tomlj.TomlTable
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.getNumberAs
import java.util.logging.Logger

/**
 * Ship movement controls component.
 *
 * Immutable properties are the settings for how motion
 * updates in land motion system (acceleration, turning, etc.).
 *
 * Mutable properties are the current motion state,
 * speed, yaw turning speed, etc.
 *
 * To update immutable settings dynamically, create copy of
 * component and copy over the runtime motion state.
 *
 * For simulating gravity, this component uses the same 5
 * contact point implementation as land movement controls.
 * For ship movement in particular, the ground contact points
 * will also be used to determine whether a ship is
 * "grounded," and thus if it can move/rotate. A ship is
 * considered grounded if any of its ground contact points is
 * in a non ship-traversable and non ship-passable block.
 *
 * We say a block is ship-traversable if a ship is able to travel
 * through, but not fall through it. An example of a ship-traversable
 * block is water. We say a block is ship-passable if a ship is able
 * to fall through. An example would be air. We say a block is grounded
 * if it is neither ship-traversable nor ship-passable. Note each of these
 * labels is mutually exclusive.
 *
 * A ship can only move when the following conditions are met:
 * 1. NONE of the ship's ground contact points are touching a grounded block
 * 2. AT LEAST ONE of the ship's ground contact points is touching a
 *    ship-traversable block
 *
 * 5 contact points are also used to simulate front and
 * backward collisions.
 *
 * The format for frontal and backward contact points
 * is the same as that of the ground contacts in the order
 * [p1, p2, p3, p4, p5]
 *
 *       TOP OF ELEMENT
 *        p1    p2                 contacts = [
 *       _________                    origin + p5,
 *      | x     x |                   origin + p1,
 *      |         |                   origin + p2,
 *      |    x <----p5                origin + p3,
 *      |         |                   origin + p4,
 *      |_x_____x_|                ]
 *       p3     p4
 *      BOTTOM OF ELEMENT
 *
 * This component makes the (practical) assumption that front and back
 * collision contact points will always be the same size. (imagine
 * a boat that can't move forward into a 3 block high roof,
 * but can back out of one) Thus, this component accepts the
 * relative coordinates of the front contact points, and an
 * [x, y, z] offset array to offset each of the forward
 * contact points into backward contacts.
 */
public data class ShipMovementControlsComponent(
    // seat index that controls vehicle
    val seatController: Int = 0,
    // translational speed parameters (all positive)
    val acceleration: Double = 0.02,
    val decelerationMultiplier: Double = 0.8,
    val speedMaxForward: Double = 0.4,
    val speedMaxReverse: Double = 0.3,
    // speed allowed when grounded
    val speedGrounded: Double = 0.05,
    // yaw turning rate parameters
    val yawRotationAcceleration: Double = 0.1,
    val yawRotationDecelerationMultiplier: Double = 0.5,
    val yawRotationSpeedMax: Double = 2.0,
    // the speed at which boats can
    // turn at the full turn accel
    val yawRotationEffectiveSpeed: Double = 0.1,
    // collision settings
    // i-frame after collision, -1 to disable
    val collisionCooldownTicks: Int = 60,
    // min speed needed to register a collision, -1 to disable
    val minCollisionSpeed: Double = 0.2,
    // contact points for gravity
    val groundContactPoints: DoubleArray = doubleArrayOf(
        1.2, 0.0, 2.0,
        -1.2, 0.0, 2.0,
        1.2, 0.0, -2.0,
        -1.2, 0.0, -2.0,
    ),
    val frontContactPoints: DoubleArray = doubleArrayOf(
        1.2, 0.0, 2.0,
        -1.2, 0.0, 2.0,
        1.2, 3.0, 2.0,
        -1.2, 3.0, 2.0,
        0.0, 1.5, 2.0,
    ),
    val backwardContactsDisplace: DoubleArray = doubleArrayOf(
        0.0,
        0.0,
        -4.0
    ),
    // @skipall
    // current motion state
    var speed: Double = 0.0,
    var yawRotationSpeed: Double = 0.0,
): VehicleComponent<ShipMovementControlsComponent> {
    val backwardContactPoints: DoubleArray
    init {
        // construct backward contact points from front
        // plus displace
        backwardContactPoints = DoubleArray(5 * 3)
        for ( i in 0..4 ) {
            for ( j in 0..2 ) {
                backwardContactPoints[i*3+j] = backwardContactsDisplace[j] + frontContactPoints[i*3+j]
            }
        }
    }

    override val type = VehicleComponentType.SHIP_MOVEMENT_CONTROLS

    override fun self() = this

    override fun deepclone(): ShipMovementControlsComponent {
        return this.copy()
    }

    override fun toJson(): JsonObject {
        val json = JsonObject()
        json.add("speed", JsonPrimitive(speed))
        json.add("yawRotationSpeed", JsonPrimitive(yawRotationAcceleration))
        return json
    }

    override fun injectJsonProperties(json: JsonObject?): ShipMovementControlsComponent {
        if ( json === null ) return this.self()
        return this.copy(
            speed = json["speed"].asDouble,
            yawRotationSpeed = json["yawRotationSpeed"].asDouble
        )
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, _logger: Logger? = null): ShipMovementControlsComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            // seat controller
            toml.getLong("seat_controller")?.let { properties["seatController"] = it.toInt() }

            // translational motion
            toml.getNumberAs<Double>("acceleration")?.let { properties["acceleration"] = it }
            toml.getNumberAs<Double>("deceleration_multiplier")?.let { properties["decelerationMultiplier"] = it }
            toml.getNumberAs<Double>("speed_max_forward")?.let { properties["speedMaxForward"] = it }
            toml.getNumberAs<Double>("speed_max_reverse")?.let { properties["speedMaxReverse"] = it }
            toml.getNumberAs<Double>("speed_grounded")?.let { properties["speedGrounded"] = it }

            // rotational motion
            toml.getNumberAs<Double>("yaw_rotation_acceleration")?.let { properties["yawRotationAcceleration"] = it }
            toml.getNumberAs<Double>("yaw_rotation_deceleration_multiplier")?.let { properties["yawRotationDecelerationMultiplier"] = it }
            toml.getNumberAs<Double>("yaw_rotation_speed_max")?.let { properties["yawRotationSpeedMax"] = it }
            toml.getNumberAs<Double>("yaw_rotation_effective_speed")?.let { properties["yawRotationEffectiveSpeed"] = it }

            // collision settings
            toml.getNumberAs<Int>("collision_cooldown_ticks")?.let { properties["collisionCooldownTicks"] = it }
            toml.getNumberAs<Double>("min_collision_speed")?.let { properties["minCollisionSpeed"] = it }

            // ground contact points
            toml.getArray("ground_contact_points")?.let { arr ->
                // need to manually parse array of 12 double points
                val points = DoubleArray(12)
                for ( i in 0 until 12 ) {
                    points[i] = arr.getNumberAs<Double>(i)
                }
                properties["groundContactPoints"] = points
            }
            // front contacts
            toml.getArray("front_contact_points")?.let { arr ->
                // need to manually parse array of 12 double points
                val points = DoubleArray(15)
                for ( i in 0 until 15 ) {
                    points[i] = arr.getNumberAs<Double>(i)
                }
                properties["frontContactPoints"] = points
            }
            // back contact displace
            toml.getArray("backward_contacts_displace")?.let { arr ->
                // need to manually parse array of 12 double points
                val points = DoubleArray(3)
                for ( i in 0 until 3 ) {
                    points[i] = arr.getNumberAs<Double>(i)
                }
                properties["backwardContactsDisplace"] = points
            }

            return mapToObject(properties, ShipMovementControlsComponent::class)
        }
    }
}