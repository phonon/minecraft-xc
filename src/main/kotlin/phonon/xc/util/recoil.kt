/**
 * Manage recoil packet wrapper and async task to send recoil
 * packets to clients. 
 */

package phonon.xc.util.recoil

import java.util.concurrent.ThreadLocalRandom
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import phonon.xc.gun.Gun
import phonon.xc.util.math.directionFromYawPitch
import phonon.xc.nms.recoil.sendRecoilPacketUsingLookAt
import phonon.xc.nms.recoil.sendRecoilPacketUsingRelativeTeleport

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

/**
 * Runnable task to send visual recoil packets to players.
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

            // use specific vehicle recoil packet if in vehicle
            // NOTE: IN 1.18.2 THIS IS NOW SMOOTH!!! :^)))
            // but not in 1.16.5 :^(
            if ( isInVehicle ) {
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

                player.sendRecoilPacketUsingLookAt(
                    playerEyeLocation.x + newViewDirection.x,
                    playerEyeLocation.y + mountOffsetY + newViewDirection.y,
                    playerEyeLocation.z + newViewDirection.z,
                )
            }
            else { // default recoil packet using relative teleport packet
                player.sendRecoilPacketUsingRelativeTeleport(
                    netRecoilHorizontal.toFloat(),
                    -netRecoilVertical.toFloat(),
                )
            }
        }
    }
}

