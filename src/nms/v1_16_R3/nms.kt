/**
 * NMS 1.16.5 type aliases
 * https://nms.screamingsandals.org/1.16.5/
 * 
 * NOTE: for 1.16.5, use the "Spigot" names for classes and methods.
 */

package phonon.xc.nms

import net.minecraft.server.v1_16_R3.EntityPlayer
import net.minecraft.server.v1_16_R3.ItemStack as ItemStack
import net.minecraft.server.v1_16_R3.NBTBase
import net.minecraft.server.v1_16_R3.NBTTagCompound
import net.minecraft.server.v1_16_R3.NBTTagList
import net.minecraft.server.v1_16_R3.NBTTagString
import net.minecraft.server.v1_16_R3.NBTTagInt
import net.minecraft.server.v1_16_R3.Packet
import net.minecraft.server.v1_16_R3.PacketListener
import net.minecraft.server.v1_16_R3.PacketPlayOutSetSlot
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_16_R3.util.CraftMagicNumbers

internal typealias NmsItemStack = ItemStack
internal typealias NmsNBTTagCompound = NBTTagCompound
internal typealias NmsNBTTagList = NBTTagList
internal typealias NmsNBTTagString = NBTTagString
internal typealias NmsNBTTagInt = NBTTagInt
internal typealias NmsPacketPlayOutSetSlot = PacketPlayOutSetSlot
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
    val nmsItem = nmsPlayer.inventory.getItemInHand()
    return nmsItem
}

/**
 * Wrapper for getting player connection and sending packet.
 * Function names differ in 1.18.2.
 */
internal fun <T: PacketListener> EntityPlayer.sendPacket(p: Packet<T>) {
    return this.playerConnection.sendPacket(p)
}

/**
 * Wrapper to send a PacketPlayOutSetSlot to a player inventory slot.
 * Required because PacketPlayOutSetSlot fields change between versions.
 */
internal fun EntityPlayer.sendItemSlotChange(slot: Int, item: ItemStack) {
    // https://nms.screamingsandals.org/1.16.5/net/minecraft/network/protocol/game/ClientboundContainerSetSlotPacket.html
    // Format:
    //      NmsPacketPlayOutSetSlot(containerId, slotId, stateId, item)
    // - player's containerId = 0
    // - slotId = slot input
    // - item = nms item
    val packet = NmsPacketPlayOutSetSlot(0, slot, item)
    this.playerConnection.sendPacket(packet)
}

/**
 * Compatibility wrapper for setting NBT tag in compound tag.
 * Using "putTag" because actual function name "put" in 1.18.2 is
 * too common.
 */
internal fun NBTTagCompound.putTag(key: String, tag: NBTBase) {
    this.set(key, tag)
}

/**
 * Compatibility wrapper for compound tag "hasKey".
 */
internal fun NBTTagCompound.containsKey(key: String): Boolean {
    return this.hasKey(key)
}

/**
 * Compatibility wrapper for compound tag "hasKeyOfType".
 */
internal fun NBTTagCompound.containsKeyOfType(key: String, ty: Int): Boolean {
    return this.hasKeyOfType(key, ty)
}

// ============================================================================
// NBT TAG OBFUSCATED FUNCTION WRAPPERS
// Problem: NBT tags created using static functions...cannot write extension
// function version compatibility wrappers for static functions... :^(
// So instead use a value class wrapper.
// ============================================================================

@JvmInline
internal value class NBTTagString(val tag: NmsNBTTagString) {
    constructor(s: String) : this(NmsNBTTagString.a(s)) // a == valueOf(str)

    fun toNms(): NmsNBTTagString {
        return this.tag
    }
}

@JvmInline
internal value class NBTTagInt(val tag: NmsNBTTagInt) {
    constructor(i: Int) : this(NmsNBTTagInt.a(i)) // a == valueOf(str)

    fun toNms(): NmsNBTTagInt {
        return this.tag
    }
}
