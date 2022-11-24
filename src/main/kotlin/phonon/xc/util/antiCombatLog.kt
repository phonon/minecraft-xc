/**
 * Built in module for detecting and punishing players for combat logging.
 * This is intended to be a very minimal, lightweight implementation
 * that checks/refreshes combat start time whenever players receive damage,
 * and kills players if they log out before a timeout period.
 * 
 * This is intended to be an async task that runs in background, but needs
 * to message pass with main thread signals when players enter combat
 * and when players log out and need to be punished. 
 * 
 * Main thread signals anti combat log task when players take damage:
 * 
 *     main (producer) --> player --> task (consumer)
 *     
 *     // async task, runs on separate timer
 *     fun run() {
 *       task.addAll(playersTookDamage)
 *       for ( player in playersInCombat ) {
 *         checkCombatLog(player)
 *       }
 *     }
 *     
 *     task (producer) --> combatlog event --> main (consumer)
 * 
 * This relies on assumption this task will run faster than main thread
 * produces player damage events, so we use a non-blocking queue and
 * this task will drain it on each run. On mineman tick-based server,
 * we can assume this always holds true. 
 */

package phonon.xc.util.anticombatlog

import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlin.math.min
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import net.kyori.adventure.text.Component
import phonon.xc.XC
import phonon.xc.util.Message

/**
 * Timestamp created when a player enters combat. `timeCombatEnds` is
 * a time relative to `System.getCurrentTimeMillis()`. The player is marked as
 * being in combat until that time is reached. This must be re-created each
 * time the player takes pvp damage (or other combat indicator).
 */
internal data class PlayerCombatTimestamp(
    val player: Player,
    val timeCombatEnds: Long, // in millis
)

/**
 * Async task for detecting combat logging.
 * Contains queues for communicating with main thread.
 */
internal class TaskAntiCombatLog(
    val xc: XC,
    val timeout: Double, // in seconds
): BukkitRunnable() {
    // timeout converted to millis
    val timeoutMillis = (timeout * 1000).toLong()

    // queue for main thread to pass message that player took damage
    // main thread (producer) --> player --> this (consumer) 
    val playerTookDamage = ConcurrentLinkedQueue<Player>()

    // queue for main thread to pass message that player died
    // so we can remove them from combat log checking
    // main thread (producer) --> player --> this (consumer) 
    val playerDied = ConcurrentLinkedQueue<Player>()
    
    // queue for this thread to pass message for players who combat logged
    // this (producer) --> player --> main thread (consumer) 
    val detectedPlayerCombatLogged = ConcurrentLinkedQueue<Player>()

    // internal densemap structure for iterating players in combat
    var playersInCombat = ArrayList<PlayerCombatTimestamp>()
    val playerLookup = HashMap<UUID, Int>() // map player unique id => array index

    override fun run() {
        val timeCurr = System.currentTimeMillis()

        // drain messages for players who took damage: refresh or add to combat logging
        do {
            val player = playerTookDamage.poll()
            if ( player !== null ) {
                // if player already in list, refresh with new timestamp
                val idx = playerLookup[player.uniqueId]
                if ( idx !== null ) {
                    playersInCombat[idx] = PlayerCombatTimestamp(player, timeCurr + timeoutMillis)
                }
                else { // append to end
                    val n = playersInCombat.size
                    playersInCombat.add(PlayerCombatTimestamp(player, timeCurr + timeoutMillis))
                    playerLookup[player.uniqueId] = n
                    // message player they entered combat
                    player.sendActionBar(Component.text("${ChatColor.BOLD}Entered combat, do not log out!"))
                }
            }
        } while ( player !== null )

        // drain messages for players who died: remove from combat logging
        do {
            val player = playerDied.poll()
            if ( player !== null ) {
                // if player in combat logging, remove them
                val idx = playerLookup.remove(player.uniqueId)
                if ( idx !== null ) {
                    if ( idx < playersInCombat.size - 1) {
                        // swap remove
                        playersInCombat[idx] = playersInCombat[playersInCombat.size - 1]
                        playersInCombat.removeAt(playersInCombat.size - 1)
                        // update lookup for new player in idx
                        playerLookup[playersInCombat[idx].player.uniqueId] = idx
                    } else { // end, just remove
                        playersInCombat.removeAt(idx)
                    }
                }
            }
        } while ( player !== null )

        // temp buffer for storing players in combat for next update
        val nextPlayersInCombat = ArrayList<PlayerCombatTimestamp>(playersInCombat.size)
        
        // iterate and check if player combat logged
        for ( combat in playersInCombat ) {
            // combat ended
            if ( timeCurr > combat.timeCombatEnds ) {
                combat.player.sendActionBar(Component.text("${ChatColor.BOLD}You may now log out again"))
                playerLookup.remove(combat.player.uniqueId)
            }
            else {
                // combat log detected
                if ( !combat.player.isOnline() ) {
                    detectedPlayerCombatLogged.add(combat.player)
                    playerLookup.remove(combat.player.uniqueId)
                    Bukkit.broadcast(Component.text("${ChatColor.RED}${ChatColor.BOLD}${combat.player.name} combat logged!"))
                }
                else {
                    playerLookup[combat.player.uniqueId] = nextPlayersInCombat.size
                    nextPlayersInCombat.add(combat)
                }
            }
        }

        // swap buffers
        playersInCombat = nextPlayersInCombat
    }
}

/**
 * System to finish marking players who are detected as combat logged
 * into a set on the main thread.
 */
internal fun XC.killCombatLoggerSystem(
    detectedCombatLoggers: ConcurrentLinkedQueue<Player>,
) {
    do {
        val player = detectedCombatLoggers.poll()
        if ( player !== null ) {
            this.combatLoggers.add(player.uniqueId)
        }
    } while ( player !== null )
}