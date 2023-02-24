
package phonon.xv.command

import java.util.EnumSet
import kotlin.math.max
import kotlin.system.measureTimeMillis
import org.bukkit.Bukkit
import org.bukkit.ChunkSnapshot
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.EntityType
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import net.kyori.adventure.text.Component
import phonon.xv.XV
import phonon.xv.component.*
import phonon.xv.core.*
import phonon.xv.system.CreateVehicleRequest
import phonon.xv.system.SpawnVehicleRequest
import phonon.xv.system.DespawnVehicleRequest
import phonon.xv.util.Message
import phonon.xv.util.entity.reassociateEntities


private val SUBCOMMANDS = listOf(
    "browse",
    "cleanupentities",
    "clear",
    "create",
    "delete",
    "deleteid",
    "deletearea",
    "despawn",
    "despawnid",
    "despawnarea",
    "help",
    "prototype",
    "reload",
    "spawn",
    "stats",
    "start",
    "stop",
)

/**
 * Main /xv command. Route to subcommands.
 */
public class Command(val xv: XV) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {
        
        if ( sender is Player && !sender.hasPermission("xv.admin") ) {
            Message.error(sender, "You do not have permission to use `/xv` command")
            return true
        }
    
        // no args, print help
        if ( args.size == 0 ) {
            this.printHelp(sender)
            return true
        }

        // parse subcommand (keep in alphabetical order)
        val arg = args[0].lowercase()
        when ( arg ) {
            "help" -> printHelp(sender)

            "browse" -> browseVehiclesGui(sender, args)
            "clear" -> clear(sender, args)
            "cleanupentities" -> cleanupentities(sender, args)
            "create" -> create(sender, args)
            "delete" -> delete(sender, args)
            "deleteid" -> deleteId(sender, args)
            "deletearea" -> deleteArea(sender, args)
            "despawn" -> despawn(sender, args)
            "despawnid" -> despawnId(sender, args)
            "despawnarea" -> despawnArea(sender, args)
            "prototype" -> prototype(sender, args)
            "reload" -> reload(sender)
            "spawn" -> spawn(sender, args)
            "stats" -> stats(sender, args)
            "start" -> start(sender)
            "stop" -> stop(sender)

            else -> {
                Message.error(sender, "Invalid /xc subcommand, use /xc help")
            }
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if ( sender is Player && !sender.hasPermission("xv.admin") ) {
            return listOf()
        }

        if ( args.size > 1 ) {
            // handle specific subcommands
            when ( args[0].lowercase() ) {
                "prototype",
                "create",
                "spawn" -> {
                    if ( args.size == 2 ) {
                        return xv.vehiclePrototypeNames
                    }
                }

                "cleanupentities" -> {
                    if ( args.size == 2 ) {
                        return listOf("delete")
                    }
                }

                "stats" -> {
                    if ( args.size == 2 ) {
                        return listOf("prototype", "element")
                    }
                    else if ( args.size == 3 ) {
                        if ( args[1].lowercase() == "prototype" ) {
                            return xv.vehiclePrototypeNames
                        }
                        else if ( args[1].lowercase() == "element" ) {
                            return xv.vehicleElementPrototypeNames
                        }
                    }
                }
            }

            return listOf()
        }

        return SUBCOMMANDS
    }

    /**
     * /xv create [prototype]
     * Immediately creates a vehicle from prototype at the player's location.
     * This does NOT do a spawn sequence, instead immediately creates
     * the vehicle.
     */
    private fun create(sender: CommandSender, args: Array<String>) {
        if ( sender !is Player ) {
            Message.error(sender, "You must be a player to run this command!")
            return
        }

        if ( args.size < 2) {
            Message.error(sender, "Invalid Syntax: /xv create [prototype]")
            return
        }

        val prototype = xv.vehiclePrototypes.get(args[1])
        if ( prototype === null ) {
            Message.error(sender, "Invalid prototype.")
            return
        }

        xv.createRequests.add(
            CreateVehicleRequest(
                prototype,
                location = sender.location,
                player = sender,
            )
        )

        Message.print(sender, "Queued vehicle ${prototype.name} create request at your location ${sender.location}")
    }

