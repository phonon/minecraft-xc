/**
 * Packet handlers for aim down sights model for player
 */

package phonon.xc.compatibility.v1_16_R3.gun.aimdownsights

import kotlin.math.min
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.persistence.PersistentDataContainer

import net.minecraft.server.v1_16_R3.NBTTagCompound
import net.minecraft.server.v1_16_R3.ItemStack as NMSItemStack
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer

import phonon.xc.XC
import phonon.xc.gun.Gun
import phonon.xc.gun.AimDownSightsModel


public class AimDownSightsModelPacketManager(
    gun: Gun
): AimDownSightsModel {
    
    override fun create(player: Player) {
        
    }

    override fun destroy(player: Player) {
        
    }
}
