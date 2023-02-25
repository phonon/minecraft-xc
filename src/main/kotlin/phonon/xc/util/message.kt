/**
 * Ingame player message printing manager
 * 
 */

package phonon.xc.util

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.NamedTextColor


public object Message {

    public val PREFIX = "[xc]"
    public val STYLE_MSG = Style.style(NamedTextColor.AQUA)
    public val STYLE_ERROR = Style.style(NamedTextColor.RED)

    /**
     * Print generic plugin message to command sender's chat (either console
     * or player).
     */
    public fun print(sender: CommandSender?, s: String) {
		if ( sender === null ) {
            System.out.println("${PREFIX} Message called with null sender: ${s}")
            return
		}

        val msg = Component.text(s, STYLE_MSG)
        sender.sendMessage(msg)
    }

    /**
     * Print error message to a command sender's chat (either console or player).
     */
    public fun error(sender: CommandSender?, s: String) {
		if ( sender === null ) {
            System.out.println("${PREFIX} Message called with null sender: ${s}")
            return
		}

        val msg = Component.text(s, STYLE_ERROR)
        sender.sendMessage(msg)
    }

    /**
     * Wrapper around Bukkit.broadcast to send plugin formatted messages
     * to all players.
     */
    public fun broadcast(s: String) {
        val msg = Component.text(s, STYLE_MSG)
        Bukkit.broadcast(msg)
    }

    /**
     * Wrapper around paper sendActionBar to send plugin formatted messages.
     */
    public fun announcement(player: Player, s: String) {
        player.sendActionBar(Component.text(s))
    }
}