    /**
     * Prints help message listing commands.
     */
    private fun printHelp(sender: CommandSender) {
        Message.print(sender, "[xv] mineman tanks when!!!")
        Message.print(sender, "/xv browse${ChatColor.WHITE}: view gui with all vehicle spawn items")
        Message.print(sender, "/xv clear${ChatColor.WHITE}: clear all vehicles and engine state")
        Message.print(sender, "/xv cleanupentities${ChatColor.WHITE}: cleanup invalid or detached vehicle entities")
        Message.print(sender, "/xv create${ChatColor.WHITE}: create vehicle at location")
        Message.print(sender, "/xv despawn${ChatColor.WHITE}: despawn vehicle you are looking at")
        Message.print(sender, "/xv delete${ChatColor.WHITE}: delete vehicle you are looking at")
        Message.print(sender, "/xv help${ChatColor.WHITE}: this")
        Message.print(sender, "/xv prototype${ChatColor.WHITE}: view prototype info")
        Message.print(sender, "/xv reload${ChatColor.WHITE}: reload plugin config and item configs")
        Message.print(sender, "/xv spawn${ChatColor.WHITE}: spawn vehicle at location")
        Message.print(sender, "/xv stats${ChatColor.WHITE}: print engine stats")
        Message.print(sender, "/xv start${ChatColor.WHITE}: start engine")
        Message.print(sender, "/xv stop${ChatColor.WHITE}: stop engine")
    }


    /**
     * Open an inventory GUI filled with all default vehicle prototype items.
     */
    private fun browseVehiclesGui(sender: CommandSender, args: Array<String>) {
        class VehicleItemGui(
            val itemsList: List<ItemStack>,
            val page: Int = 0,
        ): InventoryHolder {
            val maxPages = 1 + (itemsList.size / 54)
            val inv: Inventory = Bukkit.createInventory(this, 54, Component.text("Vehicles (Page ${page+1}/${maxPages})"))
            val baseIndex = max(0, page * 54) // floor to zero
            
            override public fun getInventory(): Inventory {
                var n = 0 // index in storage container
                
                for ( i in baseIndex until itemsList.size ) {
                    this.inv.setItem(n, itemsList[i].clone())

                    // inventory size cutoff
                    n += 1
                    if ( n >= 54 ) {
                        break
                    }
                }

                return this.inv
            }
        }

        val player = sender as? Player
        if ( player === null ) {
            Message.error(sender, "Must be a player ingame to use /xv browse")
            return
        }

        // get page from args
        val page = if ( args.size >= 2 ) {
            try {
                args[1].toInt()
            }
            catch ( err: Exception ) {
                Message.error(sender, "Invalid page number: ${args[1]}")
                return
            }
        }
        else {
            0
        }

        val itemGui = VehicleItemGui(
            itemsList = xv.vehiclePrototypeSpawnItemList,
            page = page,
        )
        
        player.openInventory(itemGui.getInventory())
    }

    /**
     * /xv clear [confirm]
     * Clears all vehicles and engine state. Use when engine is in a 
     * broken, rekt state. Note: THIS DOES NOT REMOVE ENTITIES.
     * You will need to clear state then run `/xv cleanupentities delete`.
     */
    private fun clear(sender: CommandSender, args: Array<String>) {
        if ( args.size >= 2 ) {
            val confirmation = args[1]
            if ( confirmation.lowercase() == "confirm" ) {
                xv.clearState()
                Message.print(sender, "[xv] Cleared all vehicles and engine state.")
                return
            }
        }
        Message.error(sender, "Type `/xv clear confirm` to clear all vehicle engine state.")
    }

