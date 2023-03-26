/**
 * Utility to make player crawl for shooting.
 * Packaged with `gun` since this automatically handles gun ads
 * models when starting/stopping crawling.
 * 
 * Based on GSit method:
 * - If block above is air, put a fake barrier above player
 * - Else uses a fake shulker entity above player which forces player into
 * crawling position (since shulker is like a block)
 *   HOWEVER, shulker HEAD will NEVER BE INVISIBLE so looks ugly...
 * https://github.com/Gecolay/GSit/blob/main/v1_17_R1/src/main/java/dev/geco/gsit/mcv/v1_17_R1/objects/GCrawl.java
 * https://github.com/Gecolay/GSit/blob/main/v1_17_R1/src/main/java/dev/geco/gsit/mcv/v1_17_R1/objects/BoxEntity.java
 * 
 */

package phonon.xc.nms.gun.crawl

import net.minecraft.server.v1_16_R3.DataWatcher
import net.minecraft.server.v1_16_R3.EnumDirection
import net.minecraft.server.v1_16_R3.EntityTypes
import net.minecraft.server.v1_16_R3.EntityShulker
import net.minecraft.server.v1_16_R3.PacketPlayOutSpawnEntityLiving // ClientboundAddMobPacket
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityDestroy // ClientboundRemoveEntitiesPacket
// import net.minecraft.server.v1_16_R3.PacketPlayOutEntityTeleport // ClientboundTeleportEntityPacket
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityMetadata // ClientboundSetEntityDataPacket
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer
import org.bukkit.Location
import org.bukkit.entity.Player

// ==================================================================
// Entity Shulker in 1.16.5:
// ==================================================================
// public class EntityShulker extends EntityGolem implements IMonster {

//     private static final UUID bp = UUID.fromString("7E0292F2-9434-48D5-A29F-9583AF7DF27F");
//     private static final AttributeModifier bq = new AttributeModifier(EntityShulker.bp, "Covered armor bonus", 20.0D, AttributeModifier.Operation.ADDITION);
//     public static final DataWatcherObject<EnumDirection> b = DataWatcher.a(EntityShulker.class, DataWatcherRegistry.n); // PAIL protected -> public, rename ATTACH_FACE
//     protected static final DataWatcherObject<Optional<BlockPosition>> c = DataWatcher.a(EntityShulker.class, DataWatcherRegistry.m);
//     protected static final DataWatcherObject<Byte> d = DataWatcher.a(EntityShulker.class, DataWatcherRegistry.a);
//     public static final DataWatcherObject<Byte> COLOR = DataWatcher.a(EntityShulker.class, DataWatcherRegistry.a);
//     private float br;
//     private float bs;
//     private BlockPosition bt = null;
//     private int bu;

//     public EntityShulker(EntityTypes<? extends EntityShulker> entitytypes, World world) {
//         super(entitytypes, world);
//         this.f = 5;
//     }
//    ...
//
//     @Override
//     protected void initDatawatcher() {
//         super.initDatawatcher();
//         this.datawatcher.register(EntityShulker.b, EnumDirection.DOWN);
//         this.datawatcher.register(EntityShulker.c, Optional.empty());
//         this.datawatcher.register(EntityShulker.d, (byte) 0);
//         this.datawatcher.register(EntityShulker.COLOR, (byte) 16);
//     }

//     @Override
//     public void loadData(NBTTagCompound nbttagcompound) {
//         super.loadData(nbttagcompound);
//         this.datawatcher.set(EntityShulker.b, EnumDirection.fromType1(nbttagcompound.getByte("AttachFace")));
//         this.datawatcher.set(EntityShulker.d, nbttagcompound.getByte("Peek"));
//         this.datawatcher.set(EntityShulker.COLOR, nbttagcompound.getByte("Color"));
//         if (nbttagcompound.hasKey("APX")) {
//             int i = nbttagcompound.getInt("APX");
//             int j = nbttagcompound.getInt("APY");
//             int k = nbttagcompound.getInt("APZ");
    
//             this.datawatcher.set(EntityShulker.c, Optional.of(new BlockPosition(i, j, k)));
//         } else {
//             this.datawatcher.set(EntityShulker.c, Optional.empty());
//         }
    
//     }
    
//     @Override
//     public void saveData(NBTTagCompound nbttagcompound) {
//         super.saveData(nbttagcompound);
//         nbttagcompound.setByte("AttachFace", (byte) ((EnumDirection) this.datawatcher.get(EntityShulker.b)).c());
//         nbttagcompound.setByte("Peek", (Byte) this.datawatcher.get(EntityShulker.d));
//         nbttagcompound.setByte("Color", (Byte) this.datawatcher.get(EntityShulker.COLOR));
//         BlockPosition blockposition = this.eM();
    
