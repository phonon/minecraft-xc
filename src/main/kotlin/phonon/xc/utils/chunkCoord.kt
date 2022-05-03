/**
 * Contains chunk coord wrapper.
 */

package phonon.xc.utils


/**
 * 2D chunk coordinate, like in mine man.
 */
public data class ChunkCoord(
    public val x: Int,
    public val z: Int,
) {
    // bernstein djb2 hash using magic number 33:
    // hash = 33 * x + z
    override public fun hashCode(): Int {
        return ((this.x shl 5) + this.x) + z
    }

    companion object {
        /**
         * Create chunk coord from block coordinates.
         */
        public fun fromBlockCoords(x: Int, z: Int): ChunkCoord = ChunkCoord(
            x shr 4,
            z shr 4,
        )
    }
}


/**
 * 3D chunk coordinate.
 */
public data class ChunkCoord3D(
    public val x: Int,
    public val y: Int,
    public val z: Int,
) {
    override public fun hashCode(): Int {
        // 3 random big primes
        // const val p1 = 73856093
        // const val p2 = 19349663
        // const val p3 = 83492791
        // http://www.beosil.com/download/CollisionDetectionHashing_VMV03.pdf
        return (this.x * 73856093) xor (this.y * 19349663) xor (this.z * 83492791)
    }

    companion object {
        /**
         * Create chunk coord from block coordinates.
         */
        public fun fromBlockCoords(x: Int, z: Int): ChunkCoord = ChunkCoord(
            x shr 4,
            z shr 4,
        )
    }
}

