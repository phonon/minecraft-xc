/**
 * Listener for player vehicle steering packets
 * 
 * For 1.12.2 packet format:
 * https://github.com/dmulloy2/PacketWrapper/blob/2b4bfddd12b2874ff328015541c5f643c3907b3f/PacketWrapper/src/main/java/com/comphenix/packetwrapper/WrapperPlayClientSteerVehicle.java
 *
 * 
 * NOTE: in paper (or tuinity specifically?) this packet is generated
 * and handled ASYNCHRONOUSLY which means we cannot use default mineman
 * api within the vehicle's onSteer event. It must be re-scheduled onto
 * main thread.
 */

package phonon.xv.listener

import org.bukkit.Bukkit
import org.bukkit.entity.EntityType
import org.bukkit.plugin.java.JavaPlugin
import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSteerVehicle
import phonon.xv.XV
import phonon.xv.common.UserInput

// return instance of PacketAdapter listener bound to this java plugin
public class ControlsListener(val xv: XV): PacketListener {
    
    override public fun onPacketReceive(event: PacketReceiveEvent) {
        val player = Bukkit.getPlayer(event.getUser().getUUID())
        if ( player !== null && player.isInsideVehicle() ) {
            if ( event.getPacketType() == PacketType.Play.Client.STEER_VEHICLE ) {
                val wrappedPacket = WrapperPlayClientSteerVehicle(event)
                // unpack and write to user input
                val sideways = wrappedPacket.getSideways()
                val forward = wrappedPacket.getForward()
                val isJump = wrappedPacket.isJump()
                val isUnmount = wrappedPacket.isUnmount()

                val userInput = UserInput(
                    forward = forward > 0f,
                    backward = forward < 0f,
                    left = sideways > 0f,
                    right = sideways < 0f,
                    jump = isJump,
                    shift = isUnmount,
                )

                xv.userInputs[player.getUniqueId()] = userInput
            }
        }
    }
}