//         if (blockposition != null) {
//             nbttagcompound.setInt("APX", blockposition.getX());
//             nbttagcompound.setInt("APY", blockposition.getY());
//             nbttagcompound.setInt("APZ", blockposition.getZ());
//         }
    
//     }

//  ...
// }
// ==================================================================

/**
 * Shulker entity used to force player into crawling position
 * when cannot place a barrier block.
 */
public class BoxEntity(
    location: Location,
): EntityShulker(EntityTypes.SHULKER, (location.getWorld() as CraftWorld).getHandle()) {

    init {
        this.persist = false

        this.setInvisible(true)
        this.setNoGravity(true)
        this.setInvulnerable(true)
        this.setNoAi(true)
        this.setSilent(true)
        this.setAttachedFace(EnumDirection.UP)
        this.setPeekAmount(0.0)
    }

    // for 1.16.5: set no ai:
    // https://www.spigotmc.org/threads/nms-entity-disable-ai.404800/#post-3609716
    public fun setNoAi(noAi: Boolean) {
        this.k(noAi)
    }

    public fun setAttachedFace(dir: EnumDirection) {
        this.datawatcher.set(EntityShulker.b, dir)
    }

    /**
     * Set peek amount between [0, 1] = block size of peak
     * https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/browse/src/main/java/org/bukkit/craftbukkit/entity/CraftShulker.java#49
     */
    internal fun setPeekAmount(value: Double) {
        val peek = value.coerceIn(0.0, 1.0)
        this.datawatcher.set(EntityShulker.d, (peek * 100.0).toInt().toByte())
    }
    
    public fun canChangeDimensions(): Boolean { return false }

    public fun isAffectedByFluids(): Boolean { return false }

    public fun isSensitiveToWater(): Boolean { return false }

    public fun rideableUnderWater(): Boolean { return true }

    /**
     * Wrapper around setLocation( ... ) in 1.16.5.
     */
    public fun moveTo(x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        this.setLocation(x, y, z, yaw, pitch)
    }

    /**
     * Match 1.18.2 api for getting entity synced data watcher.
     */
    public fun getEntityData(): DataWatcher {
        return this.datawatcher
    }

    /**
     * Moves the box entity above input PLAYER location such that player
     * is forced into a crawling position. In 1.16.5, we can create a
     * fake shulker entity at ANY LOCATION, so we can just directly
     * move it 1.25 above the location. (In 1.18.2, shulkers are
     * clamped onto blocks and 0.5 y increments... sad!)
     */
    internal fun moveAboveLocation(loc: Location) {
        this.setLocation(
            loc.x,
            loc.y + 1.25,
            loc.z,
            0f,
            0f,
        )
    }

    /**
     * Currently works by sending packet to respawn the shulker box.
     * Teleport packet does not seem to work, despite seeming correct
     * based on what packet should be...wtf?
     */
    internal fun sendCreatePacket(player: Player) {
        // println("sendUpdateCrawlPacket $loc")

        // send packets to create fake shulker box
        val packetSpawn = PacketPlayOutSpawnEntityLiving(this)

        // entity metadata
        // https://wiki.vg/Entity_metadata#Shulker
        // https://aadnk.github.io/ProtocolLib/Javadoc/com/comphenix/protocol/wrappers/WrappedDataWatcher.html
        // https://github.com/aadnk/PacketWrapper/blob/master/PacketWrapper/src/main/java/com/comphenix/packetwrapper/WrapperPlayServerEntityMetadata.java
        // getWatchableObjects()
        val packetEntityData = PacketPlayOutEntityMetadata(this.getId(), this.getEntityData(), true)

        val playerConnection = (player as CraftPlayer).handle.playerConnection
        playerConnection.sendPacket(packetSpawn)
        playerConnection.sendPacket(packetEntityData)
    }

    /**
     * Send packet destroying this entity.
     */
    internal fun sendDestroyPacket(player: Player) {
        val packet = PacketPlayOutEntityDestroy(this.id)
        (player as CraftPlayer).handle.playerConnection.sendPacket(packet)
    }
    
    /**
     * Send packet updating Shulker location.
     * NOTE: in 1.16.5 move packet does not work...need to re-create shulker
     * using a create packet :^(((
     */
    internal fun sendMovePacket(player: Player) {
        this.sendCreatePacket(player)
        // DOES NOT WORK IN 1.16.5
        // val packet = PacketPlayOutEntityTeleport(this)
        // (player as CraftPlayer).handle.playerConnection.sendPacket(packet)
    }
    
    /**
     * Send entity metadata packet that updates Shulker peek amount.
     * Required when y-height location changes and peek is adjusted
     * when forcing players to crawl.
     */
    internal fun sendPeekMetadataPacket(player: Player) {
        val packet = PacketPlayOutEntityMetadata(this.getId(), this.getEntityData(), true)
        (player as CraftPlayer).handle.playerConnection.sendPacket(packet)
    }
}
