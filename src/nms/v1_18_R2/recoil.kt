/**
 * NMS 1.18.2 compatibility for recoil packets.
 * https://nms.screamingsandals.org/1.18.2/
 * 
 * NOTE: for 1.18.2, use the "Mojang" names for classes and methods.
 */

package phonon.xc.nms.recoil

import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket.RelativeArgument
import net.minecraft.commands.arguments.EntityAnchorArgument
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer
import org.bukkit.entity.Player

/**
 * https://wiki.vg/Protocol#Player_Position
 * This is "X | Y | Z | X_ROT | Y_ROT"
 * This makes player position packet a relative position instead of absolute.
 */
private val RELATIVE_TELEPORT_FLAGS = setOf(
    RelativeArgument.X,
    RelativeArgument.Y,
    RelativeArgument.Z,
    RelativeArgument.Y_ROT,
    RelativeArgument.X_ROT,
)

/**
 * Create gun visual recoil by sending a relative teleport packet
 * that moves the player's yaw/pitch relative to current values.
 * (Default method, gives smooth recoil).
 * HOWEVER, THIS DOES NOT WORK IN VEHICLES!!!
 * 
 * https://nms.screamingsandals.org/1.18.2/net/minecraft/network/protocol/game/ClientboundPlayerPositionPacket.html
 * https://wiki.vg/Protocol#Player_Position
 * Position packet: 0x36, Play, Server -> Client
 *      X                    Double    Absolute or relative position, depending on Flags.
 *      Y                    Double    Absolute or relative position, depending on Flags.
 *      Z                    Double    Absolute or relative position, depending on Flags.
 *      Yaw                  Float     Absolute or relative rotation on the X axis, in degrees.
 *      Pitch                Float     Absolute or relative rotation on the Y axis, in degrees.
 *      Flags                Byte      Bit field, see below.
 *      Teleport ID          VarInt    Client should confirm this packet with Accept Teleportation containing the same Teleport ID.
 *      Dismount Vehicle     Boolean   True if the player should dismount their vehicle. 
 *
 * Flags: <Dinnerbone> It's a bitfield, X/Y/Z/Y_ROT/X_ROT. If X is set, the x value is relative and not absolute.
 * Field    Bit
 * X        0x01
 * Y        0x02
 * Z        0x04
 * Y_ROT    0x08
 * X_ROT    0x10
 */
public fun Player.sendRecoilPacketUsingRelativeTeleport(
    recoilHorizontal: Float,
    recoilVertical: Float,
) {
    val packet = ClientboundPlayerPositionPacket(
        // relative positions = 0,0,0
        0.0,
        0.0,
        0.0,
        // relative yaw, pitch = recoil
        recoilHorizontal,
        recoilVertical,
        // flags
        RELATIVE_TELEPORT_FLAGS,
        // teleport id, is this needed??
        0,
        // dismount vehicle = false
        false,
    )

    (this as CraftPlayer).handle.connection.send(packet)
}

/**
 * Create gun visual recoil by sending packet to tell client to
 * look at a direction. This is not smooth and causes discontinuous
 * motion in client. This is used when player is in vehicle or riding
 * an entity.
 * 
 * https://nms.screamingsandals.org/1.18.2/net/minecraft/network/protocol/game/ClientboundPlayerLookAtPacket.html
 * https://wiki.vg/Protocol#Look_At
 * 0x38, Play, Client
 *     FIELD NAME         FIELD TYPE              NOTES
 *     Feet/eyes          VarInt enum             Values are feet=0, eyes=1. If set to eyes, aims using the head position; otherwise aims using the feet position.
 *     Target x           Double                  x coordinate of the point to face towards.
 *     Target y           Double                  y coordinate of the point to face towards.
 *     Target z           Double                  z coordinate of the point to face towards.
 *     Is entity          Boolean                 If true, additional information about an entity is provided.
 *     Entity ID          Optional VarInt         Only if is entity is true — the entity to face towards.
 *     Entity feet/eyes   Optional VarInt enum    Whether to look at the entity's eyes or feet. Same values and meanings as before, just for the entity's head/feet. 
 */
public fun Player.sendRecoilPacketUsingLookAt(
    dirX: Double,
    dirY: Double,
    dirZ: Double,
) {
    val packet = ClientboundPlayerLookAtPacket(
        EntityAnchorArgument.Anchor.EYES,
        dirX,
        dirY,
        dirZ,
    )

    (this as CraftPlayer).handle.connection.send(packet)
}