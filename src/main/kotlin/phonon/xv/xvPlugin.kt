/*
 * Implement bukkit plugin interface
 */

package phonon.xv

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.command.TabCompleter
import org.bukkit.event.HandlerList
import kotlin.system.measureTimeMillis
import com.comphenix.protocol.ProtocolLibrary
import phonon.xv.command.*
import phonon.xv.listener.*

public class XVPlugin : JavaPlugin() {
    
    override fun onEnable() {
        
        // measure load time
        val timeStart = System.currentTimeMillis()

        val logger = this.getLogger()
        val pluginManager = this.getServer().getPluginManager()

        // main plugin initialization phase
        XV.onEnable(this)

        // register listeners
        pluginManager.registerEvents(EventListener(this), this)
        pluginManager.registerEvents(ArmorstandListener(this), this)
        ProtocolLibrary.getProtocolManager().addPacketListener(ControlsListener(this))

        // register commands
        this.getCommand("xv")?.setExecutor(Command(this))
        
        // override command aliases tab complete if they exist
        this.getCommand("xv")?.setTabCompleter(this.getCommand("xv")?.getExecutor() as TabCompleter)
        
        // load configs, save data, etc. and start engine
        XV.reload()
        XV.start()

        // print load time
        val timeEnd = System.currentTimeMillis()
        val timeLoad = timeEnd - timeStart
        logger.info("Enabled in ${timeLoad}ms")

        // print success message
        logger.info("now this is epic")
    }

    override fun onDisable() {
        XV.stop()
        XV.onDisable()
        HandlerList.unregisterAll(this)
        ProtocolLibrary.getProtocolManager().removePacketListeners(this)
        logger.info("wtf i hate xeth now")
    }
}
