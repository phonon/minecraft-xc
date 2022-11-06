/**
 * Packet handlers for aim down sights model for player
 */

package phonon.xc.nms.gun

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

import net.minecraft.server.v1_16_R3.PacketPlayOutSetSlot
import net.minecraft.server.v1_16_R3.NBTTagCompound
import net.minecraft.server.v1_16_R3.ItemStack as NMSItemStack
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer

import phonon.xc.XC
import phonon.xc.gun.Gun
import phonon.xc.gun.AimDownSightsModel


// return item slot to no item
private val NMS_ITEM_NONE = CraftItemStack.asNMSCopy(ItemStack(Material.AIR, 1))

// inventory container id
private const val PLAYER_CONTAINER_ID = 0

// item slot for aim down sights model
private const val SLOT_OFFHAND = 45


public class AimDownSightsModelPacketManager(
    gun: Gun,
    materialAimDownSights: Material,
): AimDownSightsModel {
    private val nmsItemAdsModel: NMSItemStack
    
    init {
        val modelId = if ( gun.itemModelAimDownSights > 0 ) {
            gun.itemModelAimDownSights
        } else {
            gun.itemModelDefault
        }

        // create nms item stack for ads model
        val item = ItemStack(materialAimDownSights, 1)
        val itemMeta = item.getItemMeta()
        itemMeta.setCustomModelData(modelId)
        item.setItemMeta(itemMeta)
        nmsItemAdsModel = CraftItemStack.asNMSCopy(item)
    }


    override fun create(player: Player) {
        val nmsPlayer = (player as CraftPlayer).getHandle()
        val packet = PacketPlayOutSetSlot(PLAYER_CONTAINER_ID, SLOT_OFFHAND, nmsItemAdsModel)
        nmsPlayer.playerConnection.sendPacket(packet)
    }


    companion object {
        /**
         * Remove aim down sights model from a player.
         */
        fun destroy(player: Player) {
            val nmsPlayer = (player as CraftPlayer).getHandle()
            val packet = PacketPlayOutSetSlot(PLAYER_CONTAINER_ID, SLOT_OFFHAND, NMS_ITEM_NONE)
            nmsPlayer.playerConnection.sendPacket(packet)
        }
    }
}
