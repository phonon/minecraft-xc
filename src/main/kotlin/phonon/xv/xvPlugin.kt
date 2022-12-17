/*
 * Implement bukkit plugin interface
 */

package phonon.xv

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.command.TabCompleter
import org.bukkit.event.HandlerList
import kotlin.system.measureTimeMillis
import com.comphenix.protocol.ProtocolLibrary
import phonon.xv.XV
import phonon.xv.command.*
import phonon.xv.listener.*

public class XVPlugin : JavaPlugin() {

    // plugin internal state
    val xv: XV = XV(
        this,
        this.getLogger(),
    )

    override fun onEnable() {
        
        // measure load time
        val timeStart = System.currentTimeMillis()

        val logger = this.getLogger()
        val pluginManager = this.getServer().getPluginManager()

        // register listeners
        pluginManager.registerEvents(EventListener(xv), this)
        pluginManager.registerEvents(ArmorstandListener(xv), this)
        ProtocolLibrary.getProtocolManager().addPacketListener(ControlsListener(xv))

        // register commands
        this.getCommand("xv")?.setExecutor(Command(xv))
        this.getCommand("vehicledecal")?.setExecutor(VehicleDecalCommand(xv))
        this.getCommand("vehicleskin")?.setExecutor(VehicleSkinCommand(xv))
        
        // override command aliases tab complete if they exist
        this.getCommand("xv")?.setTabCompleter(this.getCommand("xv")?.getExecutor() as TabCompleter)
        
        // load configs, save data, etc. and start engine
        xv.reload()
        xv.start()

        // print load time
        val timeEnd = System.currentTimeMillis()
        val timeLoad = timeEnd - timeStart
        logger.info("Enabled in ${timeLoad}ms")

        // print success message
        logger.info("now this is epic")
    }

    override fun onDisable() {
        xv.stop()
        HandlerList.unregisterAll(this)
        ProtocolLibrary.getProtocolManager().removePacketListeners(this)
        logger.info("wtf i hate xeth now")
    }
}