    /**
     * /xv cleanupentities [delete]
     * Tries to map invalid armorstands and other entities back to their
     * engine vehicles. If "delete" is specified, will force delete any invalid
     * armorstands with no vehicle mapping.
     */
    private fun cleanupentities(sender: CommandSender, args: Array<String>) {
        val forceDelete = if ( args.size >= 2 ) {
            val delete = args[1]
            if ( delete.lowercase() == "delete" ) {
                true
            } else {
                Message.error(sender, "[xv] Invalid /xv cleanupentities argument ${args[1]}, should be `delete`")
                return
            }
        } else {
            false
        }

        val numInvalid = xv.cleanupEntities(forceDelete)

        Message.print(sender, "[xv] Cleaned + remapped entities (deleted ${numInvalid} invalid engine entities)")
    }

    /**
     * /xv delete
     * Force delete vehicle you are looking at.
     */
    private fun delete(sender: CommandSender, args: Array<String>) {
        if ( sender !is Player ) {
            Message.error(sender, "Must be a player ingame to use /xv delete")
            return
        }
        
        val vehicle = xv.getVehiclePlayerIsLookingAt(sender)
        if ( vehicle === null ) {
            Message.error(sender, "No vehicle found.")
            return
        }
        
        xv.deleteVehicle(vehicle)
        Message.print(sender, "Deleted vehicle: prototype = ${vehicle.prototype.name}, id = ${vehicle.id}")
    }

    /**
     * /xv deleteid [id]
     * Force delete specific vehicle id.
     */
    private fun deleteId(sender: CommandSender, args: Array<String>) {
        if ( args.size < 2 ) {
            Message.error(sender, "Must specify vehicle id: /xv deleteid [id]")
            return
        }

        val id = try {
            args[1].toInt()
        } catch ( err: Exception ) {
            Message.error(sender, "Invalid vehicle id: ${args[1]}")
            return
        }

        val vehicle = xv.vehicleStorage.get(id)
        if ( vehicle !== null ) {
            xv.deleteVehicle(vehicle)
            Message.print(sender, "Deleted vehicle: prototype = ${vehicle.prototype.name}, id = ${vehicle.id}")
        } else {
            Message.error(sender, "No vehicle found with id ${id}")
        }
    }

    /**
     * /xv deletearea [radius]
     * Force delete vehicles in radius specified.
     */
    private fun deleteArea(sender: CommandSender, args: Array<String>) {
        if ( sender !is Player ) {
            Message.error(sender, "Must be a player ingame to use /xv deletearea [radius]")
            return
        }
        
        if ( args.size < 2 ) {
            Message.error(sender, "Must specify radius: /xv deletearea [radius]")
            return
        }

        val radius = args[1]

        val radiusDouble = try {
            radius.toDouble()
        } catch ( err: Exception ) {
            Message.error(sender, "Invalid radius: ${radius}")
            return
        }

        Message.print(sender, "Deleting vehicles in radius=${radiusDouble}")

        val vehiclesInArea = xv.getVehiclesNearLocation(sender.location, radiusDouble)
        for ( v in vehiclesInArea ) {
            xv.deleteVehicle(v)
            Message.print(sender, "Deleted vehicle: prototype = ${v.prototype.name}, id = ${v.id}")
        }
    }

    /**
     * /xv despawn [force]
     * Force despawn vehicle you are looking at. Add "force" to despawn even if
     * players are occupying vehicle.
     */
    private fun despawn(sender: CommandSender, args: Array<String>) {
        if ( sender !is Player ) {
            Message.error(sender, "Must be a player ingame to use /xv despawn")
            return
        }

        val force = if ( args.size >= 2 ) {
            val forceArg = args[1]
            if ( forceArg.lowercase() == "force" ) {
                true
            } else {
                Message.error(sender, "[xv] Invalid /xv despawn [force] argument ${args[1]}, should be `force`")
                return
            }
        } else {
            false
        }

        val vehicle = xv.getVehiclePlayerIsLookingAt(sender)
        if ( vehicle === null ) {
            Message.error(sender, "No vehicle found.")
            return
        }

        // try despawning
        Message.print(sender, "Despawning vehicle: prototype = ${vehicle.prototype.name}, id = ${vehicle.id}")
        xv.despawnRequests.add(DespawnVehicleRequest(
            vehicle = vehicle,
            player = sender,
            dropItem = true,
            force = force,
            skipTimer = false,
        ))
    }

