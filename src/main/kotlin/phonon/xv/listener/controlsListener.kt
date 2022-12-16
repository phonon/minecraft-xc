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
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.utility.MinecraftReflection
import com.comphenix.protocol.wrappers.WrappedChatComponent
import com.comphenix.protocol.wrappers.WrappedWatchableObject
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.events.ConnectionSide
import com.comphenix.protocol.events.ListenerPriority
import phonon.xv.XV
import phonon.xv.common.UserInput

// return instance of PacketAdapter listener bound to this java plugin
public fun ControlsListener(xv: XV): PacketAdapter {

    return object: PacketAdapter(PacketAdapter.AdapterParameteters()
        .plugin(xv.plugin)
        .connectionSide(ConnectionSide.CLIENT_SIDE)
        .types(setOf(PacketType.Play.Client.STEER_VEHICLE))
        .listenerPriority(ListenerPriority.MONITOR)
    ) {
        override public fun onPacketReceiving(event: PacketEvent) {
            val packet = event.packet
            val player = event.player

            if ( player.isInsideVehicle() ) {
                val entity = player.getVehicle()
                // if ( entity !== null && entity.type == EntityType.ARMOR_STAND ) {
                if ( entity !== null ) {
                    // unpack and write to user input
                    val sideways = packet.getFloat().read(0)
                    val forward = packet.getFloat().read(1)
                    val isJump = packet.getBooleans().read(0)
                    val isUnmount = packet.getBooleans().read(1)

                    val userInput = UserInput(
                        forward = forward > 0f,
                        backward = forward < 0f,
                        left = sideways > 0f,
                        right = sideways < 0f,
                        jump = isJump,
                        shift = isUnmount,
                    )

                    xv.userInputs[player.getUniqueId()] = userInput

                    ////// DEPRECATED: only writes if this is a vehicle element
                    // val uuid = entity.getUniqueId()
                    // val vehicleElement = Vehicle.entityToVehicleElement.get(uuid)
                    // if ( vehicleElement !== null ) {
                    //     // unpack
                    //     val sideways = packet.getFloat().read(0)
                    //     val forward = packet.getFloat().read(1)
                    //     val isJump = packet.getBooleans().read(0)
                    //     val isUnmount = packet.getBooleans().read(1)
                    // }
                }
            }

        }
    }

}