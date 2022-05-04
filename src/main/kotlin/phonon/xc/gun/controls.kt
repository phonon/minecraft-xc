/**
 * Contain all player gun shooting controls systems and
 * support classes.
 */

package phonon.xc.gun

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack


/**
 * Create progress bar string. Input should be double
 * in range [0.0, 1.0] marking progress.
 */
public fun progressBarReload(progress: Double): String {
    // available shades
    // https://en.wikipedia.org/wiki/Box-drawing_character
    // val SOLID = 2588     // full solid block
    // val SHADE0 = 2592    // medium shade
    // val SHADE1 = 2593    // dark shade

    return when ( Math.round(progress * 10.0).toInt() ) {
        0 ->  "\u2503\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2503"
        1 ->  "\u2503\u2588\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2503"
        2 ->  "\u2503\u2588\u2588\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2503"
        3 ->  "\u2503\u2588\u2588\u2588\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2503"
        4 ->  "\u2503\u2588\u2588\u2588\u2588\u2592\u2592\u2592\u2592\u2592\u2592\u2503"
        5 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2592\u2592\u2592\u2592\u2592\u2503"
        6 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2592\u2592\u2592\u2592\u2503"
        7 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2592\u2592\u2592\u2503"
        8 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2592\u2592\u2503"
        9 ->  "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2592\u2503"
        10 -> "\u2503\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2503"
        else -> ""
    }
}


/**
 * Holds player automatic firing state for a gun.
 */
internal data class AutomaticFiring(
    // player firing
    val player: Player,
    // gun
    val gun: Gun,
    // total length of time player has been firing
    val totalTime: Double,
    // tick counter since last fired, used for timing firing rate in bullets/tick
    val ticksSinceFired: Int,
)

internal data class PlayerGunShootRequest(
    val player: Player,
    val gun: Gun,
    val item: ItemStack,
)

internal data class PlayerGunReloadRequest(
    val player: Player,
    val gun: Gun,
    val item: ItemStack,
)


/**
 * Player shooting system
 */
internal fun gunPlayerShootSystem(requests: ArrayList<Player>) {
    for ( player in requests ) {

    }
}


/**
 * Player reload request system
 */
internal fun gunPlayerReloadSystem(requests: ArrayList<Player>) {
    for ( player in requests ) {
        
    }
}