/*
 * Implement bukkit plugin interface
 */

package phonon.xv

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.command.TabCompleter
import org.bukkit.event.HandlerList
import kotlin.system.measureTimeMillis
import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListenerCommon
import com.github.retrooper.packetevents.event.PacketListenerPriority
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import io.github.retrooper.packetevents.injector.SpigotChannelInjector
import org.bukkit.Bukkit
import org.bukkit.entity.ArmorStand
import phonon.xc.XC
import phonon.xc.XCPlugin
import phonon.xv.XV
import phonon.xv.command.*
import phonon.xv.listener.*
import phonon.xv.util.file.readJson
import phonon.xv.util.file.writeJson
import java.util.logging.Level

public class XVPlugin : JavaPlugin() {

    // plugin internal state
    var xv: XV? = null

    // listeners (store refs so we can register/unregister)
    var controlsPacketListener: PacketListenerCommon? = null
    
    /**
     * Setup packet events api, see:
     * https://github.com/retrooper/packetevents-example/blob/2.0/thread-safe-listener/src/main/java/main/Main.java
     */
    override fun onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        // Are all listeners read only?
        PacketEvents.getAPI().getSettings().readOnlyListeners(true)
        PacketEvents.getAPI().load()
    }

    override fun onEnable() {
        
        // measure load time
        val timeStart = System.currentTimeMillis()

        val logger = this.getLogger()
        val pluginManager = this.getServer().getPluginManager()

        val xcPlugin = pluginManager.getPlugin("xc") as XCPlugin
        if ( xcPlugin === null || pluginManager.isPluginEnabled(xcPlugin) == false ) {
            logger.severe("xc plugin not found")
            return
        }

        val xv = XV(
            xcPlugin.xc,
            this,
            this.getLogger(),
        )

        // register listeners
        pluginManager.registerEvents(ArmorstandListener(xv), this)
        pluginManager.registerEvents(EventListener(xv), this)
        pluginManager.registerEvents(DamageListener(xv), this)
        
        // PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        // // Are all listeners read only?
        // PacketEvents.getAPI().getSettings().readOnlyListeners(true)
        //     .checkForUpdates(true)
        // PacketEvents.getAPI().load()
        // println("packetevents loaded: ${PacketEvents.getAPI().isLoaded()} initialized: ${PacketEvents.getAPI().isInitialized()}")
        this.controlsPacketListener = PacketEvents.getAPI().getEventManager().registerListener(ControlsListener(xv), PacketListenerPriority.MONITOR)
        PacketEvents.getAPI().init()

        // update players
        val packetInjector = PacketEvents.getAPI().getInjector() as SpigotChannelInjector
        for ( user in PacketEvents.getAPI().getProtocolManager().getUsers() ) {
            val player = Bukkit.getPlayer(user.getUUID())
            println("user: ${user}, ${user.getUUID()}, ${user.getProfile()}, ${user.getProfile().getName()} player: ${player?.name}")
            if ( player !== null ) {
                packetInjector.updatePlayer(user, player)
            }
        }

        // register commands
        this.getCommand("xv")?.setExecutor(Command(xv))
        this.getCommand("vehicledecal")?.setExecutor(VehicleDecalCommand(xv))
        this.getCommand("vehicleskin")?.setExecutor(VehicleSkinCommand(xv))
        
        // override command aliases tab complete if they exist
        this.getCommand("xv")?.setTabCompleter(this.getCommand("xv")?.getExecutor() as TabCompleter)
        
        // load configs, vehicles, etc.
        xv.reload()

        // start engine
        xv.start()

        // save reference
        this.xv = xv

        // print load time
        val timeEnd = System.currentTimeMillis()
        val timeLoad = timeEnd - timeStart
        logger.info("Enabled in ${timeLoad}ms")

        // print success message
        logger.info("now this is epic")
    }

    override fun onDisable() {
        this.xv?.stop()
        this.xv?.saveVehiclesJson()
        HandlerList.unregisterAll(this)
        // PacketEvents.getAPI().getEventManager().unregisterListener(this.controlsPacketListener)
        // PacketEvents.getAPI().terminate()
        logger.info("wtf i hate xeth now")
    }
}
