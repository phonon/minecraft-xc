/**
 * Manage recoil packet wrapper and async task to send recoil
 * packets to clients. 
 */

package phonon.xc.util.recoil

import java.util.concurrent.ThreadLocalRandom
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.wrappers.EnumWrappers
import com.comphenix.protocol.utility.MinecraftReflection
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import phonon.xc.gun.Gun
import phonon.xc.util.math.directionFromYawPitch
import phonon.xc.util.reflect.getEnumConstant

/**
 * Wrapper for player gun recoil data needed to run asynchronously.
 */
public data class RecoilPacket(
    val player: Player,
    val isInVehicle: Boolean, // flag if player in vehicle, must use alternate recoil packet
    val mountOffsetY: Double, // mount offset y height, to adjust eye height for recoil calculation
    val recoilVertical: Double,
    val recoilHorizontal: Double,
    val multiplier: Double, // recoil multiplier
)

// https://wiki.vg/Protocol#Player_Position
// https://github.com/WeaponMechanics/MechanicsMain/blob/master/WeaponCompatibility/Weapon_1_16_R3/src/main/java/me/deecaad/weaponmechanics/compatibility/v1_16_R3.java#L39
// This is "X | Y | Z | X_ROT | Y_ROT"
// This makes player position packet a relative position instead of absolute.

// Mineman class for EnumPlayerTeleportFlags
// https://github.com/dmulloy2/PacketWrapper/blob/master/PacketWrapper/src/main/java/com/comphenix/packetwrapper/WrapperPlayServerPosition.java
private val EnumPlayerTeleportFlags = MinecraftReflection.getMinecraftClass("EnumPlayerTeleportFlags", "PacketPlayOutPosition\$EnumPlayerTeleportFlags")

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

private val EnumArgumentAnchor = MinecraftReflection.getMinecraftClass("ArgumentAnchor\$Anchor")
private val enumArgumentAnchorEyes = getEnumConstant(EnumArgumentAnchor, "EYES")!!

/**
 * Runnable task to spawn bullet trails.
 * 
 * Two methods for recoil packet:
 * 1. PacketPlayOutPosition:
 *    - This "teleports" player relative to current position. Allows smooth
 *      camera recoil. This is main method.
 *    - HOWEVER, if player is in vehicle, this will eject the player...
 * 2. PacketPlayOutEntityLook 
 *    - This forces screen to look at a particular location.
 *      It's not smooth, but it prevents players from ejecting from vehicles.
 */
public class TaskRecoil(
    val protocolManager: ProtocolManager,
    val packets: ArrayList<RecoilPacket>,
): Runnable {
    override fun run() {
        val random = ThreadLocalRandom.current()

        for ( p in packets ) {
            val (player, isInVehicle, mountOffsetY, recoilVertical, recoilHorizontal, multiplier) = p
            
            // skip if no recoil
            if ( recoilVertical == 0.0 && recoilHorizontal == 0.0 ) {
                continue
            }
            
            // calculate net recoil after multiplier
            val netRecoilVertical = recoilVertical * multiplier
            val netRecoilHorizontalRange = recoilHorizontal * multiplier
            val netRecoilHorizontal = random.nextDouble(-netRecoilHorizontalRange, netRecoilHorizontalRange)

            if ( isInVehicle ) { // use specific vehicle recoil packet
                // println("USING VEHICLE RECOIL PACKET PacketPlayServerLookAt")

                // calculate new look direction new yaw and pitch
                val playerEyeLocation = player.getEyeLocation()
                val newYaw = playerEyeLocation.yaw + netRecoilHorizontal.toFloat()
                val newPitch = playerEyeLocation.pitch - netRecoilVertical.toFloat()

                // new view direction for player after recoil
                // note: multiplying by 50 to extend out the view
                // if player in a fast moving vehicle, the client can desync from this server
                // view packet. player will move ahead of the view being sent back,
                // which will cause player to suddenly turn around
                val newViewDirection = directionFromYawPitch(newYaw, newPitch).multiply(50.0)

                // println("SENDING YAW PACKET:")
                // println("playerEyeLocation = $playerEyeLocation")
                // println("playerFeetLocation = ${player.getLocation()}")
                // println("playerEyeLocation.yaw = ${playerEyeLocation.yaw}")
                // println("playerEyeLocation.pitch = ${playerEyeLocation.pitch}")
                // println("netRecoilHorizontal = $netRecoilHorizontal")
                // println("netRecoilVertical = $netRecoilVertical")
                // println("newViewDirection = $newViewDirection")

                val yawPacket = protocolManager.createPacket(PacketType.Play.Server.LOOK_AT, false)
                yawPacket.getModifier().write(4, enumArgumentAnchorEyes)
                yawPacket.getDoubles().write(0, playerEyeLocation.x + newViewDirection.x)
                yawPacket.getDoubles().write(1, playerEyeLocation.y + mountOffsetY + newViewDirection.y)
                yawPacket.getDoubles().write(2, playerEyeLocation.z + newViewDirection.z)
                yawPacket.getBooleans().write(0, false)

                try {
                    protocolManager.sendServerPacket(player, yawPacket)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            else { // default recoil packet
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
                yawPacket.getFloat().write(0, netRecoilHorizontal.toFloat())
                yawPacket.getFloat().write(1, -netRecoilVertical.toFloat())
                
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
}

