
package phonon.xv.component

import org.bukkit.entity.Player
import phonon.xv.core.VehicleComponent

/**
 * Land movement controls component.
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
 * Contact points are used for 5-point contact for ground detection.
 * Imagine a vehicle as a rectangle defined by contact points:
 *       
 *        p1    p2                 contacts = [
 *       _________                    origin,
 *      | x     x |                   origin + p1,
 *      |         |                   origin + p2,
 *      |    O    |  <- origin        origin + p3,
 *      |         |                   origin + p4,
 *      |_x_____x_|                ]
 *       p3     p4
 * 
 * We can then simulate gravity for vehicle to fall by checking if
 * all contact points are in air:
 *      if ( all_in_air(contacts) ) {
 *        apply_gravity()
 *      }
 * This is much more robust than just checking origin. This prevents
 * vehicles from falling into 1 block holes.
 * 
 * Note the contact points are stored as local offsets from origin.
 * The packed double array is in order [p1, p2, p3, p4].
 * Keep this convention order (front = p1, p2, rear = p3, p4).
 * We can do other fancy stuff like fitting a plane to the local y
 * values at each contact point and rotate vehicle to match terrain,
 * which needs a standard convention for point order.
 */
public data class LandMovementControlsComponent(
    // translational speed parameters (all positive)
    val acceleration: Double = 0.05,
    val decelerationMultiplier: Double = 0.8,
    val speedMaxForward: Double = 0.4,
    val speedMaxReverse: Double = 0.3,
    // yaw turning rate parameters
    val yawRotationAcceleration: Double = 0.02,
    val yawRotationDecelerationMultiplier: Double = 0.5,
    val yawRotationSpeedMax: Double = 2.0,
    // contact points for ground detection
    val contactPoints: DoubleArray = doubleArrayOf(
        1.2, 0.0, 2.0,
        -1.2, 0.0, 2.0,
        1.2, 0.0, -2.0,
        -1.2, 0.0, -2.0,
    ),
): VehicleComponent {
    // player controller for vehicle
    var player: Player? = null
    // current motion state
    var speed: Double = 0.0
    var yawRotationSpeed: Double = 0.0
}