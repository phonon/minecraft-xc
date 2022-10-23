/**
 * Contain main engine global state and core api.
 */

package phonon.xv

import java.nio.file.Paths
import java.nio.file.Files
import java.util.UUID
import java.util.logging.Logger
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scheduler.BukkitRunnable
import phonon.xv.system.*
import phonon.xv.core.ComponentsStorage
import phonon.xv.common.UserInput

/**
 * XV engine global state.
 * Stores all game state and provide XV engine api.
 */
public object XV {
    // spigot plugin variable
    internal var plugin: Plugin? = null
    internal var logger: Logger? = null

    // ========================================================================
    // STORAGE
    // ========================================================================
    internal var config: Config = Config()
    
    // components
    internal val storage: ComponentsStorage = ComponentsStorage()

    // user input controls when mounted on entities
    internal val userInputs: HashMap<UUID, UserInput> = HashMap()

    // ========================================================================
    // RUNNING TASKS
    // ========================================================================
    internal var engineTask: BukkitTask? = null

    /**
     * onEnable:
     * Set links to spigot plugin and logger.
     */
    internal fun onEnable(plugin: Plugin) {
        XV.plugin = plugin
        XV.logger = plugin.getLogger()
    }

    /**
     * Remove hooks to plugins and external APIs
     */
    internal fun onDisable() {
        XV.plugin = null
        XV.logger = null
    }

    /**
     * Re-initialize storages and re-load config.
     * TODO: async
     */
    internal fun reload(async: Boolean = false) {
        val timeStart = System.currentTimeMillis()
        
        // finish: print stats
        val timeEnd = System.currentTimeMillis()
        val timeLoad = timeEnd - timeStart
        XV.logger?.info("Reloaded in ${timeLoad}ms")
    }

    /**
     * Starts running engine task
     */
    internal fun start() {
        if ( XV.engineTask == null ) {
            XV.engineTask = Bukkit.getScheduler().runTaskTimer(XV.plugin!!, object: Runnable {
                override fun run() {
                    XV.update()
                }
            }, 0, 0)

            XV.logger!!.info("Starting engine")
        }
        else {
            XV.logger!!.warning("Engine already running")
        }
    }

    /**
     * Stop running engine task
     */
    internal fun stop() {
        val task = XV.engineTask
        if ( task != null ) {
            task.cancel()
            XV.engineTask = null
            XV.logger!!.info("Stopping engine")
        } else {
            XV.logger!!.warning("Engine not running")
        }
    }

    /**
     * Main engine update, runs on each tick
     */
    internal fun update() {
        // player vehicle movement controls
        systemLandMovement(storage, userInputs)

        // update vehicle models after transforms updated
        // MUST BE RUN AFTER ALL MOVEMENT CONTROLLERS
        systemUpdateModels(storage)
    }
}