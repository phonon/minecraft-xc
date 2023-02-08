
package phonon.xv.command

import java.util.EnumSet
import kotlin.math.max
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
import phonon.xv.system.CreateVehicleReason
import phonon.xv.util.Message
import phonon.xv.util.entity.reassociateEntities


private val SUBCOMMANDS = listOf(
    "browse",
    "clear",
    "help",
    "prototype",
    "reload",
    "spawn",
    "start",
    "stop",
    "create",
    "savereload",
)

/**
 * Main /xv command. Route to subcommands.
 */
public class Command(val xv: XV) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {
        
        // val player = if ( sender is Player ) sender else null
    
        // no args, print help
        if ( args.size == 0 ) {
            this.printHelp(sender)
            return true
        }

        // parse subcommand (keep in alphabetical order)
        val arg = args[0].lowercase()
        when ( arg ) {
            "browse" -> browseVehiclesGui(sender, args)
            "clear" -> clear(sender)
            "help" -> printHelp(sender)
            "prototype" -> prototype(sender, args)
            "reload" -> reload(sender)
            "spawn" -> spawn(sender, args)
            "start" -> start(sender)
            "stop" -> stop(sender)
            "create" -> create(sender, args)
            "savereload" -> saveReload(sender)

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
                "prototype",
                "spawn" -> {
                    if ( args.size == 2 ) {
                        if ( sender is Player && !sender.isOp() ) { // only let ops use
                            return listOf()
                        }

                        return xv.vehiclePrototypeNames
                    }
                }
                "create" -> {
                    if (args.size == 2) {
                        if ( sender is Player && !sender.isOp() ) {
                            return listOf()
                        }

                        return xv.vehiclePrototypeNames
                    }
                }
            }

            return listOf()
        }

        return SUBCOMMANDS
    }

    private fun create(sender: CommandSender, args: Array<String>) {
        if ( sender !is Player ) {
            sender.sendMessage("You must be a player to run this command!")
            return
        }

        if ( args.size < 2) {
            sender.sendMessage("Invalid Syntax: /xv create <prototype>")
            return
        }

        val prototype = xv.vehiclePrototypes.get(args[1])
        if ( prototype == null ) {
            sender.sendMessage("Invalid prototype.")
            return
        }

        xv.createRequests.add(
            CreateVehicleRequest(
                prototype,
                CreateVehicleReason.NEW,
                location = sender.location,
                player = sender,
            )
        )

        sender.sendMessage("Queued create request at your location ${sender.location}")
    }

    private fun printHelp(sender: CommandSender) {
        Message.print(sender, "[xv] mineman vehicles!!!")
        Message.print(sender, "/xv help: help")
        Message.print(sender, "/xv reload: reload plugin config and item configs")
    }

    private fun reload(sender: CommandSender) {
        val player = if ( sender is Player ) sender else null
        if ( player === null || player.isOp() ) {
            Message.print(sender, "[xv] Reloading...")
            xv.reload()
            Message.print(sender, "[xv] Reloaded")
        }
        else {
            Message.error(sender, "[xv] Only operators can reload")
        }
    }

    private fun saveReload(sender: CommandSender) {
        val player = if ( sender is Player ) sender else null
        if ( player === null || player.isOp() ) {
            Message.print(sender, "[xv] Saving...")
            xv.saveVehicles(xv.config.pathSave)
            Message.print(sender, "[xv] Data saved to ${xv.config.pathSave}")
            Message.print(sender, "[xv] Reload...")
            xv.reload()
            Message.print(sender, "[xv] Reloaded")
            Message.print(sender, "[xv] Loading data from ${xv.config.pathSave}...")
            xv.loadVehicles(xv.config.pathSave)
            Message.print(sender, "[xv] Loaded")
            // run create on requests
            xv.flushCreateQueue()
            // reassociate armorstands w/ newly loaded
            // components
            Bukkit.getWorlds().forEach { w ->
                reassociateEntities(xv, w.entities)
            }
        }
        else {
            Message.error(sender, "[xv] Only operators can reload")
        }
    }

    private fun start(sender: CommandSender) {
        val player = if ( sender is Player ) sender else null
        if ( player === null || player.isOp() ) {
            Message.print(sender, "[xv] Starting engine...")
            xv.start()
        }
        else {
            Message.error(sender, "[xv] Only operators can /xv start")
        }
    }

    private fun stop(sender: CommandSender) {
        val player = if ( sender is Player ) sender else null
        if ( player === null || player.isOp() ) {
            Message.print(sender, "[xv] Stopping engine...")
            xv.stop()
        }
        else {
            Message.error(sender, "[xv] Only operators can /xv stop")
        }
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

        if ( !player.hasPermission("xv.admin") ) {
            Message.error(sender, "You do not have permission 'xv.admin' to use /xv browse")
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

    private fun clear(sender: CommandSender) {
        val player = if ( sender is Player ) sender else null
        if ( player === null || player.isOp() ) {
            Message.print(sender, "[xv] Clearing archetypes...")
            for ( archetype in xv.storage.archetypes ) {
                archetype.clear()
            }
        }
        else {
            Message.error(sender, "[xv] Only operators can /xv clear")
        }
    }

    /**
     * Debug print prototypes. Usage:
     * /xv prototype: print out list of all prototype names
     * /xv prototype [name]: print out a prototype name, print all its vehicle elements
     */
    private fun prototype(sender: CommandSender, args: Array<String>) {
        // Only let op use this command ingame
        // TODO: give permissions node to view this data
        val player = if ( sender is Player ) sender else null
        if ( player !== null && !player.isOp() ) {
            Message.error(sender, "[xv] Only operators can use this command")
            return
        }

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
     * Spawn vehicle for testing
     */
    private fun spawn(sender: CommandSender, args: Array<String>) {
        // TODO
    }
}

