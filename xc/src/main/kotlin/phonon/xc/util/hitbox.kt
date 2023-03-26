/**
 * Contain math primitives in here
 */

package phonon.xc.util

import java.util.EnumMap
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sqrt
import org.bukkit.World
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player

/**
 * Hitbox size extents. Hitboxes are all AABBs centered at
 * a location, with equal x, z extents. Since entity locations
 * y is always at bottom of their hitbox, this hitbox size config
 * uses a y vertical offset and a y height. This makes creating
 * hitbox from locations easier.
 */
public data class HitboxSize(
    public val xHalf: Float,
    public val zHalf: Float,
    public val yHeight: Float,
    public val yOffset: Float,
) {
    public val radiusMin = min(min(xHalf, zHalf), yHeight / 2f)

    /**
     * Create clone of this.
     */
    public fun clone(): HitboxSize {
        return HitboxSize(xHalf, zHalf, yHeight, yOffset)
    }
}


/**
 * Axis-aligned bounding box. For use as a hitbox.
 */
public data class Hitbox(
    // entity this is attached to
    public val entity: Entity,

    // world space min point
    public val xmin: Float,
    public val ymin: Float,
    public val zmin: Float,

    // world space max point
    public val xmax: Float,
    public val ymax: Float,
    public val zmax: Float,

    // store center location, used for some area effect checks
    public val xcenter: Float,
    public val ycenter: Float,
    public val zcenter: Float,

    // effective radius for explosion, using MINIMUM of x, y, z
    // extents, so that explosion damage is more conservative.
    // so this is NOT a sphere bound radius
    public val radiusMin: Float,
) {
    // explosion id is a tag for tracking an explosion and which hitboxes
    // have already been damaged by it. since hitboxes can be attached to
    // multiple chunks, we need to track which hitboxes have already been
    // damaged by this explosion. explosion id must be unique per explosion.
    // this value is set by the `createExplosion` function each time
    public var lastExplosionId: Int = -1

    /**
     * Check if hitbox contains a point.
     */
    public fun contains(
        x: Float,
        y: Float,
        z: Float,
    ): Boolean {
        return x >= xmin && x <= xmax &&
            y >= ymin && y <= ymax &&
            z >= zmin && z <= zmax
    }

    /**
     * Return ray-AABB intersection using slab method, based on:
     * https://tavianator.com/cgit/dimension.git/tree/libdimension/bvh/bvh.c#n196
     */
    public fun intersectsRay(
        // ray origin
        rx: Float,
        ry: Float,
        rz: Float,
        // ray inverse normalized direction (1/dir.x, 1/dir.y, 1/dir.z)
        rInvDirX: Float,
        rInvDirY: Float,
        rInvDirZ: Float,
    ): Boolean {
        // This is actually correct, even though it appears not to handle edge cases
        // (ray.n.{x,y,z} == 0).  It works because the infinities that result from
        // dividing by zero will still behave correctly in the comparisons.  Rays
        // which are parallel to an axis and outside the box will have tmin == inf
        // or tmax == -inf, while rays inside the box will have tmin and tmax
        // unchanged.

        val tx1 = (this.xmin - rx) * rInvDirX
        val tx2 = (this.xmax - rx) * rInvDirX

        var tmin = min(tx1, tx2)
        var tmax = max(tx1, tx2)

        val ty1 = (this.ymin - ry) * rInvDirY
        val ty2 = (this.ymax - ry) * rInvDirY

        tmin = max(tmin, min(ty1, ty2))
        tmax = min(tmax, max(ty1, ty2))

        val tz1 = (this.zmin - rz) * rInvDirZ
        val tz2 = (this.zmax - rz) * rInvDirZ

        tmin = max(tmin, min(tz1, tz2))
        tmax = min(tmax, max(tz1, tz2))

        return tmax >= max(0f, tmin)
    }
    
    /**
     * Return ray-AABB intersection using slab method, based on:
     * https://tavianator.com/cgit/dimension.git/tree/libdimension/bvh/bvh.c#n196
     * 
     * Unlike `intersectsRay`, this does same thing but returns the
     * intersection distance in ray space if it intersects.
     * Otherwise, returns null.
     */
    public fun intersectsRayLocation(
        // ray origin
        rx: Float,
        ry: Float,
        rz: Float,
        // ray inverse normalized direction (1/dir.x, 1/dir.y, 1/dir.z)
        rInvDirX: Float,
        rInvDirY: Float,
        rInvDirZ: Float,
    ): Float? {
        // This is actually correct, even though it appears not to handle edge cases
        // (ray.n.{x,y,z} == 0).  It works because the infinities that result from
        // dividing by zero will still behave correctly in the comparisons.  Rays
        // which are parallel to an axis and outside the box will have tmin == inf
        // or tmax == -inf, while rays inside the box will have tmin and tmax
        // unchanged.

        val tx1 = (this.xmin - rx) * rInvDirX
        val tx2 = (this.xmax - rx) * rInvDirX

        var tmin = min(tx1, tx2)
        var tmax = max(tx1, tx2)

        val ty1 = (this.ymin - ry) * rInvDirY
        val ty2 = (this.ymax - ry) * rInvDirY

        tmin = max(tmin, min(ty1, ty2))
        tmax = min(tmax, max(ty1, ty2))

        val tz1 = (this.zmin - rz) * rInvDirZ
        val tz2 = (this.zmax - rz) * rInvDirZ

        tmin = max(tmin, min(tz1, tz2))
        tmax = min(tmax, max(tz1, tz2))

        tmin = max(0f, tmin) // normalize to only forward direction

        return if ( tmax >= tmin ) tmin else null
    }

    /**
     * Return distance to x, y, z location.
     */
    public fun distance(x: Float, y: Float, z: Float): Float {
        val dx = x - this.xcenter
        val dy = y - this.ycenter
        val dz = z - this.zcenter
        return sqrt((dx * dx) + (dy * dy) + (dz * dz))
    
    }
    /**
     * Return distance to line from (ax, ay, az) point and
     * line direction vector components (nx, ny, nz), where
     *     line = (ax, ay, az) + t * (nx, ny, nz).
     * https://en.wikipedia.org/wiki/Distance_from_a_point_to_a_line#Vector_formulation
     */
    public fun distanceToLine(
        // line point (ax, ay, az)
        ax: Float,
        ay: Float,
        az: Float,
        // line normalized direction vector (nx, ny, nz)
        nx: Float,
        ny: Float,
        nz: Float,
    ): Float {
        val pxax = this.xcenter - ax
        val pyay = this.ycenter - ay
        val pzaz = this.zcenter - az
        
        // dot product (p - a) . n
        val paDotN = pxax * nx + pyay * ny + pzaz * nz

        // shortest distance compoennts from hitbox center to line
        val dx = pxax - paDotN * nx
        val dy = pyay - paDotN * ny
        val dz = pzaz - paDotN * nz

        return sqrt((dx * dx) + (dy * dy) + (dz * dz))
    }

    /**
     * Create particles filling volume of the hitbox
     */
    public fun visualize(world: World, particle: Particle) {
        val AMOUNT = 10f
        val MIN_STEP = 0.1f
        val dx = max(MIN_STEP, (xmax - xmin) / AMOUNT)
        val dy = max(MIN_STEP, (ymax - ymin) / AMOUNT)
        val dz = max(MIN_STEP, (zmax - zmin) / AMOUNT)

        var n = 0
        var x = xmin
        while ( x <= xmax ) {
            var y = ymin
            while ( y <= ymax ) {
                var z = zmin
                while ( z <= zmax ) {
                    world.spawnParticle(
                        particle,
                        x.toDouble(),
                        y.toDouble(),
                        z.toDouble(),
                        1
                    )
                    z += dz

                    n += 1
                    // guard against loop too big
                    if ( n > 10000 ) {
                        return
                    }
                }
                y += dy
            }
            x += dx
        }
    }


    companion object {
        /**
         * Create hitbox from an entity and size config.
         */
        @JvmStatic
        public fun from(entity: Entity, size: HitboxSize): Hitbox {
            val loc = entity.location
            val x = loc.x.toFloat()
            val y = loc.y.toFloat() // note: this is always at bottom of entity
            val z = loc.z.toFloat()

            val xmin = x - size.xHalf
            val ymin = y + size.yOffset
            val zmin = z - size.zHalf

            val xmax = x + size.xHalf
            var ymax = ymin + size.yHeight
            val zmax = z + size.zHalf

            // if player, must adjust for sneak
            if ( entity.type == EntityType.PLAYER ) {
                val playerEntity = entity as Player
                if ( playerEntity.isSneaking() ) {
                    ymax -= 0.2f
                } else if ( playerEntity.isSwimming() ) {
                    ymax = ymin + 0.9f
                }
            }

            val xcenter = 0.5f * (xmin + xmax)
            val ycenter = 0.5f * (ymin + ymax)
            val zcenter = 0.5f * (zmin + zmax)

            // calculate radiuses
            val radiusMin = size.radiusMin
            
            return Hitbox(
                entity,
                xmin,
                ymin,
                zmin,
                xmax,
                ymax,
                zmax,
                xcenter,
                ycenter,
                zcenter,
                radiusMin,
            )
        }

        /**
         * Create default entity hitbox half extent mappings.
         */
        internal fun defaultEntityHitboxSizes(): EnumArrayMap<EntityType, HitboxSize> {
            val map = EnumArrayMap.from<EntityType, HitboxSize>({_ -> HitboxSize(0f, 0f, 0f, 0f)})
            
            // each value is (x/2, z/2, y, y_offset)

            // most important! :D NOTE: this is when not sneaking, while sneaking need custom offset...
            map[EntityType.PLAYER] = HitboxSize(0.4f, 0.4f, 1.8f, -0.1f)

            // friendly/neutral mobs (NOTE: does not consider baby vs adult)
            map[EntityType.HORSE] = HitboxSize(0.8f, 0.8f, 1.7f, -0.1f)
            map[EntityType.DONKEY] = HitboxSize(0.8f, 0.8f, 1.6f, -0.1f)
            map[EntityType.MULE] = HitboxSize(0.8f, 0.8f, 1.7f, -0.1f)
            map[EntityType.WOLF] = HitboxSize(0.35f, 0.35f, 0.95f, -0.1f)
            map[EntityType.PIG] = HitboxSize(0.55f, 0.55f, 1.0f, -0.1f)
            map[EntityType.SHEEP] = HitboxSize(0.55f, 0.55f, 1.4f, -0.1f)
            map[EntityType.COW] = HitboxSize(0.55f, 0.55f, 1.5f, -0.1f)
            map[EntityType.MUSHROOM_COW] = HitboxSize(0.55f, 0.55f, 1.5f, -0.1f)
            map[EntityType.CHICKEN] = HitboxSize(0.3f, 0.3f, 0.8f, -0.1f)
            map[EntityType.SQUID] = HitboxSize(0.5f, 0.5f, 0.9f, -0.1f)
            map[EntityType.CAT] = HitboxSize(0.4f, 0.4f, 0.8f, -0.1f)
            map[EntityType.OCELOT] = HitboxSize(0.4f, 0.4f, 0.8f, -0.1f)
            map[EntityType.BAT] = HitboxSize(0.35f, 0.35f, 1.0f, -0.1f)
            map[EntityType.IRON_GOLEM] = HitboxSize(0.8f, 0.8f, 2.8f, -0.1f)
            map[EntityType.VILLAGER] = HitboxSize(0.4f, 0.4f, 2.05f, -0.1f)
            map[EntityType.WANDERING_TRADER] = HitboxSize(0.4f, 0.4f, 2.05f, -0.1f)
            map[EntityType.TRADER_LLAMA] = HitboxSize(0.55f, 0.55f, 1.97f, -0.1f)
            map[EntityType.LLAMA] = HitboxSize(0.55f, 0.55f, 1.97f, -0.1f)
            map[EntityType.SNOWMAN] = HitboxSize(0.45f, 0.45f, 2.0f, -0.1f)
            map[EntityType.BEE] = HitboxSize(0.45f, 0.45f, 0.7f, -0.1f)
            map[EntityType.PARROT] = HitboxSize(0.35f, 0.35f, 1.0f, -0.1f)
            map[EntityType.FOX] = HitboxSize(0.4f, 0.4f, 0.8f, -0.1f)
            map[EntityType.RABBIT] = HitboxSize(0.3f, 0.3f, 0.6f, -0.1f)
            map[EntityType.POLAR_BEAR] = HitboxSize(0.8f, 0.8f, 1.5f, -0.1f)
            map[EntityType.PANDA] = HitboxSize(0.75f, 0.75f, 1.35f, -0.1f)
            map[EntityType.PHANTOM] = HitboxSize(0.55f, 0.55f, 0.6f, -0.1f)
            
            map[EntityType.COD] = HitboxSize(0.35f, 0.35f, 0.4f, -0.1f)
            map[EntityType.SALMON] = HitboxSize(0.45f, 0.45f, 0.5f, -0.1f)
            map[EntityType.PUFFERFISH] = HitboxSize(0.45f, 0.45f, 0.8f, -0.1f)
            map[EntityType.TROPICAL_FISH] = HitboxSize(0.35f, 0.35f, 0.5f, -0.1f)
            map[EntityType.TURTLE] = HitboxSize(0.7f, 0.7f, 0.5f, -0.1f)
            map[EntityType.DOLPHIN] = HitboxSize(0.55f, 0.55f, 0.7f, -0.1f)

            // hostile mobs
            map[EntityType.ZOMBIE] = HitboxSize(0.4f, 0.4f, 2.05f, -0.1f)
            map[EntityType.DROWNED] = HitboxSize(0.4f, 0.4f, 2.05f, -0.1f)
            map[EntityType.HUSK] = HitboxSize(0.4f, 0.4f, 2.05f, -0.1f)
            map[EntityType.ZOMBIE_VILLAGER] = HitboxSize(0.4f, 0.4f, 2.05f, -0.1f)
            map[EntityType.WITCH] = HitboxSize(0.4f, 0.4f, 2.05f, -0.1f)
            map[EntityType.PILLAGER] = HitboxSize(0.4f, 0.4f, 2.05f, -0.1f)
            map[EntityType.PIGLIN] = HitboxSize(0.4f, 0.4f, 2.05f, -0.1f)
            map[EntityType.PIGLIN_BRUTE] = HitboxSize(0.4f, 0.4f, 2.05f, -0.1f)
            map[EntityType.ZOMBIFIED_PIGLIN] = HitboxSize(0.4f, 0.4f, 2.05f, -0.1f)
            map[EntityType.EVOKER] = HitboxSize(0.4f, 0.4f, 2.05f, -0.1f)
            map[EntityType.VINDICATOR] = HitboxSize(0.4f, 0.4f, 2.05f, -0.1f)
            map[EntityType.ILLUSIONER] = HitboxSize(0.4f, 0.4f, 2.05f, -0.1f)
            map[EntityType.VEX] = HitboxSize(0.3f, 0.3f, 0.9f, -0.1f)
            map[EntityType.SKELETON] = HitboxSize(0.4f, 0.4f, 2.1f, -0.1f)
            map[EntityType.STRAY] = HitboxSize(0.4f, 0.4f, 2.1f, -0.1f)
            map[EntityType.WITHER_SKELETON] = HitboxSize(0.45f, 0.45f, 2.50f, -0.1f)
            map[EntityType.SKELETON_HORSE] = HitboxSize(0.8f, 0.8f, 1.7f, -0.1f)
            map[EntityType.ZOMBIE_HORSE] = HitboxSize(0.8f, 0.8f, 1.7f, -0.1f)
            map[EntityType.CREEPER] = HitboxSize(0.4f, 0.4f, 1.8f, -0.1f)
            map[EntityType.SPIDER] = HitboxSize(0.8f, 0.8f, 1.0f, -0.1f)
            map[EntityType.ENDERMAN] = HitboxSize(0.4f, 0.4f, 3.0f, -0.1f)
            map[EntityType.CAVE_SPIDER] = HitboxSize(0.45f, 0.45f, 0.6f, -0.1f)
            map[EntityType.SILVERFISH] = HitboxSize(0.3f, 0.3f, 0.4f, -0.1f)
            map[EntityType.BLAZE] = HitboxSize(0.4f, 0.4f, 1.9f, -0.1f)
            map[EntityType.ENDERMITE] = HitboxSize(0.3f, 0.3f, 0.4f, -0.1f)
            map[EntityType.SHULKER] = HitboxSize(0.6f, 0.6f, 1.1f, -0.1f)
            map[EntityType.HOGLIN] = HitboxSize(0.8f, 0.8f, 1.5f, -0.1f)
            map[EntityType.ZOGLIN] = HitboxSize(0.8f, 0.8f, 1.5f, -0.1f)
            map[EntityType.RAVAGER] = HitboxSize(1.075f, 1.075f, 2.3f, -0.1f)
            map[EntityType.STRIDER] = HitboxSize(0.55f, 0.55f, 1.8f, -0.1f)
            map[EntityType.GUARDIAN] = HitboxSize(0.525f, 0.525f, 0.95f, -0.1f)
            map[EntityType.ELDER_GUARDIAN] = HitboxSize(1.1f, 1.1f, 2.1f, -0.1f)
            map[EntityType.GHAST] = HitboxSize(2.1f, 2.1f, 4.1f, -0.1f)
            map[EntityType.GIANT] = HitboxSize(1.9f, 1.9f, 12.1f, -0.1f)
            map[EntityType.ENDER_DRAGON] = HitboxSize(8.1f, 8.1f, 8.1f, -0.1f)
            map[EntityType.WITHER] = HitboxSize(0.55f, 0.55f, 3.6f, -0.1f)

            // hostiles size dependent, take average
            map[EntityType.SLIME] = HitboxSize(0.4f, 0.4f, 1.2f, -0.1f)
            map[EntityType.MAGMA_CUBE] = HitboxSize(0.4f, 0.4f, 1.2f, -0.1f)

            // non-living entities
            map[EntityType.ARMOR_STAND] = HitboxSize(0.35f, 0.35f, 2.1f, -0.1f)
            map[EntityType.BOAT] = HitboxSize(0.65f, 0.65f, 0.6f, -0.1f)
            map[EntityType.MINECART] = HitboxSize(0.5f, 0.5f, 0.8f, -0.1f)
            map[EntityType.MINECART_CHEST] = HitboxSize(0.5f, 0.5f, 0.8f, -0.1f)
            map[EntityType.MINECART_FURNACE] = HitboxSize(0.5f, 0.5f, 0.8f, -0.1f)
            map[EntityType.MINECART_COMMAND] = HitboxSize(0.5f, 0.5f, 0.8f, -0.1f)
            map[EntityType.MINECART_TNT] = HitboxSize(0.5f, 0.5f, 0.8f, -0.1f)
            map[EntityType.MINECART_HOPPER] = HitboxSize(0.5f, 0.5f, 0.8f, -0.1f)
            map[EntityType.MINECART_MOB_SPAWNER] = HitboxSize(0.5f, 0.5f, 0.8f, -0.1f)
            map[EntityType.ENDER_CRYSTAL] = HitboxSize(1.1f, 1.1f, 2.1f, -0.1f)
            
            // ignored, but here for reference
            // map[EntityType.EGG] = HitboxSize(0.225f, 0.225f, 0.35f, -0.1f)
            // map[EntityType.ARROW] = HitboxSize(0.35f, 0.35f, 0.6f, -0.1f)
            // map[EntityType.SNOWBALL] = HitboxSize(0.225f, 0.225f, 0.35f, -0.1f)
            // map[EntityType.FIREBALL] = HitboxSize(0.6f, 0.6f, 1.1f, -0.1f)
            // map[EntityType.SMALL_FIREBALL] = HitboxSize(0.25625f, 0.25625f, 0.4125f, -0.1f)
            // map[EntityType.ENDER_PEARL] = HitboxSize(0.225f, 0.225f, 0.35f, -0.1f)
            // map[EntityType.ENDER_SIGNAL] = HitboxSize(0.225f, 0.225f, 0.35f, -0.1f)
            // map[EntityType.SPLASH_POTION] = HitboxSize(0.225f, 0.225f, 0.35f, -0.1f)
            // map[EntityType.THROWN_EXP_BOTTLE] = HitboxSize(0.225f, 0.225f, 0.35f, -0.1f)
            // map[EntityType.WITHER_SKULL] = HitboxSize(0.25625f, 0.25625f, 0.4125f, -0.1f)
            // map[EntityType.PRIMED_TNT] = HitboxSize(0.5f, 0.5f, 1.0800000190734864f, -0.1f)
            // map[EntityType.FALLING_BLOCK] = HitboxSize(0.5f, 0.5f, 1.0800000190734864f, -0.1f)
            // map[EntityType.FIREWORK] = HitboxSize(0.225f, 0.225f, 0.35f, -0.1f)
            // map[EntityType.DROPPED_ITEM] = HitboxSize(0.225f, 0.225f, 0.35f, -0.1f)
            // map[EntityType.EXPERIENCE_ORB] = HitboxSize(0.35f, 0.35f, 0.6f, -0.1f)
            // map[EntityType.AREA_EFFECT_CLOUD] = HitboxSize(3.1f, 3.1f, 0.6f, -0.1f)
            // map[EntityType.SHULKER_BULLET] = HitboxSize(0.25625f, 0.25625f, 0.4125f, -0.1f)
            // map[EntityType.SPECTRAL_ARROW] = HitboxSize(0.35f, 0.35f, 0.6f, -0.1f)
            // map[EntityType.DRAGON_FIREBALL] = HitboxSize(0.6f, 0.6f, 1.1f, -0.1f)
            // map[EntityType.EVOKER_FANGS] = HitboxSize(0.35f, 0.35f, 0.9f, -0.1f)
            // map[EntityType.TRIDENT] = HitboxSize(0.35f, 0.35f, 0.6f, -0.1f)
            // map[EntityType.LLAMA_SPIT] = HitboxSize(0.225f, 0.225f, 0.35f, -0.1f)

            return map
        }

        /**
         * Default mappings if entity is targetable. Kept in here since similar
         * to `defaultEntityHitboxSizes()`.
         */
        internal fun defaultEntityTargetable(): EnumArrayMap<EntityType, Boolean> {
            // default insert all living entities
            val map = EnumArrayMap.from<EntityType, Boolean>({type -> type.isAlive()})

            // insert specific non-living entities
            map[EntityType.BOAT] = true
            map[EntityType.MINECART] = true
            map[EntityType.MINECART_CHEST] = true
            map[EntityType.MINECART_FURNACE] = true
            map[EntityType.MINECART_COMMAND] = true
            map[EntityType.MINECART_TNT] = true
            map[EntityType.MINECART_HOPPER] = true
            map[EntityType.MINECART_MOB_SPAWNER] = true
            
            // disable, since armor stands used as decoration or custom vehicles
            map[EntityType.ARMOR_STAND] = false

            // disable, used as decoration
            map[EntityType.ITEM_FRAME] = false

            // maybe?
            // map[EntityType.ENDER_CRYSTAL] = true

            return map
        }
    }
}

