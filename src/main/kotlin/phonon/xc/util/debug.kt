/**
 * Debug structures and performance probes.
 */

package phonon.xc.util.debug

/**
 * Debug structure to hold past tick timings for different operations.
 * Internally store a ring buffer of timings for operations,
 * timing report can scan the ring buffer and report averages.
 */
internal class DebugTimings(public val size: Int) {
    // index to insert next timing item
    private var index: Int = 0

    // timings for operations in engine
    internal val timings = HashMap<String, LongArray>()
    
    // Average timings. Each index refers to last X ticks avg timing:
    // - avgTimings[0] is last tick timing
    // - avgTimings[1] is last 2 ticks timing
    // - avgTimings[4] is last 5 ticks timing
    public val avgTimings = HashMap<String, DoubleArray>()
    
    /**
     * Increments pointer to current insertion slot.
     * Run at start of each tick update.
     */
    public fun tick() {
        index += 1
        if ( index >= size ) {
            index = 0
        }
    }

    /**
     * Insert a timing value for operation.
     */
    public fun add(key: String, time: Long) {
        val timings = timings.getOrPut(key) { LongArray(size) }
        timings[index] = time
    }

    /**
     * Run through ring buffers and calculate average timings.
     * Should be run on-demand when client needs to view
     * average timings.
     */
    public fun calculateAverageTimings() {
        for ( (key, timings) in timings ) {
            val avg = avgTimings.getOrPut(key) { DoubleArray(size) }
            
            var i = index
            avg[0] = timings[i].toDouble()
            var count = 1
            
            while ( count < size ) {
                // avg[count] = ((avg[count - 1]) * (count - 1) + timings[i]) / count

                // factored out division so values don't become large and overflow
                avg[count] = (( (count.toDouble() - 1.0) / count.toDouble() ) * avg[count - 1]) + (timings[i].toDouble() / count.toDouble())

                i = if ( i > 0 ) i - 1 else size - 1
                count += 1
            }
        }
    }
}