
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
                
            }
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

    
}

