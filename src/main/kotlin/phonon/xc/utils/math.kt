/**
 * Math utility functions
 */
package phonon.xc.utils.math

import kotlin.math.sqrt

@Suppress("NOTHING_TO_INLINE")
public inline fun lengthSquared(x: Float, y: Float, z: Float): Float = (x * x) + (y * y) + (z * z)

@Suppress("NOTHING_TO_INLINE")
public inline fun length(x: Float, y: Float, z: Float): Float = sqrt((x * x) + (y * y) + (z * z))

