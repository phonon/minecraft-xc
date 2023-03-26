/**
 * Progress bar strings.
 */
package phonon.xc.util

import org.bukkit.ChatColor

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
