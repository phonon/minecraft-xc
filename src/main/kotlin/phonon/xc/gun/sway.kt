/**
 * Sway and pipelined player motion calculation system.
 * 
 * This is pretty dirty...
 */

package phonon.xc.gun

import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.entity.Item
import phonon.xc.XC

/**
 * Calculate shooting sway based on player speed/state and gun.
 */
public fun calculateSway(
    player: Player,
    gun: Gun,
    playerSpeed: Double,
): Double {
    // TODO
    return 0.0
}


// ==================================================================
// INTERNAL PLAYER MOTION CALCULATION SYSTEM
// ==================================================================

private val EMPTY_ARRAY: Array<Player> = emptyArray()

// internal motion calculation pipeline state
private var runningPipeline: Boolean = false
private var currentPlayers: Array<Player> = arrayOf()
private var currentPipelineCursor: Int = 0

/**
 * Result of player speed system calculation.
 */
internal data class PlayersMotionState(
    public val playerSpeed: HashMap<UUID, Double>,
    public val playerLocation: HashMap<UUID, Location>,
)

/**
 * System to calculate player speeds. Return maps:
 * player uuid => speed in blocks/tick
 * player uuid => location
 */
internal fun playerSpeedSystem(
    playerSpeed: HashMap<UUID, Double>,       // player uuid => previous speed
    playerLocation: HashMap<UUID, Location>, // player uuid => previous location
): PlayersMotionState {
    if ( runningPipeline ) { // 2-tick pipeline (currently non configurable)
        // finish running player motion calculation pipeline
        pipelinedPlayerSpeedSystem(
            currentPlayers,
            currentPipelineCursor,
            currentPlayers.size,
            2.0,
            playerSpeed,
            playerLocation,
        )
        runningPipeline = false
        currentPlayers = EMPTY_ARRAY
        currentPipelineCursor = 0
    }
    else {
        val players = Bukkit.getOnlinePlayers()
        val numPlayers = players.size
    
        if ( numPlayers >= XC.config.playersBeforePipelinedSway ) {
            // initiate pipelined motion calculation
            currentPlayers = players.toTypedArray()
            runningPipeline = true
            currentPipelineCursor = pipelinedPlayerSpeedSystem(
                currentPlayers,
                0,
                currentPlayers.size / 2,
                2.0,
                playerSpeed,
                playerLocation,
            )
        } else { // do in single loop
            for ( p in players ) {
                calculatePlayerSpeed(
                    p,
                    1.0,
                    playerSpeed,
                    playerLocation,
                )
            }
        }
    }

    return PlayersMotionState(playerSpeed, playerLocation)
}

/**
 * Calculate player speed from distance between locations
 * and insert into map input arg.
 */
private fun calculatePlayerSpeed(
    player: Player,
    dt: Double, // time delta in ticks
    playerSpeed: HashMap<UUID, Double>,
    playerLocation: HashMap<UUID, Location>,
) {
    val currLocation = player.getLocation()
    val dist = playerLocation[player.uniqueId]?.distance(currLocation) ?: 0.0
    val speed = dist / dt

    val playerId = player.getUniqueId()
    playerSpeed[playerId] = speed
    playerLocation[playerId] = currLocation

    // println("player = ${player.getName()}, dt = $dt, speed = $speed")
}

/**
 * Two-tick pipelined sway system.
 */
private fun pipelinedPlayerSpeedSystem(
    players: Array<Player>,
    start: Int,
    maxCalculations: Int,
    dt: Double, // time delta in ticks
    playerSpeed: HashMap<UUID, Double>,
    playerLocation: HashMap<UUID, Location>,
): Int {
    var i = 0
    var cursor = start
    while ( cursor < players.size && i < maxCalculations ) {
        val p = players[cursor]
        calculatePlayerSpeed(
            p,
            dt,
            playerSpeed,
            playerLocation,
        )
        i += 1
        cursor += 1
    }

    return cursor
}