    /**
     * /xv despawnid [id] [force]
     * Force despawn specific vehicle id. Add "force" to despawn even if
     * players are occupying vehicle.
     */
    private fun despawnId(sender: CommandSender, args: Array<String>) {
        if ( args.size < 2 ) {
            Message.error(sender, "Must specify vehicle id: /xv despawn [id] [force]")
            return
        }

        val id = try {
            args[1].toInt()
        } catch ( err: Exception ) {
            Message.error(sender, "Invalid vehicle id: ${args[1]}")
            return
        }
        
        val force = if ( args.size >= 3 ) {
            val forceArg = args[2]
            if ( forceArg.lowercase() == "force" ) {
                true
            } else {
                Message.error(sender, "[xv] Invalid /xv despawnid [id] [force] force argument ${args[2]}, should be `force`")
                return
            }
        } else {
            false
        }

        val vehicle = xv.vehicleStorage.get(id)
        if ( vehicle !== null ) {
            val player = if ( sender is Player ) {
                sender
            } else {
                null
            }

            Message.print(sender, "Despawning vehicle: prototype = ${vehicle.prototype.name}, id = ${vehicle.id}")
            xv.despawnRequests.add(DespawnVehicleRequest(
                vehicle = vehicle,
                player = player,
                dropItem = true,
                force = force,
                skipTimer = false,
            ))
        } else {
            Message.error(sender, "No vehicle found with id ${id}")
        }
    }

    /**
     * /xv despawnarea [radius] [force]
     * Force delete vehicles in radius specified.
     */
    private fun despawnArea(sender: CommandSender, args: Array<String>) {
        if ( sender !is Player ) {
            Message.error(sender, "Must be a player ingame to use /xv despawnarea [radius] [force]")
            return
        }
        
        if ( args.size < 2 ) {
            Message.error(sender, "Must specify radius: /xv despawnarea [radius] [force]")
            return
        }

        val radius = args[1]

        val radiusDouble = try {
            radius.toDouble()
        } catch ( err: Exception ) {
            Message.error(sender, "Invalid radius: ${radius}")
            return
        }

        val force = if ( args.size >= 3 ) {
            val forceArg = args[2]
            if ( forceArg.lowercase() == "force" ) {
                true
            } else {
                Message.error(sender, "[xv] Invalid /xv despawnarea [id] [force] force argument ${args[2]}, should be `force`")
                return
            }
        } else {
            false
        }

        Message.print(sender, "Despawning vehicles in radius=${radiusDouble}, force=${force}")

        val vehiclesInArea = xv.getVehiclesNearLocation(sender.location, radiusDouble)
        for ( v in vehiclesInArea ) {
            Message.print(sender, "Despawning vehicle: prototype = ${v.prototype.name}, id = ${v.id}")
            xv.despawnRequests.add(DespawnVehicleRequest(
                vehicle = v,
                player = sender,
                dropItem = true,
                force = force,
                skipTimer = false,
            ))
        }
    }

    /**
     * /xv prototype
     * Debug print out list of all prototype names.
     * 
     * /xv prototype [name]
     * Print out a prototype name info, print all its vehicle elements
     */
    private fun prototype(sender: CommandSender, args: Array<String>) {
        if ( args.size == 1 ) {
            Message.print(sender, "[xv] Prototypes:")
            for ( (name, prototype) in xv.vehiclePrototypes ) {
                Message.print(sender, "  - ${name}")
            }
        }
        else if ( args.size == 2 ) {
            val name = args[1]
            val prototype = xv.vehiclePrototypes[name]
            if ( prototype === null ) {
                Message.error(sender, "[xv] Prototype ${name} not found")
            }
            else {
                Message.print(sender, "[xv] Prototype '${name}':")
                for ( element in prototype.elements ) {
                    Message.print(sender, "  - ${element.name}")

                    if ( element.parent !== null ) {
                        Message.print(sender, "    - Parent: ${element.parent}")
                    }

                    for ( ty in element.layout ) {
                        Message.print(sender, "    - ${ty}")
                    }
                }
            }
        }
        else {
            Message.error(sender, "[xv] Invalid usage")
            Message.error(sender, "[xv] /xv prototype: print out list of all prototype names and # of elements")
            Message.error(sender, "[xv] /xv prototype [name]: print out a prototype and its vehicle elements")
        }
    }

