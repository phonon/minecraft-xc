/*
 * Implement bukkit plugin interface
 */

package phonon.xc

import org.bukkit.command.TabCompleter
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.event.HandlerList
import phonon.xc.commands.*
import phonon.xc.listeners.*
import java.io.File

public class XCPlugin : JavaPlugin() {
    
    override fun onEnable() {
        
        // measure load time
        val timeStart = System.currentTimeMillis()

        val logger = this.getLogger()
        val pluginManager = this.getServer().getPluginManager()

        // ===================================
        // Initialize main plugin:
        // - save hooks to this plugin 
        // - save hooks to external APIs
        // ===================================
        XC.onEnable(this)
        
        // protocol lib hook
        val pluginProtocolLib = pluginManager.getPlugin("ProtocolLib")
        if ( pluginManager.isPluginEnabled("ProtocolLib") && pluginProtocolLib != null ) {
            XC.usingProtocolLib = true
            logger.info("Using ProtocolLib v${pluginProtocolLib.getDescription().getVersion()}")
        }

        // world guard hook
        val pluginWorldGuard = pluginManager.getPlugin("WorldGuard")
        if ( pluginManager.isPluginEnabled("WorldGuard") && pluginWorldGuard != null ) {
            XC.usingWorldGuard = true
            logger.info("Using WorldGuard v${pluginWorldGuard.getDescription().getVersion()}")
        }

        // ===================================
        // Plugin reload
        // ===================================

        // register listeners
        pluginManager.registerEvents(EventListener(this), this)

        // register commands
        this.getCommand("xc")?.setExecutor(Command(this))
        this.getCommand("aimdownsights")?.setExecutor(AimDownSightsCommand(this))
        
        // override command aliases tab complete if they exist
        this.getCommand("xc")?.setTabCompleter(this.getCommand("xc")?.getExecutor() as TabCompleter)
        this.getCommand("ads")?.setTabCompleter(this.getCommand("aimdownsights")?.getExecutor() as TabCompleter)

        // load plugin and start engine
        XC.reload()
        XC.start()

        // print load time
        val timeEnd = System.currentTimeMillis()
        val timeLoad = timeEnd - timeStart
        logger.info("Enabled in ${timeLoad}ms")

        // print success message
        logger.info("now this is epic")
    }

    override fun onDisable() {
        XC.stop()
        XC.onDisable()

        HandlerList.unregisterAll(this);

        logger.info("wtf i hate xeth now")
    }
}
