/**
 * Math utility functions
 */
package phonon.xc.util.math

import kotlin.math.sqrt
import org.bukkit.util.Vector

@Suppress("NOTHING_TO_INLINE")
public inline fun lengthSquared(x: Float, y: Float, z: Float): Float = (x * x) + (y * y) + (z * z)

@Suppress("NOTHING_TO_INLINE")
public inline fun length(x: Float, y: Float, z: Float): Float = sqrt((x * x) + (y * y) + (z * z))

/**
 * Set vector to a normalized direction vector from yaw, pitch.
 * Assume y-up. Inputs should be in degrees
 */
public fun directionFromYawPitch(yaw: Float, pitch: Float): Vector {
    val v = Vector()

    if ( pitch == 90f ) {
        v.x = 0.0;
        v.y = -1.0;
        v.z = 0.0;
    }
    else if ( pitch == -90f ) {
        v.x = 0.0;
        v.y = 1.0;
        v.z = 0.0;
    }
    else {
        val yawRad = Math.toRadians(yaw.toDouble());
        val pitchRad = Math.toRadians(pitch.toDouble());
        val cosPitch = Math.cos(pitchRad)

        v.x = -cosPitch * Math.sin(yawRad)
        v.y = -Math.sin(pitchRad)
        v.z = cosPitch * Math.cos(yawRad)
    }

    return v
}