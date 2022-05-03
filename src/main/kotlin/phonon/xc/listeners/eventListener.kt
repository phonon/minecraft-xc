/**
 * Main event listener.
 */

package phonon.xc.listeners

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerChangedMainHandEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.player.PlayerToggleSprintEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerAnimationEvent
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent 
import phonon.xc.XC
import phonon.xc.utils.Message

public class EventListener(val plugin: JavaPlugin): Listener {
    @EventHandler
    public fun onPlayerQuit(e: PlayerQuitEvent) {

    }

    @EventHandler
    public fun onPlayerChangeArmor(e: PlayerArmorChangeEvent) {

    }

    @EventHandler (ignoreCancelled = true)
    public fun onToggleSneak(e: PlayerToggleSneakEvent) {
        
    }

    @EventHandler (ignoreCancelled = true)
    public fun onToggleSprint(e: PlayerToggleSprintEvent) {

    }

    @EventHandler (ignoreCancelled = true)
    public fun onDropItem(e: PlayerDropItemEvent) {

    }

    @EventHandler (ignoreCancelled = true)
    public fun onSwapItem(e: PlayerSwapHandItemsEvent) {

    }

    @EventHandler (ignoreCancelled = true)
    public fun onInteract(e: PlayerInteractEvent) {
        // val player = e.player
        // val equipment = player.equipment
        // if ( equipment == null ) return

        // val itemMainHand = equipment.itemInMainHand
        // if ( itemMainHand.type == XC.config.materialGun ) {
        //     Message.print(player, "Firing")
        //     XC.shootGun(player, XC.gunDebug)
        // }
    }

    @EventHandler (ignoreCancelled = true)
    public fun onInteractAt(e: PlayerInteractAtEntityEvent) {

    }

    @EventHandler (ignoreCancelled = true)
    public fun onAnimation(e: PlayerAnimationEvent) {
        val player = e.player
        val equipment = player.equipment
        if ( equipment == null ) return

        val itemMainHand = equipment.itemInMainHand
        if ( itemMainHand.type == XC.config.materialGun ) {
            Message.print(player, "Firing")
            XC.shootGun(player, XC.gunDebug)
        }
    }

}