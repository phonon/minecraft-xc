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

import net.minecraft.core.Direction
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.monster.Shulker 
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld
import org.bukkit.Location

// ==================================================================
// Entity Shulker in 1.16.5:
// ==================================================================
// public class Shulker extends EntityGolem implements IMonster {

//     private static final UUID bp = UUID.fromString("7E0292F2-9434-48D5-A29F-9583AF7DF27F");
//     private static final AttributeModifier bq = new AttributeModifier(Shulker.bp, "Covered armor bonus", 20.0D, AttributeModifier.Operation.ADDITION);
//     public static final DataWatcherObject<EnumDirection> b = DataWatcher.a(Shulker.class, DataWatcherRegistry.n); // PAIL protected -> public, rename ATTACH_FACE
//     protected static final DataWatcherObject<Optional<BlockPosition>> c = DataWatcher.a(Shulker.class, DataWatcherRegistry.m);
//     protected static final DataWatcherObject<Byte> d = DataWatcher.a(Shulker.class, DataWatcherRegistry.a);
//     public static final DataWatcherObject<Byte> COLOR = DataWatcher.a(Shulker.class, DataWatcherRegistry.a);
//     private float br;
//     private float bs;
//     private BlockPosition bt = null;
//     private int bu;

//     public Shulker(EntityType<? extends Shulker> EntityType, World world) {
//         super(EntityType, world);
//         this.f = 5;
//     }
//    ...
//
//     @Override
//     protected void initDatawatcher() {
//         super.initDatawatcher();
//         this.datawatcher.register(Shulker.b, EnumDirection.DOWN);
//         this.datawatcher.register(Shulker.c, Optional.empty());
//         this.datawatcher.register(Shulker.d, (byte) 0);
//         this.datawatcher.register(Shulker.COLOR, (byte) 16);
//     }

//     @Override
//     public void loadData(NBTTagCompound nbttagcompound) {
//         super.loadData(nbttagcompound);
//         this.datawatcher.set(Shulker.b, EnumDirection.fromType1(nbttagcompound.getByte("AttachFace")));
//         this.datawatcher.set(Shulker.d, nbttagcompound.getByte("Peek"));
//         this.datawatcher.set(Shulker.COLOR, nbttagcompound.getByte("Color"));
//         if (nbttagcompound.hasKey("APX")) {
//             int i = nbttagcompound.getInt("APX");
//             int j = nbttagcompound.getInt("APY");
//             int k = nbttagcompound.getInt("APZ");
    
//             this.datawatcher.set(Shulker.c, Optional.of(new BlockPosition(i, j, k)));
//         } else {
//             this.datawatcher.set(Shulker.c, Optional.empty());
//         }
    
//     }
    
//     @Override
//     public void saveData(NBTTagCompound nbttagcompound) {
//         super.saveData(nbttagcompound);
//         nbttagcompound.setByte("AttachFace", (byte) ((EnumDirection) this.datawatcher.get(Shulker.b)).c());
//         nbttagcompound.setByte("Peek", (Byte) this.datawatcher.get(Shulker.d));
//         nbttagcompound.setByte("Color", (Byte) this.datawatcher.get(Shulker.COLOR));
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
): Shulker(EntityType.SHULKER, (location.getWorld() as CraftWorld).getHandle()) {

    init {
        this.persist = false

        this.setInvisible(true)
        this.setNoGravity(true)
        this.setInvulnerable(true)
        this.setNoAi(true)
        this.setSilent(true)
        this.setAttachFace(Direction.UP)
    }

    /**
     * Set raw peek amount between [0, 100]
     */
    public fun setRawPeekAmount(amount: Byte) {
        super.setRawPeekAmount(amount.toInt())
    }

    public override fun canChangeDimensions(): Boolean { return false }

    public override fun isAffectedByFluids(): Boolean { return false }

    public override fun isSensitiveToWater(): Boolean { return false }

    public override fun rideableUnderWater(): Boolean { return true }

}
