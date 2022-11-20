/**
 * Packet handler for block cracking.
 */

package phonon.xc.util.blockCrackAnimation


import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import phonon.xc.nms.CraftPlayer
import phonon.xc.nms.blockcrack.broadcastBlockCrackAnimation

/**
 * Block location to create block cracking animation,
 * for where bullets hit.
 */
public data class BlockCrackAnimation(val world: World, val x: Int, val y: Int, val z: Int)

/**
 * Send packet for block breaking at location
 */
public class TaskBroadcastBlockCrackAnimations(
    val animations: ArrayList<BlockCrackAnimation>,
): Runnable {
    // cache of players in each world
    // map world uuid => List<Player>
    internal val worldPlayers: HashMap<UUID, List<CraftPlayer>> = HashMap()

    override fun run() {
        val random = ThreadLocalRandom.current()

        // cache players into CraftPlayer in each world
        if ( animations.size > 0 ) {
            Bukkit.getWorlds().forEach { world -> 
                worldPlayers.put(world.getUID(), world.getPlayers().map({ p -> p as CraftPlayer }))
            }
        }

        for ( block in animations ) {
            worldPlayers[block.world.getUID()]?.let { players ->
                // generate random entity id for entity breaking block
                val entityId = random.nextInt(Integer.MAX_VALUE)
                val breakStage = random.nextInt(4) + 1
                block.world.broadcastBlockCrackAnimation(players, entityId, block.x, block.y, block.z, breakStage)
            }
        }
    }
}
