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
 * https://github.com/Gecolay/GSit/blob/main/v1_18_R2/src/main/java/dev/geco/gsit/mcv/v1_18_R2/objects/GCrawl.java
 * https://github.com/Gecolay/GSit/blob/main/v1_18_R2/src/main/java/dev/geco/gsit/mcv/v1_18_R2/objects/BoxEntity.java
 * 
 */

package phonon.xc.nms.gun.crawl

import kotlin.math.ceil
import kotlin.math.floor
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ClientboundAddMobPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.monster.Shulker
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer
import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * Shulker entity is used to force player into crawling position
 * when we cannot place a barrier block above the player.
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

    public override fun canChangeDimensions(): Boolean { return false }

    public override fun isAffectedByFluids(): Boolean { return false }

    public override fun isSensitiveToWater(): Boolean { return false }

    public override fun rideableUnderWater(): Boolean { return true }

    /**
     * Set peek amount between [0, 1] = block size of peak
     * https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/browse/src/main/java/org/bukkit/craftbukkit/entity/CraftShulker.java#49
     */
    internal fun setPeekAmount(value: Double) {
        val peek = value.coerceIn(0.0, 1.0)
        super.setRawPeekAmount((peek * 100.0).toInt())
    }

    /**
     * Moves the box entity above input PLAYER location and adjusts the
     * shulker peek amount to force player into crawling mode.
     * 
     * The shulker y-height is forced to 0.5 increments. So we need to
     * round upwards to next 0.5 increment, then use peek to force player
     * into crawl:
     *     
     *        shulker
     *         _____
     *        |     |
     *        |_____| ceil(player.y + 1.5)
     *           | 
     *        ======= peek
     *                            } Remaining distance must be
     *                            } ~ 1.0 to force player into crawl
     *        ------- player
     * 
     * Use strategy similar to:
     * https://github.com/Gecolay/GSit/blob/main/v1_18_R2/src/main/java/dev/geco/gsit/mcv/v1_18_R2/objects/GCrawl.java#L151
     */
    internal fun moveAboveLocation(loc: Location) {
        // get height in block
        val heightInBlock = loc.y - floor(loc.y)

        val yHeightShulker: Double
        val yPeekAmount: Double
        if ( heightInBlock >= 0.4 ) { // on top of half slab, round to 1.5 above, then use peek
            yHeightShulker = loc.y + 1.5
            yPeekAmount = 1.0 - heightInBlock
        } else { // close enough to ground, can place shulker directly above and not use peek
            yHeightShulker = loc.y + 0.5
            yPeekAmount = 0.0
        }

        this.moveTo(
            loc.x,
            yHeightShulker,
            loc.z,
            0f,
            0f,
        )
        this.setPeekAmount(yPeekAmount) // internally will be clamped to [0, 1]

        // println("yHeightShulker: $yHeightShulker, yPeekAmount: $yPeekAmount")
    }

    /**
     * Currently works by sending packet to respawn the shulker box.
     * Teleport packet does not seem to work, despite seeming correct
     * based on what packet should be...wtf?
     */
    internal fun sendCreatePacket(player: Player) {
        // println("sendUpdateCrawlPacket $loc")

        // send packets to create fake shulker box
        val packetSpawn = ClientboundAddMobPacket(this)

        // entity metadata
        // https://wiki.vg/Entity_metadata#Shulker
        val packetEntityData = ClientboundSetEntityDataPacket(this.getId(), this.getEntityData(), true)

        val playerConnection = (player as CraftPlayer).handle.connection
        playerConnection.send(packetSpawn)
        playerConnection.send(packetEntityData)
    }
    
    /**
     * Send packet destroying this entity.
     */
    internal fun sendDestroyPacket(player: Player) {
        val packet = ClientboundRemoveEntitiesPacket(this.id)
        (player as CraftPlayer).handle.connection.send(packet)
    }
    
    /**
     * Send packet updating Shulker location.
     */
    internal fun sendMovePacket(player: Player) {
        val packet = ClientboundTeleportEntityPacket(this)
        (player as CraftPlayer).handle.connection.send(packet)
    }
    
    /**
     * Send entity metadata packet that updates Shulker peek amount.
     * Required when y-height location changes and peek is adjusted
     * when forcing players to crawl.
     */
    internal fun sendPeekMetadataPacket(player: Player) {
        val packet = ClientboundSetEntityDataPacket(this.getId(), this.getEntityData(), true)
        (player as CraftPlayer).handle.connection.send(packet)
    }
}
