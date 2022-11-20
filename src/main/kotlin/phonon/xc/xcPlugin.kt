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

public class XCPlugin: JavaPlugin() {
    
    // plugin internal state
    val xc: XC = XC(
        this,
        this.getLogger(),
    )
    
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

        // world guard hook
        val pluginWorldGuard = pluginManager.getPlugin("WorldGuard")
        val usingWorldGuard = if ( pluginManager.isPluginEnabled("WorldGuard") && pluginWorldGuard != null ) {
            logger.info("Using WorldGuard v${pluginWorldGuard.getDescription().getVersion()}")
            true
        } else {
            false
        }

        xc.usingWorldGuard(usingWorldGuard)

        // ===================================
        // Plugin reload
        // ===================================

        // register listeners
        pluginManager.registerEvents(EventListener(xc), this)

        // register commands
        this.getCommand("xc")?.setExecutor(Command(xc))
        this.getCommand("aimdownsights")?.setExecutor(AimDownSightsCommand(xc))
        
        // override command aliases tab complete if they exist
        this.getCommand("xc")?.setTabCompleter(this.getCommand("xc")?.getExecutor() as TabCompleter)
        this.getCommand("ads")?.setTabCompleter(this.getCommand("aimdownsights")?.getExecutor() as TabCompleter)

        // load plugin and start engine
        xc.reload()
        xc.start()

        // print load time
        val timeEnd = System.currentTimeMillis()
        val timeLoad = timeEnd - timeStart
        logger.info("Enabled in ${timeLoad}ms")

        // print success message
        logger.info("now this is epic")
    }

    override fun onDisable() {
        xc.stop()
        xc.onDisable()

        HandlerList.unregisterAll(this);

        logger.info("wtf i hate xeth now")
    }
}
