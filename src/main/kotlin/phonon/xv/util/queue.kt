package phonon.xv.util

import java.util.Queue

/**
 * Draining iterator on a queue. This empties and returns all elements
 * inside the queue.
 * 
 * Note this can be UNSAFE and weakly consistent for concurrent queues,
 * like a ConcurrentLinkedQueue. We are effectively doing
 *     while ( !queue.isEmpty() ) {
 *        yield queue.remove()
 *     }
 * 1. If the producer thread is faster than the consumer thread, this
 *    will never terminate. In xv systems, this should never happen
 *    as long as we only have one producer and one consumer thread.
 * 2. There can be additional elements added by the producer thread
 *    after the `.isEmpty()` check and before the `.remove()` call.
 *    We will have to ignore these elements.
 */
internal class QueueDrainIterator<T>(val queue: Queue<T>): Iterator<T> {
    override fun hasNext(): Boolean {
        return queue.isNotEmpty()
    }

    override fun next(): T {
        return queue.remove()
    }
}

/**
 * Create a draining iterator on the queue.
 */
internal fun <T> Queue<T>.drain(): Iterator<T> {
    return QueueDrainIterator(this)
}
