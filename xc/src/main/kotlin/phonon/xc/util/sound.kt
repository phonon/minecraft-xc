/**
 * Utils for sound effects.
 */
package phonon.xc.util.sound

import org.bukkit.World
import org.bukkit.Location
import org.bukkit.Sound


/**
 * Wrapper for playing sound effect 
 */
public data class SoundPacket(
    val sound: String,
    val world: World,
    val location: Location,
    val volume: Float,
    val pitch: Float,
)

/**
 * Runnable task to spawn explosion particles
 */
public class TaskSounds(
    val sounds: ArrayList<SoundPacket>,
): Runnable {
    override fun run() {
        for ( s in sounds ) {
            s.world.playSound(
                s.location,
                s.sound,
                s.volume,
                s.pitch,
            )
        }
    }
}