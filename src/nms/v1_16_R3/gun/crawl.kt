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

import java.util.UUID
import net.minecraft.server.v1_16_R3.EnumDirection
import net.minecraft.server.v1_16_R3.EntityTypes
import net.minecraft.server.v1_16_R3.EntityShulker
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld
import org.bukkit.World
import org.bukkit.block.data.BlockData
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionEffect
import org.bukkit.persistence.PersistentDataType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import phonon.xc.XC
import phonon.xc.util.Message
import phonon.xc.gun.useAimDownSights
import phonon.xc.gun.AmmoInfoMessagePacket
import phonon.xc.util.progressBar10

import phonon.xc.nms.gun.item.*

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
     * Set raw peek amount between [0, 100]
     */
    public fun setRawPeekAmount(amount: Byte) {
        this.datawatcher.set(EntityShulker.d, amount)
    }

    public fun canChangeDimensions(): Boolean { return false }

    public fun isAffectedByFluids(): Boolean { return false }

    public fun isSensitiveToWater(): Boolean { return false }

    public fun rideableUnderWater(): Boolean { return true }

}
