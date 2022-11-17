/**
 * Packet handlers for aim down sights model for player
 */

package phonon.xc.gun

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

import phonon.xc.nms.NmsPacketPlayOutSetSlot
import phonon.xc.nms.NmsItemStack
import phonon.xc.nms.CraftItemStack
import phonon.xc.nms.CraftPlayer
import phonon.xc.nms.sendItemSlotChange

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
    private val nmsItemAdsModel: NmsItemStack
    
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
        nmsPlayer.sendItemSlotChange(SLOT_OFFHAND, nmsItemAdsModel)
    }


    companion object {
        /**
         * Remove aim down sights model from a player.
         */
        fun destroy(player: Player) {
            val nmsPlayer = (player as CraftPlayer).getHandle()
            nmsPlayer.sendItemSlotChange(SLOT_OFFHAND, NMS_ITEM_NONE)
        }
    }
}
