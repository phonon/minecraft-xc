/**
 * Sway and pipelined player motion calculation system.
 * 
 * This is pretty dirty...
 * 
 * TODO: convert into a object, move pipeline
 * state into class state.
 */

package phonon.xc.gun

import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.entity.EntityType
import phonon.xc.XC

/**
 * Calculate shooting sway based on player speed/state and gun.
 */
public fun XC.calculateSway(
    player: Player,
    gun: Gun,
    playerSpeed: Double,
): Double {
    var sway = gun.swayBase

    // player movement:
    // - player sneak move: ~0.0647 blocks/tick
    // - player walk:       ~0.2158 blocks/tick
    // - player sprint:     ~0.2806 blocks/tick
    // here motion will be gated at 0.1 before aim down sights stops
    // so that players can still move while sneaking.
    if ( playerSpeed > 0.1 ) {
        sway *= (1.0 + (playerSpeed * gun.swaySpeedMultiplier))
    } else {
        if ( player.isSneaking() || this.isCrawling(player) ) {
            sway *= gun.swayAimDownSights
        }
    }

    // player riding a mount:
    player.getVehicle()?.let { mount ->
        if ( mount.type == EntityType.ARMOR_STAND ) {
            sway *= gun.swayRideArmorStand
        } else if ( mount.type == EntityType.BOAT ) {
            sway *= gun.swayRideBoat
        } else { // apply horse modifier (generic entity mount)
            sway *= gun.swayRideHorse
        }
    }
    
    return sway
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
internal fun XC.playerSpeedSystem(
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
    
        if ( numPlayers >= this.config.playersBeforePipelinedSway ) {
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
 * 
 * Note: This uses exponential weighted average of speed
 * to smooth out speed. When player is turning rapidly or accidently
 * hits a block, speed will drop to 0, even though player is still in
 * motion. Moving average smooths out these blips where speed = 0.0
 * temporarily.
 * 
 * Sometimes previous location does not change before this is run.
 * Player curr == prev which will cause speed to drop to 0.0.
 * No idea why mineman does this...retarded engine...
 * Must set avg smoothing to deal with these cases...
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
    val oldSpeed = playerSpeed[player.uniqueId] ?: 0.0
    
    val playerId = player.getUniqueId()
    playerSpeed[playerId] = 0.3*speed + 0.7*oldSpeed // moving avg
    playerLocation[playerId] = currLocation

    // DEBUG
    // val speedFmt = "%.2f".format(speed)
    // val avgSpeedFmt = "%.2f".format(playerSpeed[playerId] ?: 0.0)
    // println("player = ${player.getName()}, dt = $dt, speed = $speedFmt, avg_speed = $avgSpeedFmt")
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
