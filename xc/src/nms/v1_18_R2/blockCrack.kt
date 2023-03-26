/**
 * NMS 1.18.2 compatibility for block crack/break animation packets.
 * https://nms.screamingsandals.org/1.18.2/
 * 
 * NOTE: for 1.18.2, use the "Mojang" names for classes and methods.
 */

package phonon.xc.nms.blockcrack

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.Packet 
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer
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
    // https://nms.screamingsandals.org/1.18.2/net/minecraft/network/protocol/game/ClientboundBlockDestructionPacket.html
    val packet = ClientboundBlockDestructionPacket(entityId, BlockPos(blx, bly, blz), breakStage)

    players.broadcastPacketWithinDistance(
        packet,
        originX = blx.toDouble(),
        originY = bly.toDouble(),
        originZ = blz.toDouble(),
        maxDistance = 64.0,
    )
}
