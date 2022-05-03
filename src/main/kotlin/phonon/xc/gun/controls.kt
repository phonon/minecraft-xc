package phonon.xc.gun

import org.bukkit.entity.Player

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