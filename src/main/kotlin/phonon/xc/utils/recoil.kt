/**
 * Manage recoil packet wrapper and async task to send recoil
 * packets to clients. 
 */

package phonon.xc.utils.recoil

import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.wrappers.EnumWrappers
import com.comphenix.protocol.utility.MinecraftReflection
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import phonon.xc.gun.Gun


/**
 * Wrapper for player gun recoil data needed to run asynchronously.
 */
public data class RecoilPacket(
    val player: Player,
    val gun: Gun,
)

// https://wiki.vg/Protocol#Player_Position
// https://github.com/WeaponMechanics/MechanicsMain/blob/master/WeaponCompatibility/Weapon_1_16_R3/src/main/java/me/deecaad/weaponmechanics/compatibility/v1_16_R3.java#L39
// This is "X | Y | Z | X_ROT | Y_ROT"
// This makes player position packet a relative position instead of absolute.

// Mineman class for EnumPlayerTeleportFlags
// https://github.com/dmulloy2/PacketWrapper/blob/master/PacketWrapper/src/main/java/com/comphenix/packetwrapper/WrapperPlayServerPosition.java
private val EnumPlayerTeleportFlags = MinecraftReflection.getMinecraftClass("EnumPlayerTeleportFlags", "PacketPlayOutPosition\$EnumPlayerTeleportFlags");

/**
 * Wrapper for teleport packet flags.
 * Makes position relative instead of absolute.
 */
private enum class PlayerTeleportFlag { 
    X,
    Y,
    Z,
    Y_ROT,
    X_ROT,
    ;
}

private val RELATIVE_TELEPORT_FLAGS = setOf(
    PlayerTeleportFlag.X,
    PlayerTeleportFlag.Y,
    PlayerTeleportFlag.Z,
    PlayerTeleportFlag.Y_ROT,
    PlayerTeleportFlag.X_ROT,
)

/**
 * Runnable task to spawn bullet trails.
 */
public class TaskRecoil(
    val protocolManager: ProtocolManager,
    val packets: ArrayList<RecoilPacket>,
): Runnable {
    override fun run() {
        for ( p in packets ) {
            val (player, gun) = p
            
            // https://wiki.vg/Protocol#Player_Position
            // Position packet: 0x36, Play, Server -> Client
            //      X                    Double    Absolute or relative position, depending on Flags.
            //      Y                    Double    Absolute or relative position, depending on Flags.
            //      Z                    Double    Absolute or relative position, depending on Flags.
            //      Yaw                  Float     Absolute or relative rotation on the X axis, in degrees.
            //      Pitch                Float     Absolute or relative rotation on the Y axis, in degrees.
            //      Flags                Byte      Bit field, see below.
            //      Teleport ID          VarInt    Client should confirm this packet with Accept Teleportation containing the same Teleport ID.
            //      Dismount Vehicle     Boolean   True if the player should dismount their vehicle. 
            //
            // Flags: <Dinnerbone> It's a bitfield, X/Y/Z/Y_ROT/X_ROT. If X is set, the x value is relative and not absolute.
            // Field    Bit
            // X        0x01
            // Y        0x02
            // Z        0x04
            // Y_ROT    0x08
            // X_ROT    0x10 
            val yawPacket = protocolManager.createPacket(PacketType.Play.Server.POSITION, false)
            yawPacket.getDoubles().write(0, 0.0)
            yawPacket.getDoubles().write(1, 0.0)
            yawPacket.getDoubles().write(2, 0.0)
            yawPacket.getFloat().write(0, 0f)
            yawPacket.getFloat().write(1, -gun.recoilVertical.toFloat())
            
            // teleport relative position flags
            val teleportFlags = yawPacket.getSets(EnumWrappers.getGenericConverter(EnumPlayerTeleportFlags, PlayerTeleportFlag::class.java))
            teleportFlags.write(0, RELATIVE_TELEPORT_FLAGS)

            try {
                protocolManager.sendServerPacket(player, yawPacket)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

