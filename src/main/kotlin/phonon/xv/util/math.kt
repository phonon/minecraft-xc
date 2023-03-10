/**
 * Math related util functions. Mainly simplified vector math constructs,
 * simple rotation matrices, vector transforms, etc.
 */

package phonon.xv.util.math

import org.bukkit.util.Vector
import org.bukkit.util.EulerAngle

// utility function to clamp v in [min, max]
private fun clamp(v: Double, min: Double, max: Double): Double {
    return Math.max(min, Math.min(max, v))
}

/**
 * Set vector to a normalized direction vector from yaw, pitch.
 * Assume y-up. Inputs should be in degrees
 */
public fun directionFromYawPitch(v: Vector, yaw: Float, pitch: Float): Vector {
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

public fun directionFromPrecomputedYawPitch(
    v: Vector,
    yawSin: Double,
    yawCos: Double,
    pitchSin: Double,
    pitchCos: Double
): Vector {
    if ( pitchSin > 0.9999 ) {
        v.x = 0.0;
        v.y = -1.0;
        v.z = 0.0;
    }
    else if ( pitchSin < -0.9999 ) {
        v.x = 0.0;
        v.y = 1.0;
        v.z = 0.0;
    }
    else {
        v.x = -pitchCos * yawSin
        v.y = -pitchSin
        v.z = pitchCos * yawCos
    }

    return v
}

/**
 * Set vector to a normalized direction vector from yaw, pitch.
 * Sets in the XZ plane. Inputs should be in degrees.
 * Axis directions are based on minecraft axes.
 */
public fun directionFromYaw(v: Vector, yaw: Float): Vector {
    val yawRad = Math.toRadians(yaw.toDouble());
    v.x = -Math.sin(yawRad)
    v.y = 0.0
    v.z = Math.cos(yawRad)
    return v
}

/**
 * Set vector to a normalized direction vector from yaw, pitch.
 * Sets in the XZ plane. Inputs should be in degrees.
 * Axis directions are based on minecraft axes.
 */
public fun directionFromPrecomputedYaw(v: Vector, yawSin: Double, yawCos: Double): Vector {
    v.x = -yawSin
    v.y = 0.0
    v.z = yawCos
    return v
}

/**
 * Normalized linear interpolation between two vectors
 */
public fun nlerp(target: Vector, from: Vector, to: Vector, t: Double): Vector {
    val t1 = 1.0 - t

    target.x = t * to.x + t1 * from.x
    target.y = t * to.y + t1 * from.y
    target.z = t * to.z + t1 * from.z

    return target.normalize()
}

/**
 * Linear interpolate angles in degrees [0, 360], handles
 * discontinuity at 0,360
 */
public fun lerp_angle(from: Double, to: Double, t: Double): Double {
    val diff = (to - from) % 360.0
    val dist = ((2.0 * diff) % 360.0) - diff
    return from + dist * t
}

public enum class EulerOrder {
    XYZ,
    YZX,
    ZXY,
    XZY,
    YXZ,
    ZYX
}

/**
 * Simple 3x3 rotation matrix
 */
public data class Mat3(
    public var m00: Double, // XX
    public var m10: Double, // YX
    public var m20: Double, // ZX

    public var m01: Double, // XY
    public var m11: Double, // YY
    public var m21: Double, // ZY

    public var m02: Double, // XZ
    public var m12: Double, // YZ
    public var m22: Double  // ZZ
) {

    public fun transformVector(v: Vector, out: Vector): Vector {
        val x = v.x
        val y = v.y
        val z = v.z

        out.x = this.m00 * x + this.m01 * y + this.m02 * z
        out.y = this.m10 * x + this.m11 * y + this.m12 * z
        out.z = this.m20 * x + this.m21 * y + this.m22 * z

        return out
    }

    /**
     * Create from XYZ minecraft euler rotation format, using
     * pre-computed yaw, pitch cos and sin
     */
    public fun makeRotationfromPrecomputedYawPitch(yawSin: Double, yawCos: Double, pitchSin: Double, pitchCos: Double): Mat3 {
        // val x = euler.x // pitch
        // val y = euler.y // yaw
        // val z = euler.z // roll

        val a: Double = pitchCos
        val b: Double = -pitchSin

        val c: Double = yawCos
        val d: Double = yawSin

        val e: Double = 1.0
        val f: Double = 0.0

        val ae = a * e
        val af = a * f
        val be = b * e
        val bf = b * f

        this.m00 = c * e       // m00
        this.m01 = -c * f      // m01
        this.m02 = d           // m02

        this.m10 = af + be * d // m10
        this.m11 = ae - bf * d // m11
        this.m12 = -b * c      // m12

        this.m20 = bf - ae * d // m20
        this.m21 = be + af * d // m21
        this.m22 = a * c       // m22

        return this
    }
    
    /**
     * Create from XYZ minecraft euler rotation format, using
     * pre-computed yaw, pitch cos and sin
     * 
     * yaw: Rotation Y = [ cos(yaw) 0 -sin(yaw)]
     *                   [   0      1      0   ]
     *                   [ sin(yaw) 0  cos(yaw)]
     * 
     * pitch Rotation X = [1        0          0  ]
     *                    [0     cos(pitch) -sin(pitch)]
     *                    [0     sin(pitch)  cos(pitch)]
     * 
     * R = Ry * Rx
     * 
     */
    public fun makeRotationfromPrecomputedYawPitch2(yawSin: Double, yawCos: Double, pitchSin: Double, pitchCos: Double): Mat3 {
        // val x = euler.x // pitch
        // val y = euler.y // yaw
        // val z = euler.z // roll

        val a: Double = pitchCos
        val b: Double = pitchSin

        val c: Double = yawCos
        val d: Double = yawSin

        this.m00 = c       // m00 * x
        this.m01 = -d * b  // m01 * y
        this.m02 = -d * a  // m02 * z

        this.m10 = 0.0 // m10
        this.m11 = a   // m11
        this.m12 = -b      // m12

        this.m20 = d // m20
        this.m21 = c * b // m21
        this.m22 = c * a   // m22

        return this
    }

    public fun makeRotationFromPrecomputed(
        pitchSin: Double,
        pitchCos: Double,
        yawSin: Double,
        yawCos: Double,
        rollSin: Double,
        rollCos: Double,
        eulerOrder: EulerOrder
    ): Mat3 {
        val a = pitchCos
        val b = pitchSin

        val c = yawCos
        val d = yawSin

        val e = rollCos
        val f = rollSin
        
        when ( eulerOrder ) {
            EulerOrder.XYZ -> {
                val ae = a * e
                val af = a * f
                val be = b * e
                val bf = b * f

                this.m00 = c * e
                this.m01 = -c * f
                this.m02 = d

                this.m10 = af + be * d
                this.m11 = ae - bf * d
                this.m12 = -b * c

                this.m20 = bf - ae * d
                this.m21 = be + af * d
                this.m22 = a * c
            }

            EulerOrder.YXZ -> {
                val ce = c * e
                val cf = c * f
                val de = d * e
                val df = d * f

                this.m00 = ce + df * b
                this.m01 = de * b - cf
                this.m02 = a * d

                this.m10 = a * f
                this.m11 = a * e
                this.m12 = -b

                this.m20 = cf * b - de
                this.m21 = df + ce * b
                this.m22 = a * c
            }

            EulerOrder.ZXY -> {
                val ce = c * e
                val cf = c * f
                val de = d * e
                val df = d * f

                this.m00 = ce - df * b
                this.m01 = -a * f
                this.m02 = de + cf * b

                this.m10 = cf + de * b
                this.m11 = a * e
                this.m12 = df - ce * b

                this.m20 = -a * d
                this.m21 = b
                this.m22 = a * c
            }

            EulerOrder.ZYX -> {
                val ae = a * e
                val af = a * f
                val be = b * e
                val bf = b * f

                this.m00 = c * e
                this.m01 = be * d - af
                this.m02 = ae * d + bf

                this.m10 = c * f
                this.m11 = bf * d + ae
                this.m12 = af * d - be

                this.m20 = -d
                this.m21 = b * c
                this.m22 = a * c
            }

            EulerOrder.YZX -> {
                val ac = a * c
                val ad = a * d
                val bc = b * c
                val bd = b * d

                this.m00 = c * e
                this.m01 = bd - ac * f
                this.m02 = bc * f + ad

                this.m10 = f
                this.m11 = a * e
                this.m12 = -b * e

                this.m20 = -d * e
                this.m21 = ad * f + bc
                this.m22 = ac - bd * f
            }

            EulerOrder.XZY -> {
                val ac = a * c
                val ad = a * d
                val bc = b * c
                val bd = b * d

                this.m00 = c * e
                this.m01 = -f
                this.m02 = d * e

                this.m10 = ac * f + bd
                this.m11 = a * e
                this.m12 = ad * f - bc

                this.m20 = bc * f - ad
                this.m21 = b * e
                this.m22 = bd * f + ac
            }
        }

        return this
    }

    public fun toEuler(eulerOrder: EulerOrder): EulerAngle {
        val e00 = this.m00
        val e01 = this.m01
        val e02 = this.m02

        val e10 = this.m10
        val e11 = this.m11
        val e12 = this.m12

        val e20 = this.m20
        val e21 = this.m21
        val e22 = this.m22

        val eulerX: Double
        val eulerY: Double
        val eulerZ: Double

        when ( eulerOrder ) {

            EulerOrder.XYZ -> {
                eulerY = Math.asin( clamp( e02, -1.0, 1.0 ) )

                if ( Math.abs( e02 ) < 0.9999999 ) {
                    eulerX = Math.atan2( -e12, e22 )
                    eulerZ = Math.atan2( -e01, e00 )
                }
                else {
                    eulerX = Math.atan2( e21, e11 )
                    eulerZ = 0.0
                }
            }

            EulerOrder.YXZ -> {
                eulerX = Math.asin( - clamp( e12, -1.0, 1.0 ) )

                if ( Math.abs( e12 ) < 0.9999999 ) {
                    eulerY = Math.atan2( e02, e22 )
                    eulerZ = Math.atan2( e10, e11 )
                }
                else {
                    eulerY = Math.atan2( -e20, e00 )
                    eulerZ = 0.0
                }
            }
            
            EulerOrder.ZXY -> {
                eulerX = Math.asin( clamp( e21, -1.0, 1.0 ) )

                if ( Math.abs( e21 ) < 0.9999999 ) {
                    eulerY = Math.atan2( -e20, e22 )
                    eulerZ = Math.atan2( -e01, e11 )
                }
                else {
                    eulerY = 0.0
                    eulerZ = Math.atan2( e10, e00 )
                }
            }

            EulerOrder.ZYX -> {
                eulerY = Math.asin( - clamp( e20, -1.0, 1.0 ) )

                if ( Math.abs( e20 ) < 0.9999999 ) {
                    eulerX = Math.atan2( e21, e22 )
                    eulerZ = Math.atan2( e10, e00 )
                }
                else {
                    eulerX = 0.0
                    eulerZ = Math.atan2( -e01, e11 )
                }
            }

            EulerOrder.YZX -> {
                eulerZ = Math.asin( clamp( e10, -1.0, 1.0 ) )

                if ( Math.abs( e10 ) < 0.9999999 ) {
                    eulerX = Math.atan2( -e12, e11 )
                    eulerY = Math.atan2( -e20, e00 )
                }
                else {
                    eulerX = 0.0
                    eulerY = Math.atan2( e02, e22 )
                }
            }

            EulerOrder.XZY -> {
                eulerZ = Math.asin( -clamp( e01, -1.0, 1.0 ) )

                if ( Math.abs( e01 ) < 0.9999999 ) {
                    eulerX = Math.atan2( e21, e11 )
                    eulerY = Math.atan2( e02, e00 )
                }
                else {
                    eulerX = Math.atan2( -e12, e22 )
                    eulerY = 0.0
                }
            }
        }

        return EulerAngle(eulerX, eulerY, eulerZ)
    }

    companion object {
        public fun zero(): Mat3 {
            return Mat3(
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0
            )
        }

        public fun fromPrecomputedYawPitch(yawSin: Double, yawCos: Double, pitchSin: Double, pitchCos: Double): Mat3 {
            return Mat3.zero().makeRotationfromPrecomputedYawPitch(yawSin, yawCos, pitchSin, pitchCos)
        }
    }
}