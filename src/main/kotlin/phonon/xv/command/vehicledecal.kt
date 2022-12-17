package phonon.xv.command

import java.util.EnumSet
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.EntityType
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import phonon.xv.XV
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.EntityVehicleData
import phonon.xv.util.Message


/**
 * /vehicledecal [name]
 * Different usage for player ingame and console:
 * - console usage: prints different skin decal variants available for name.
 * - ingame usage: when mounted on vehicle, trys to set the decal variant
 *   of the player's current vehicle.
 */
public class VehicleDecalCommand(val xv: XV) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {
        // do separate actions for ingame (set skin) or console (print skins)
        if ( sender is Player ) {
            this.onCommandPlayerIngame(sender, args)
        } else if ( sender is ConsoleCommandSender ) {
            this.onCommandConsole(sender, args)
        } else {
            this.printHelp(sender)
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, cmd: Command, alias: String, args: Array<String>): List<String> {
        // do separate actions for ingame (set skin) or console (print skins)
        if ( sender is Player ) {
            return this.onTabCompletePlayerIngame(sender, args)
        } else if ( sender is ConsoleCommandSender ) {
            return this.onTabCompleteConsole(args)
        } else {
            return listOf()
        }
    }

    private fun printHelp(sender: CommandSender?) {
        Message.print(sender, "/vehicledecal [name]")
        Message.print(sender, "Use this command to set the custom decal on the vehicle you are mounting.")
    }

    /**
     * Run from console: just print all skins available.
     */
    fun onCommandConsole(console: ConsoleCommandSender, args: Array<String>): Boolean {
        
        // no args, print help
        if ( args.size == 0 ) {
            this.printHelp(console)
            return true
        }

        // parse skin name, try to print variants
        val arg = args[0].lowercase()
        val decals = xv.skins[arg]?.decalNames
        if ( decals !== null ) {
            Message.print(console, "Skin '${arg}':")
            for ( d in decals ) {
                Message.print(console, "- ${d}")
            }
        } else {
            Message.print(console, "No skin set found with name '${arg}'")
        }

        return true
    }

    /**
     * Tab complete for console: print list of all available skin sets, 
     * or print available skins for a given set.
     */
    fun onTabCompleteConsole(args: Array<String>): List<String> {        
        // print available skins
        if ( args.size < 2 ) {
            return xv.skins.skinNames
        }

        return listOf()
    }

    /**
     * Run from ingame: if player inside a vehicle, send a request to change
     * decal variant on the vehicle.
     */
    fun onCommandPlayerIngame(player: Player, args: Array<String>): Boolean {

        // no args, print help
        if ( args.size == 0 ) {
            this.printHelp(player)
            return true
        }

        // parse subcommand (keep in alphabetical order)
        val arg = args[0].lowercase()
        
        // TODO: check if player is inside vehicle, then send
        // request to change skin

        return true
    }

    /**
     * Tab complete ingame: if player inside a vehicle, get available decal
     * variants for the vehicle.
     */
    fun onTabCompletePlayerIngame(player: Player, args: Array<String>): List<String> {
        
        // get vehicle + components, check for valid components with skins
        // then get first component with skin, and use it to get available skins

        return listOf()
    }
}