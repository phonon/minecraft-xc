/**
 * NMS 1.18.2 compatibility for block crack/break animation packets.
 * https://nms.screamingsandals.org/1.18.2/
 * 
 * NOTE: for 1.18.2, use the "Mojang" names for classes and methods.
 */

package phonon.xc.nms.blockcrack

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket 
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld
import org.bukkit.World


public fun World.broadcastBlockCrackAnimation(
    entityId: Int,
    blx: Int,
    bly: Int,
    blz: Int,
    breakStage: Int,
) {
    // https://nms.screamingsandals.org/1.18.2/net/minecraft/network/protocol/game/ClientboundBlockDestructionPacket.html
    val packet = ClientboundBlockDestructionPacket(entityId, BlockPos(blx, bly, blz), breakStage)

    // https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/browse/nms-patches/net/minecraft/server/level/WorldServer.patch
    (this as CraftWorld).getHandle().getServer().getPlayerList().broadcastAll(packet)
}