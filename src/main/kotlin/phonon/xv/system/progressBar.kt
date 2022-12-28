package phonon.xv.system

import org.bukkit.ChatColor
import org.bukkit.World
import org.bukkit.entity.Player

/**
 * action bars last on a player's screen
 * for ~3 seconds (according to this thread)
 * https://bukkit.org/threads/action-bars-with-no-duration.419534/
 *
 * we'll set default refresh every 2 seconds
 */
public val ACTIONBAR_REFRESH = 2 * 20

public data class ProgessBarRequest(
    val player: Player,
    val world: World,
    val x: Int, // int block positions so move check is less punishing
    val y: Int,
    val z: Int,
    val durationTicks: Int,
    val callback: () -> Unit
) {
    // how many ticks have passed since
    // progress bar starts ticking
    var passedTicks = 0

    // we use this to keep track of how many ticks since
    // we last sent an action bar
    var actionBarRefreshTicks = 0
}

fun systemProgressBar(
    requests: ArrayList<ProgessBarRequest>
): ArrayList<ProgessBarRequest> {
    // idea is every tick we tick the current arr of
    // requests and construct a new arr of requests
    val newReqs = ArrayList<ProgessBarRequest>(requests.size)
    for ( req in requests ) {
        val (
            player,
            world,
            x,
            y,
            z,
            duration,
            callback
        ) = req

        // if the player has moved, notify them
        // and then continue. we don't add this request
        // to the new request list
        val loc = player.location
        if (!(
            world == loc.world
            && x == loc.blockX
            && y == loc.blockY
            && z == loc.blockZ
        )) {
            player.sendMessage("${ChatColor.RED}You moved!")
            continue
        }

        // in some cases our default refresh rate of 2 seconds is too
        // high. (ie when duration = 2 * 20 ticks = 2 seconds) we want
        // to set out refresh rate such that we can see the actionbar
        // hit a few intermediate states before reaching 10 squares
        val refreshPeriod = ACTIONBAR_REFRESH.coerceAtMost(duration / 10)

        req.passedTicks++
        // send player the progress bar if refresh period has passed
        if ( refreshPeriod == 0 || req.actionBarRefreshTicks++ % refreshPeriod == 0) {
            val progress = req.passedTicks.toDouble() / req.durationTicks
            player.sendMessage(progressBar10(progress))
        }
        // if request duration has passed, invoke callback
        // otherwise add it to request arr for next list
        if ( req.passedTicks >= duration ) {
            callback.invoke()
        } else {
            newReqs.add(req)
        }
    }
    return newReqs
}

/**
 * Create progress bar string with 10 ticks.
 * Input should be double in range [0.0, 1.0] marking progress.
 *
 * took this from the combat plugin
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