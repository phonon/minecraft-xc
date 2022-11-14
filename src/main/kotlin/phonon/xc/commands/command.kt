
package phonon.xc.commands

import org.bukkit.Bukkit
import org.bukkit.ChunkSnapshot
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import phonon.xc.XC
import phonon.xc.gun.crawl.CrawlStart
import phonon.xc.util.Message
import phonon.xc.util.CustomItemGui
import phonon.xc.util.createCustomItemGui


private val SUBCOMMANDS = listOf(
    "help",
    "reload",

    "ammo",
    "benchmark",
    "browse",
    "timings",
    "debugtimings",
    "gun",
    "gundebug",
    "hitbox",

    // random testing debug commands
    // "chunk",
    "crawl",
)

/**
 * Main /xc command. Route to subcommands.
 */
public class Command(
    val xc: XC,
): CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {
        
        val player = if ( sender is Player ) sender else null
    
        // no args, print help
        if ( args.size == 0 ) {
            this.printHelp(sender)
            return true
        }

        // parse subcommand
        val arg = args[0].lowercase()
        when ( arg ) {
            "help" -> printHelp(sender)
            "reload" -> reload(sender)

            "ammo"-> ammo(sender, args)
            "browse" -> browse(sender, args)
            "gun" -> gun(sender, args)
            "timings" -> timings(sender)
            "debugtimings" -> debugTimings(sender)
            "benchmark" -> benchmark(player, args)
            "gundebug" -> gundebug(sender, args)
            "hitbox" -> hitbox(sender, args)
            
            // random testing debug commands
            // "chunk" -> debugChunkSnapshotTest(sender)
            "crawl" -> crawl(sender)
            else -> {
                Message.error(sender, "Invalid /xc subcommand, use /xc help")
            }
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if ( args.size > 1 ) {
            // handle specific subcommands
            when ( args[0].lowercase() ) {
                "browse" -> return listOf("ammo", "gun", "hat", "melee", "throwable")
            }
        }

        return SUBCOMMANDS
    }

    private fun printHelp(sender: CommandSender?) {
        Message.print(sender, "[xc] for Mineman 1.16.5")
        Message.print(sender, "/xc help: help")
        Message.print(sender, "/xc reload: reload plugin config and item configs")

        Message.print(sender, "/xc benchmark: run projectile benchmark")
        Message.print(sender, "/xc browse: view gun, hat, ammo items")
        Message.print(sender, "/xc debugtimings: toggle debug timings")
        Message.print(sender, "/xc hitbox: visualize hitboxes")
        Message.print(sender, "/xc timings: print debug timings")
        return
    }

    private fun reload(sender: CommandSender?) {
        val player = if ( sender is Player ) sender else null
        if ( player === null || player.isOp() ) {
            Message.print(sender, "[xc] Reloading...")
            xc.reload(async = false)
            Message.print(sender, "[xc] Reloaded")
        }
        else {
            Message.error(sender, "[xc] Only operators can reload")
        }
    }

    private fun browse(sender: CommandSender?, args: Array<String>) {
        val player = if ( sender is Player ) sender else null
        if ( player === null ) {
            // TODO: CONSOLE FORMAT: RUN /xc list [type] INSTEAD
            Message.error(sender, "[xc] Only players ingame can use /xc browse")
            return
        }

        if ( args.size < 2 ) {
            Message.error(sender, "[xc] /xc browse [ammo|gun|hat|melee|throwable] [page]")
            return
        }

        // parse third arg as "page number", which is base index
        // in item ids to start at. for user command parsing,
        // let page start at 1 (not 0), but internally page is 0 indexed.
        // so subtract 1
        val page = if ( args.size > 2 ) {
            (args[2].toIntOrNull() ?: 1) - 1
        } else {
            0
        }

        if ( page < 0 ) {
            Message.error(sender, "Page must be >= 1.")
            return
        }

        val type = args[1].lowercase()
        when ( type ) {
            "ammo" -> player.openInventory(xc.createCustomItemGui("Ammo", xc.storage.ammoIds, xc.storage.ammo, page).getInventory())
            "gun" -> player.openInventory(xc.createCustomItemGui("Gun", xc.storage.gunIds, xc.storage.gun, page).getInventory())
            "hat" -> player.openInventory(xc.createCustomItemGui("Hat", xc.storage.hatIds, xc.storage.hat, page).getInventory())
            "melee" -> player.openInventory(xc.createCustomItemGui("Melee", xc.storage.meleeIds, xc.storage.melee, page).getInventory())
            "throwable" -> player.openInventory(xc.createCustomItemGui("Throwable", xc.storage.throwableIds, xc.storage.throwable, page).getInventory())
            else -> {
                Message.error(sender, "[xc] Invalid $type: /xc browse [ammo|gun|hat|melee|throwable] [page]")
            }
        }
    }

    /**
     * Print engine timings to sender
     */
    private fun timings(sender: CommandSender?) {
        val player = if ( sender is Player ) sender else null
        if ( player !== null && !player.isOp() ) {
            Message.error(player, "[xc] op only")
        }

        xc.debugTimings.calculateAverageTimings()

        Message.print(sender, "[xc] average timings last 1s, 5s, 10s [us]:")
        for ( (key, avgTimings) in xc.debugTimings.avgTimings ) {
            Message.print(sender, String.format("- %s: %.3f | %.3f | %.3f",
                key,
                avgTimings[19] / 1000,
                avgTimings[99] / 1000,
                avgTimings[199] / 1000
            ))
        }
    }
    
    /**
     * Toggles engine debug timings setting
     */
    private fun debugTimings(sender: CommandSender?) {
        val player = if ( sender is Player ) sender else null
        if ( player !== null && !player.isOp() ) {
            Message.error(player, "[xc] op only")
            return
        }

        xc.doDebugTimings = !xc.doDebugTimings
        Message.print(sender, "[xc] Debug timings: ${xc.doDebugTimings}")
    }
    
    /**
     * Toggles engine benchmark task
     */
    private fun benchmark(sender: CommandSender?, args: Array<String>) {
        val player = if ( sender is Player ) sender else null

        if ( player !== null ) {
            if ( !player.isOp() ) {
                Message.error(player, "[xc] op only")
                return
            }

            if ( args.size < 2 ) {
                Message.print(player, "[xc] Disabling benchmark")
                Message.print(player, "[xc] To run, use /xc benchmark [projectileCount]")
                xc.setBenchmark(false, 0)
            } else {
                val projectileCount = args[1].toInt()
                if ( projectileCount < 1 ) {
                    Message.error(player, "[xc] projectileCount must be > 0")
                } else {
                    Message.print(player, "[xc] Running benchmark with ${projectileCount} projectiles")
                    xc.setBenchmark(true, projectileCount, player)
                }
            }
        }

        Message.print(sender, "[xc] Must be run in-game by player")
    }
    
    
    /**
     * Get a ammo item from ID.
     */
    private fun ammo(sender: CommandSender?, args: Array<String>) {
        val player = if ( sender is Player ) sender else null
        if ( player === null ) {
            Message.error(sender, "[xc] Must be run in-game by player")
            return
        }
        if ( !player.isOp() ) {
            Message.error(player, "[xc] op only")
            return
        }

        if ( args.size < 2 ) {
            Message.print(sender, "/xc ammo [id]")
            return
        }

        val ammoId = args[1].toInt()
        val ammo = xc.storage.ammo[ammoId]
        if ( ammo != null ) {
            val item = ammo.toItemStack(xc)
            player.getInventory().addItem(item)
            return
        } else {
            Message.error(sender, "[xc] Invalid ammo ID")       
        }
    }
    
    /**
     * Get a gun item from ID.
     */
    private fun gun(sender: CommandSender?, args: Array<String>) {
        val player = if ( sender is Player ) sender else null
        if ( player === null ) {
            Message.error(sender, "[xc] Must be run in-game by player")
            return
        }
        if ( !player.isOp() ) {
            Message.error(player, "[xc] op only")
            return
        }

        if ( args.size < 2 ) {
            Message.print(sender, "/xc gun [id]")
            return
        }

        val gunId = args[1].toInt()
        if ( gunId >= 0 && gunId < xc.config.maxGunTypes ) {
            val gun = xc.storage.gun[gunId]
            if ( gun != null ) {
                val item = gun.toItemStack(xc)
                player.getInventory().addItem(item)
            }
            return
        }
        
        Message.error(sender, "[xc] Invalid gun ID")
    }
    
    /**
     * Set debug gun parameters
     */
    private fun gundebug(sender: CommandSender?, args: Array<String>) {
        val player = if ( sender is Player ) sender else null
        if ( player !== null && !player.isOp() ) {
            Message.error(player, "[xc] op only")
        }

        if ( args.size < 2 ) {
            Message.print(sender, "/xc gundebug [velocity] [gravity]")
        } else if ( args.size < 3 ) {
            val velocity = args[1].toFloat()
            val gravity = 0.025f
            xc.storage.gun[0] = xc.gunDebug.copy(
                projectileVelocity = velocity,
                projectileGravity = gravity,
            )
        } else {
            val velocity = args[1].toFloat()
            val gravity = args[2].toFloat()
            xc.storage.gun[0] = xc.gunDebug.copy(
                projectileVelocity = velocity,
                projectileGravity = gravity,
            )
        }
    }


    /**
     * Debug hitboxes (show particles at hitbox locations) 
     * in player range. Must be run in-game by a player.
     */
    private fun hitbox(sender: CommandSender?, args: Array<String>) {
        val player = if ( sender is Player ) sender else null
        if ( player === null ) {
            Message.error(sender, "[xc] Must be run in-game by player")
            return
        }
        if ( !player.isOp() ) {
            Message.error(player, "[xc] op only")
            return
        }

        var range = if ( args.size < 2 ) {
            1
        } else {
            args[1].toInt()
        }

        Message.print(player, "[xc] Showing hitboxes in range=${range}")
        xc.debugHitboxRequest(player, range)
    }

    // /**
    //  * Experiment to try make player crawl
    //  */
    private fun crawl(sender: CommandSender?) {
        val player = if ( sender is Player ) sender else null

        if ( player !== null ) {
            // player.setSwimming(true)
            // forceCrawl(player)
            xc.crawlStartQueue.add(CrawlStart(player))

            return
        }

        Message.print(sender, "[xc] Must be run in-game by player")
    }


    /**
     * Debug command to get entity default bounding box sizes.
     * This loads a chunk, spawns all possible entities, waits
     * for them to tick, then calculates size from BoundingBox.
     */
    // private fun getEntityHitbox() {
    //     val world = Bukkit.getWorlds()[0]
    //     val loc = Location(world, 0.0, 200.0, 0.0)
    //     world.loadChunk(0, 0)

    //     for ( e in enumValues<EntityType>() ) {
    //         try {
    //             val entity = world.spawnEntity(loc, e)
    //             Bukkit.getScheduler().runTaskLater(plugin, object: Runnable {
    //                 override fun run() {
    //                     val boundingBox = entity.getBoundingBox()
                        
    //                     val x = boundingBox.getMaxX() - boundingBox.getMinX()
    //                     val y = boundingBox.getMaxY() - boundingBox.getMinY()
    //                     val z = boundingBox.getMaxZ() - boundingBox.getMinZ()
    //                     val yOffset = entity.location.y - boundingBox.getMinY() // unneeded, minY == location.y 
                        
    //                     // without size increase
    //                     // println("map[EntityType.${e}] = HitboxSize(${x/2.0}f, ${z/2.0}f, ${y}f, ${yOffset}f)")
                        
    //                     // optionally expand bounding box
    //                     // since arrows have (0.5, 0.5, 0.5) hitbox if we just use default
    //                     // hitbox, it will feel like targets are harder to hit...
    //                     println("map[EntityType.${e}] = HitboxSize(${(x/2.0) + 0.1}f, ${(z/2.0) + 0.1}f, ${y + 0.1}f, ${yOffset - 0.1}f)")

    //                     entity.remove()
    //                 }
    //             }, 1)

    //         } catch ( err: Exception ) {
    //             println("ERROR making entity ${e}")
    //         }
    //     }
    // }

    /**
     * Toggles engine debug timings setting
     * 
     * on laptop, found it took ~20 ms / 100 chunks
     * ~200 us / chunk
     */
    // private fun debugChunkSnapshotTest(sender: CommandSender?) {
    //     val cxmin = 0
    //     val cxmax = 10
    //     val czmin = 0
    //     val czmax = 10

    //     val world = Bukkit.getWorlds()[0]

    //     // load chunks
    //     for ( cx in cxmin..cxmax ) {
    //         for ( cz in czmin..czmax ) {
    //             world.loadChunk(cx, cz)
    //         }
    //     }

    //     val t0 = System.nanoTime()
        
    //     val chunkSnapshots = ArrayList<ChunkSnapshot>()
    //     for ( cx in cxmin..cxmax ) {
    //         for ( cz in czmin..czmax ) {
    //             val chunk = world.getChunkAt(cx, cz)
    //             chunkSnapshots.add(chunk.getChunkSnapshot(false, false, false))
    //         }
    //     }
    //     val t1 = System.nanoTime()
    //     val dt = t1 - t0

    //     println("load chunks: ${dt / 1000}us")
    // }
}
