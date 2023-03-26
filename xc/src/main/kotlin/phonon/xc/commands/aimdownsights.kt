/**
 * Command to toggle aim down sights usage.
 */

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
import phonon.xc.XC
import phonon.xc.util.Message


private val AIM_DOWN_SIGHTS_SETTINGS = listOf(
    "on",
    "off",
)

/**
 * `/aimdownsights on|off` command.
 * Set player's ads mode on or off.
 */
public class AimDownSightsCommand(
    val xc: XC,
): CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {
        
        val player = if ( sender is Player ) sender else null
        
        if ( player == null ) {
            Message.error(sender, "You must be a player ingame to use this command.")
            return true
        }
        
        // no args, print help
        if ( args.size == 0 ) {
            this.printHelp(sender)
            return true
        }

        // parse subcommand
        val arg = args[0].lowercase()
        when ( arg ) {
            "on" -> xc.setAimDownSights(player, true)
            "off" -> xc.setAimDownSights(player, false)
            else -> {
                printHelp(sender)
                return true
            }
        }

        Message.print(sender, "${ChatColor.BOLD}Aim down sights turned ${arg}")

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        return AIM_DOWN_SIGHTS_SETTINGS
    }

    private fun printHelp(sender: CommandSender?) {
        Message.print(sender, "/aimdownsights on|off")
        Message.print(sender, "Toggles gun aim down sights model. Purely visual (does NOT affect accuracy).")
        return
    }
    
}
