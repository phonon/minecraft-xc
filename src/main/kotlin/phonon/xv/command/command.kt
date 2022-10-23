
package phonon.xv.command

import org.bukkit.Bukkit
import org.bukkit.ChunkSnapshot
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import phonon.xv.XV
import phonon.xv.util.Message


private val SUBCOMMANDS = listOf(
    "help",
    "reload",
    "prototype",
)

/**
 * Main /xv command. Route to subcommands.
 */
public class Command(val plugin: JavaPlugin) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {
        
        // val player = if ( sender is Player ) sender else null
    
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
            "prototype" -> prototype(sender, args)

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
                "prototype" -> {
                    if ( args.size == 2 ) {
                        if ( sender is Player && !sender.isOp() ) { // only let ops use
                            return listOf()
                        }

                        return XV.vehiclePrototypeNames
                    }
                }
            }

            return listOf()
        }

        return SUBCOMMANDS
    }

    private fun printHelp(sender: CommandSender?) {
        Message.print(sender, "[xv] mineman vehicles!!!")
        Message.print(sender, "/xv help: help")
        Message.print(sender, "/xv reload: reload plugin config and item configs")
    }

    private fun reload(sender: CommandSender?) {
        val player = if ( sender is Player ) sender else null
        if ( player === null || player.isOp() ) {
            Message.print(sender, "[xv] Reloading...")
            XV.reload()
            Message.print(sender, "[xv] Reloaded")
        }
        else {
            Message.error(sender, "[xv] Only operators can reload")
        }
    }

    /**
     * Debug print prototypes. Usage:
     * /xv prototype: print out list of all prototype names
     * /xv prototype [name]: print out a prototype name, print all its vehicle elements
     */
    private fun prototype(sender: CommandSender?, args: Array<String>) {
        // Only let op use this command ingame
        // TODO: give permissions node to view this data
        val player = if ( sender is Player ) sender else null
        if ( player !== null && !player.isOp() ) {
            Message.error(sender, "[xv] Only operators can use this command")
            return
        }

        if ( args.size == 1 ) {
            Message.print(sender, "[xv] Prototypes:")
            for ( (name, prototype) in XV.vehiclePrototypes ) {
                Message.print(sender, "  - ${name}")
            }
        }
        else if ( args.size == 2 ) {
            val name = args[1]
            val prototype = XV.vehiclePrototypes[name]
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
}

