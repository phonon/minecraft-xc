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
import org.bukkit.scheduler.BukkitRunnable
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
    // | Crouch      |  1.31 block/s |
    // | Walk        |  4.32 block/s |
    // | Sprint      |  5.61 block/s |
    // | Jump-sprint |  7.13 block/s |
    // | Max horse   | 14.57 block/s |
    // here motion will be gated at 2.50 block/tick before aim down sights stops
    // so that players can still move while sneaking.
    if ( playerSpeed > this.config.swayMovementThreshold ) {
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
 * Async task to calculate player's speed based on difference in location
 * between ticks. For each player, calculate speed as:
 *      speed = |currLocation - prevLocation| / dt
 * - `dt` is time in [s] between time this task is run
 * - `currLocation` is player current location
 * - `prevLocation` is player's previous location, stored in XC state in 
 *   read-only map.
 * Then perform exponential moving average on speed to smooth out
 * random spikes: such as teleporting, or sometimes location reads same
 * as previous location (even on non-async...wtf mineman).
 * 
 * This should be using only thread-safe checks on player state.
 * However, we have to unsafely update the XC player speed and location
 * state by overwriting these maps with newly updated maps. This is not
 * technically thread safe since this can occur while players are shooting
 * and need speed to calculate gun random sway. However, this will not
 * corrupt any systems, and gameplay impact is negligble (only effect is
 * some players will use newly updated player speed, which is moving averaged
 * so difference between ticks is relatively low/smooth), so we will do
 * this :^).
 * 
 * Roughly expected movement speeds are:
 * - walking: 4.317 block/s
 * - sprinting: 5.612 block/s
 * - jump-sprinting: 7.127 block/s ??
 * - max horse speed: 14.57 blocks/s
 * https://minecraft.fandom.com/wiki/Sprinting
 */
internal class TaskCalculatePlayerSpeed(
    val xc: XC,
): BukkitRunnable() {
    // max speed, for clamping speed to avoid large spikes
    val maxSpeed: Double = 20.0

    // exponential moving average factor, for how much to weight
    // current speed, must be in range [0, 1]. old avg speed will be
    // weighted (1 - alpha).
    // https://en.wikipedia.org/wiki/Moving_average#Exponential_moving_average
    val alpha = xc.config.swayMovementSpeedDecay
    val oneMinusAlpha = 1.0 - alpha
    
    // previous time this task was run, used to calculate delta time dt for speed
    var tPrev = System.currentTimeMillis()
    
    override fun run() {
        val tCurr = System.currentTimeMillis()
        val dt = (tCurr - tPrev).toDouble() / 1000.0

        val oldPlayerSpeeds: Map<UUID, Double> = xc.playerSpeed
        val oldPlayerLocations: Map<UUID, Location> = xc.playerPreviousLocation
        val newPlayerSpeeds = HashMap<UUID, Double>()
        val newPlayerLocations = HashMap<UUID, Location>()

        val players = Bukkit.getOnlinePlayers()
        
        for ( player in players ) {
            val currLoc = player.getLocation()
            val currSpeed = oldPlayerLocations[player.uniqueId]?.let { oldLoc ->
                // we clamp speed within [0.0, 20.0] so that if player
                // teleports large distance (e.g. warp or respawn), this won't
                // spike speed for too long. 20.0 is chosen since max horse speed
                // is ~15 blocks/s, so this is slightly faster than that.
                (currLoc.distance(oldLoc) / dt).coerceIn(0.0, maxSpeed)
            } ?: 0.0
            val oldSpeed = oldPlayerSpeeds[player.uniqueId] ?: 0.0

            // moving average of speed
            val avgSpeed = alpha*currSpeed + oneMinusAlpha*oldSpeed

            // if nan encountered convert to 0.0 (saw it happen in tests...)
            val avgSpeedCleaned = if ( avgSpeed.isNaN() ) 0.0 else avgSpeed

            newPlayerSpeeds[player.uniqueId] = avgSpeedCleaned
            newPlayerLocations[player.uniqueId] = currLoc

            // println("player ${player.name} speed: $avgSpeed")
        }
        
        // replace xc's state object player speeds/locations
        // this is not synchronized with main thread controls,
        // but main thread is read-only. this will not significantly
        // impact gameplay to replace in an unsafe way.
        xc.playerSpeed = newPlayerSpeeds
        xc.playerPreviousLocation = newPlayerLocations

        // update time for next run
        tPrev = tCurr
    }
}

/**
 * System to calculate player speeds. Return maps:
 * player uuid => speed in blocks/tick
 * player uuid => location
 */
@Deprecated(message = "Use async TaskCalculatePlayerSpeed system")
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
 * 
 * Note speed is clamped in [0, 10.0] to prevent large spikes when players
 * teleport 1000s of blocks.
 */
@Deprecated(message = "Use async TaskCalculatePlayerSpeed system")
private fun calculatePlayerSpeed(
    player: Player,
    dt: Double, // time delta in ticks
    playerSpeed: HashMap<UUID, Double>,
    playerLocation: HashMap<UUID, Location>,
) {
    val currLocation = player.getLocation()
    val dist = playerLocation[player.uniqueId]?.distance(currLocation) ?: 0.0
    val speed = (dist / dt).coerceIn(0.0, 10.0) // clamp to prevent spikes from teleporting
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
@Deprecated(message = "Use async TaskCalculatePlayerSpeed system")
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
