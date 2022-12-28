/*
 * Implement bukkit plugin interface
 */

package phonon.xv

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.command.TabCompleter
import org.bukkit.event.HandlerList
import kotlin.system.measureTimeMillis
import com.comphenix.protocol.ProtocolLibrary
import org.bukkit.Bukkit
import org.bukkit.entity.ArmorStand
import phonon.xv.XV
import phonon.xv.command.*
import phonon.xv.core.*
import phonon.xv.listener.*
import phonon.xv.system.systemCreateVehicle
import phonon.xv.util.entity.reassociateEntities
import phonon.xv.util.file.readJson
import phonon.xv.util.file.writeJson
import java.util.logging.Level

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
        
        // load configs, etc.
        xv.reload()
        // load data
        xv.loadVehicles(xv.config.pathSave, xv.logger)
        // create all loaded vehicles
        xv.flushCreateQueue()
        // reassociate armorstands w/ newly loaded
        // components
        Bukkit.getWorlds().forEach { w ->
            reassociateEntities(xv, w.entities)
        }
        // start engine
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
        // save data
        xv.saveVehicles(xv.config.pathSave, xv.logger)
        logger.info("wtf i hate xeth now")
    }
}
