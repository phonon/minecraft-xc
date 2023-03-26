package phonon.xv.util

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.entity.Player

/**
 * Info message for a player with associated priority to determine whether
 * a message should override the previous one.
 */
public data class InfoMessage(
    val player: Player,
    val message: String,
    val priority: Int,
)

/**
 * This stores a dense map of player UUIDs to their info message.
 * Common problem is multiple systems need to emit info messages to players.
 * For example:
 *  - Driving a tank shows a fuel bar message
 *  - Reloading the same tank needs to show reloading bar
 * We need to be able to decide which message should override without
 * multiple sends which glitch the client info bar. This map manages
 * setting the current player info message to send with priority
 * so systems can explicitly override each other with hard-coded
 * priorities.
 * 
 * This does not support removing messages. It's expected the internals
 * are reset on each update tick.
 * 
 * This is concurrent because we need to add messages from multiple
 * threads (e.g. fuel/reloading systems that perform progress on
 * separate threads).
 */
public class ConcurrentPlayerInfoMessageMap(
    capacity: Int = 256, // max size, messages ignored if map is full
) {
    /**
     * Live storage, where messages are inserted by tasks and other threads.
     */
    private var messages = ConcurrentHashMap<UUID, InfoMessage>(capacity)

    /**
     * Stable snapshot for single-threaded iteration. There will be some
     * messages inserted while main thread is iterating which will be lost,
     * acceptable for now unless better solution figured out. 
     */
    private var snapshot = ConcurrentHashMap<UUID, InfoMessage>(capacity)
    
    /**
     * Clear internal storage.
     */
    public fun clear() {
        messages.clear()
    }

    /**
     * Tries to put an info message for a player. If there is no existing
     * message or if the priority is GREATER than the existing message,
     * then the new message is inserted.
     */
    public fun put(
        player: Player,
        priority: Int,
        message: String,
    ) {
        this.messages.compute(player.uniqueId, { _, existingMessage ->
            if ( existingMessage === null ) {
                InfoMessage(player, message, priority)
            } else if ( priority > existingMessage.priority ) {
                InfoMessage(player, message, priority)
            } else {
                existingMessage
            }
        })
    }

    /**
     * Sets the snapshot to the current live map, and swaps the live map
     * with the cleared snapshot map. Returns the snapshot map.
     */
    public fun snapshot(): ConcurrentHashMap<UUID, InfoMessage> {
        this.snapshot.clear()
        val tmp = messages
        this.messages = snapshot
        this.snapshot = tmp
        return tmp
    }

    /**
     * Returns true if the player has an info message. Can use to check if
     * message already exists before doing a long operation of constructing
     * a new message. 
     */
    public fun contains(player: Player): Boolean {
        return messages.containsKey(player.uniqueId)
    }

    /**
     * Returns the priority of the message for the player. If no message
     * exists, returns -1.
     */
    public fun priority(player: Player): Int {
        return messages[player.uniqueId]?.priority ?: -1
    }
}