    /**
     * /xv reload
     * This fully reloads all plugin state. Does full reload order:
     * 1. save vehicles state into json
     * 2. clear engine storage/state
     * 3. reload configs, prototypes, etc.
     * 4. load vehicles and remap entities in world to vehicles
     */
    private fun reload(sender: CommandSender) {
        Message.print(sender, "[xv] Reloading...")
        val timeReload = measureTimeMillis {
            xv.reload()
        }
        Message.print(sender, "[xv] Reloaded in ${timeReload} ms")
    }

    /**
     * /xv stats
     * Print out general engine stats for number of vehicles, archetypes, etc.
     * 
     * /xv stats prototype [prototype]
     * Print out stats for a prototype (count, etc.)
     * 
     * /xv stats element [element]
     * Print out stats for a vehicle element (count across vehicles, etc.)
     */
    private fun stats(sender: CommandSender, args: Array<String>) {
        if ( args.size == 1 ) {
            Message.print(sender, "[xv] stats:")
            Message.print(sender, "- Vehicles: ${xv.vehicleStorage.size}")
            Message.print(sender, "- Archetypes: ${xv.storage.archetypes.size}")
        }
        else if ( args.size >= 2 ) {
            val subcmd = args[1].lowercase()
            if ( subcmd == "prototype" ) {
                if ( args.size < 3 ) {
                    Message.error(sender, "[xv] /xv stats prototype [prototype]: print out stats for a prototype")
                    return
                }
                val name = args[2]
                Message.print(sender, "[xv] stats for vehicle prototype '${name}':")
                Message.print(sender, "TODO")
            }
            else if ( subcmd == "element" ) {
                if ( args.size < 3 ) {
                    Message.error(sender, "[xv] /xv stats element [prototype]: print out stats for a vehicle element prototype")
                    return
                }
                val name = args[2]
                Message.print(sender, "[xv] stats for element prototype '${name}':")
                Message.print(sender, "TODO")
            } else {
                Message.error(sender, "[xv] Invalid usage")
                Message.error(sender, "[xv] /xv stats: print out general engine stats")
                Message.error(sender, "[xv] /xv stats prototype [prototype]: print out stats for a prototype")
                Message.error(sender, "[xv] /xv stats element [element]: print out stats for a vehicle element")
            }
        }
    }

    /**
     * /xv spawn [prototype]
     * Queues spawning a vehicle of prototype at the player's location.
     * This does perform full spawn sequence.
     */
    private fun spawn(sender: CommandSender, args: Array<String>) {
        if ( sender !is Player ) {
            Message.error(sender, "You must be a player to run this command!")
            return
        }

        if ( args.size < 2) {
            Message.error(sender, "Invalid Syntax: /xv spawn [prototype]")
            return
        }

        val prototype = xv.vehiclePrototypes.get(args[1])
        if ( prototype === null ) {
            Message.error(sender, "Invalid prototype.")
            return
        }

        xv.spawnRequests.add(
            SpawnVehicleRequest(
                prototype,
                location = sender.location,
                player = sender,
            )
        )

        Message.print(sender, "Queued vehicle ${prototype.name} spawn request at your location ${sender.location}")
    }

    /**
     * /start
     * Starts the xv vehicle engine (if not running)
     */
    private fun start(sender: CommandSender) {
        Message.print(sender, "[xv] Starting engine...")
        xv.start()
    }

    /**
     * /xv stop
     * Stops the xv vehicle engine.
     */
    private fun stop(sender: CommandSender) {
        Message.print(sender, "[xv] Stopping engine...")
        xv.stop()
    }
}

