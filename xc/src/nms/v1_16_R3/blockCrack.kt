/**
 * NMS 1.16.5 compatibility for block crack/break animation packets.
 * https://nms.screamingsandals.org/1.16.5/
 * 
 * NOTE: for 1.16.5, use the "Spigot" names for classes and methods.
 */

package phonon.xc.nms.blockcrack

import net.minecraft.server.v1_16_R3.BlockPosition 
import net.minecraft.server.v1_16_R3.PacketPlayOutBlockBreakAnimation  
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer
import org.bukkit.World
import phonon.xc.nms.broadcastPacketWithinDistance


public fun World.broadcastBlockCrackAnimation(
    players: List<CraftPlayer>,
    entityId: Int,
    blx: Int,
    bly: Int,
    blz: Int,
    breakStage: Int,
) {
    // https://nms.screamingsandals.org/1.16.5/net/minecraft/network/protocol/game/ClientboundBlockDestructionPacket.html
    val packet = PacketPlayOutBlockBreakAnimation(entityId, BlockPosition(blx, bly, blz), breakStage)
    
    players.broadcastPacketWithinDistance(
        packet,
        originX = blx.toDouble(),
        originY = bly.toDouble(),
        originZ = blz.toDouble(),
        maxDistance = 64.0,
    )
}