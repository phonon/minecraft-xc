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
import org.bukkit.World


public fun World.broadcastBlockCrackAnimation(
    entityId: Int,
    blx: Int,
    bly: Int,
    blz: Int,
    breakStage: Int,
) {
    // https://nms.screamingsandals.org/1.16.5/net/minecraft/network/protocol/game/ClientboundBlockDestructionPacket.html
    val packet = PacketPlayOutBlockBreakAnimation(entityId, BlockPosition(blx, bly, blz), breakStage)

    // https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/browse/nms-patches/net/minecraft/server/level/WorldServer.patch
    // https://nms.screamingsandals.org/1.16.5/net/minecraft/server/players/PlayerList.html
    (this as CraftWorld).getHandle().getServer().server.getPlayerList().sendAll(packet)
}