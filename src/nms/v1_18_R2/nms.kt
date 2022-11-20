/**
 * NMS 1.18.2 type aliases
 * https://nms.screamingsandals.org/1.18.2/
 * 
 * NOTE: for 1.18.2, use the "Mojang" names for classes and methods.
 */

package phonon.xc.nms

import net.minecraft.server.level.ServerPlayer
import net.minecraft.nbt.Tag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.IntTag
import net.minecraft.network.PacketListener
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.world.item.ItemStack
import org.bukkit.craftbukkit.v1_18_R2.inventory.CraftItemStack
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_18_R2.util.CraftMagicNumbers

internal typealias NmsItemStack = ItemStack
internal typealias NmsNBTTagCompound = CompoundTag
internal typealias NmsNBTTagList = ListTag
internal typealias NmsNBTTagString = StringTag
internal typealias NmsNBTTagInt = IntTag
internal typealias NmsPacketPlayOutSetSlot = ClientboundContainerSetSlotPacket
internal typealias CraftItemStack = CraftItemStack
internal typealias CraftPlayer = CraftPlayer
internal typealias CraftMagicNumbers = CraftMagicNumbers

// ============================================================================
// EXTENSION FUNCTIONS FOR API COMPATIBILITY
// ============================================================================

/**
 * Get player selected item as an NMS ItemStack. 
 */
internal fun CraftPlayer.getMainHandNMSItem(): NmsItemStack {
    val nmsPlayer = this.getHandle()
    return nmsPlayer.getMainHandItem()
}

/**
 * Wrapper for getting player connection and sending packet.
 * Function names differ in 1.18.2.
 */
internal fun <T: PacketListener> ServerPlayer.sendPacket(p: Packet<T>) {
    return this.connection.send(p)
}

/**
 * Send packet to all players in list of players within distance
 * from origin.
 * Similar to protocol lib:
 * https://github.com/dmulloy2/ProtocolLib/blob/4bc9e8b7b78196c99d95330005171ced8ed5b73d/src/main/java/com/comphenix/protocol/injector/PacketFilterManager.java#L268
 */
internal fun <T: PacketListener> List<CraftPlayer>.broadcastPacketWithinDistance(
    packet: Packet<T>,
    originX: Double,
    originY: Double,
    originZ: Double,
    maxDistance: Double,
) {
    val maxDistanceSq = maxDistance * maxDistance

    for ( player in this ) {
        val loc = player.location

        val dx = loc.x - originX
        val dy = loc.y - originY
        val dz = loc.z - originZ

        val distanceSq = (dx * dx) + (dy * dy) + (dz * dz)

        if ( distanceSq <= maxDistanceSq ) {
            player.getHandle().connection.send(packet)
        }
    }
}

/**
 * Wrapper to send a PacketPlayOutSetSlot to a player inventory slot.
 * Required because PacketPlayOutSetSlot fields change between versions.
 */
internal fun ServerPlayer.sendItemSlotChange(slot: Int, item: ItemStack) {
    // https://nms.screamingsandals.org/1.18.2/net/minecraft/network/protocol/game/ClientboundContainerSetSlotPacket.html
    // Format:
    //      NmsPacketPlayOutSetSlot(containerId, slotId, stateId, item)
    // - containerId = player's inventoryMenu container id 
    // - slotId = slot input
    // - stateId = used to synchronize slot??
    //   https://www.spigotmc.org/threads/playerinventory-setitem-and-packetplayoutsetslot-code-procedure-differences.532343/
    //   https://www.spigotmc.org/threads/packet-set-slot-and-window-items-field.517201/
    // - item = nms item
    val packet = NmsPacketPlayOutSetSlot(
        this.inventoryMenu.containerId,
        this.inventoryMenu.incrementStateId(),
        slot,
        item,
    )
    this.connection.send(packet)
}

/**
 * Compatibility wrapper for setting NBT tag in compound tag.
 * Using "putTag" because actual function name "put" in 1.18.2 is
 * too common.
 */
internal fun CompoundTag.putTag(key: String, tag: Tag) {
    this.put(key, tag)
}

/**
 * Compatibility wrapper for compound tag "hasKey" in older versions.
 */
internal fun CompoundTag.containsKey(key: String): Boolean {
    return this.contains(key)
}

/**
 * Compatibility wrapper for compound tag "hasKeyOfType" in older versions.
 */
internal fun CompoundTag.containsKeyOfType(key: String, ty: Int): Boolean {
    return this.contains(key, ty)
}

// ============================================================================
// NBT TAG OBFUSCATED FUNCTION WRAPPERS
// Problem: NBT tags created using static functions...cannot write extension
// function version compatibility wrappers for static functions... :^(
// So instead use a value class wrapper.
// ============================================================================

@JvmInline
internal value class NBTTagString(val tag: NmsNBTTagString) {
    constructor(s: String) : this(NmsNBTTagString.valueOf(s))

    fun toNms(): NmsNBTTagString {
        return this.tag
    }
}

@JvmInline
internal value class NBTTagInt(val tag: NmsNBTTagInt) {
    constructor(i: Int) : this(NmsNBTTagInt.valueOf(i))

    fun toNms(): NmsNBTTagInt {
        return this.tag
    }
}
