package phonon.xv.util

import org.bukkit.ChatColor
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import phonon.xv.util.item.toNms
import phonon.xv.util.item.itemInMainHandEquivalentTo
import phonon.xv.nms.CraftPlayer

/**
 * Standard task for actions that require "progress", typical example:
 *    1. Player interaction begins process (e.g. spawn item,
 *       reload fuel/ammo, etc.)
 *    2. This progress task is created and scheduled to run on an
 *       async thread. Each tick period this checks if player
 *       is still in valid state to continue action, e.g. player
 *       still online, hasn't moved too far, still has same item,
 *       etc. Run 'onProgress' callback each tick with progress.
 *    3. If player failed tick check, cancel task and run `onCancel`
 *       callback.
 *    4. If player finishes progress timer, cancel task and run
 *       `onFinish` callback.
 */
public class TaskProgress(
    val timeTaskMillis: Long, // how long task takes in millis
    val player: Player,
    val maxMoveDistance: Double, // if < 0, ignore check
    val initialItemInHand: ItemStack? = null,
    val itemIdTag: Int? = null, // ItemStack item integer id tag for identifying same item
    val onProgress: ((progress: Double) -> Unit)? = null,
    val onCancel: ((reason: TaskProgress.CancelReason) -> Unit)? = null,
    val onFinish: (() -> Unit)? = null,
): BukkitRunnable() {
    // cache nms player reference
    private val nmsPlayer = (player as CraftPlayer).getHandle()
    
    // cache nms item reference
    private val nmsInitialItem = initialItemInHand?.toNms()

    // initial location (x0, y0, z0)
    private val initialLoc = player.location
    private val x0 = initialLoc.x
    private val y0 = initialLoc.y
    private val z0 = initialLoc.z

    // max move dist squared
    private val maxMoveDistSq = maxMoveDistance * maxMoveDistance

    // time task as double
    private val timeTaskAsDouble = timeTaskMillis.toDouble()

    // time counter
    public val tStart = System.currentTimeMillis()
    public val tFinish = tStart + timeTaskMillis

    /**
     * Reasons this action can be cancelled.
     */
    public enum class CancelReason {
        ITEM_CHANGED, // item in hand changed
        MOVED, // moved too far
        PLAYER_INVALID, // died or offline
        ;
    }

    fun cancel(reason: CancelReason) {
        super.cancel()
        onCancel?.invoke(reason)
    }

    fun finish() {
        super.cancel()
        onFinish?.invoke()
    }

    override fun run() {
        // check if player offline or dead
        val location = player.location
        if ( !player.isOnline() || player.isDead() ) {
            this.cancel(CancelReason.PLAYER_INVALID)
            return
        }

        // check if player moved too far
        if ( maxMoveDistance > 0 ) {
            val dx = location.x - x0
            val dy = location.y - y0
            val dz = location.z - z0
            val distSq = (dx * dx) + (dy * dy) + (dz * dz)
            if ( distSq > maxMoveDistSq ) {
                this.cancel(CancelReason.MOVED)
                return
            }
        }

        // check if item in hand changed
        if ( nmsInitialItem !== null ) {
            if ( !nmsPlayer.itemInMainHandEquivalentTo(nmsInitialItem, itemIdTag) ) {
                this.cancel(CancelReason.ITEM_CHANGED)
                return
            }
        }

        val t = System.currentTimeMillis()
        if ( t >= tFinish ) {
            this.finish()
        } else {
            if ( onProgress !== null ) {
                val progress = (t - tStart).toDouble() / timeTaskAsDouble
                onProgress.invoke(progress)
            }
        }
    }
}

/**
 * Create progress bar string with 10 ticks.
 * Input should be double in range [0.0, 1.0] marking progress.
 */
internal fun progressBar10(progress: Double): String {
    // available shades
    // https://en.wikipedia.org/wiki/Box-drawing_character
    // val SOLID = 2588     // full solid block
    // val SHADE0 = 2592    // medium shade
    // val SHADE1 = 2593    // dark shade

    return when ( Math.round(progress * 10.0).toInt() ) {
        0 ->  "\u2503${ChatColor.DARK_GRAY}\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592${ChatColor.WHITE}\u2503"
        1 ->  "\u2503\u2588${ChatColor.DARK_GRAY}\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592${ChatColor.WHITE}\u2503"
        2 ->  "\u2503\u2588\u2588${ChatColor.DARK_GRAY}\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592${ChatColor.WHITE}\u2503"
        3 ->  "\u2503\u2588\u2588\u2588${ChatColor.DARK_GRAY}\u2592\u2592\u2592\u2592\u2592\u2592\u2592${ChatColor.WHITE}\u2503"
        4 ->  "\u2503\u2588\u2588\u2588\u2588${ChatColor.DARK_GRAY}\u2592\u2592\u2592\u2592\u2592\u2592${ChatColor.WHITE}\u2503"
        5 ->  "\u2503\u2588\u2588\u2588\u2588\u2588${ChatColor.DARK_GRAY}\u2592\u2592\u2592\u2592\u2592${ChatColor.WHITE}\u2503"
        6 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588${ChatColor.DARK_GRAY}\u2592\u2592\u2592\u2592${ChatColor.WHITE}\u2503"
        7 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588${ChatColor.DARK_GRAY}\u2592\u2592\u2592${ChatColor.WHITE}\u2503"
        8 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588${ChatColor.DARK_GRAY}\u2592\u2592${ChatColor.WHITE}\u2503"
        9 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588${ChatColor.DARK_GRAY}\u2592${ChatColor.WHITE}\u2503"
        10 -> "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2503"
        else -> ""
    }
}