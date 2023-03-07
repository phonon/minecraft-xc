/**
 * Implement different projectile types.
 */

package phonon.xc.gun

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.max
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.sqrt
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import phonon.xc.XC
import phonon.xc.util.ChunkCoord
import phonon.xc.util.ChunkCoord3D
import phonon.xc.util.Hitbox
import phonon.xc.util.math.*
import phonon.xc.util.particle.*
import phonon.xc.util.blockCrackAnimation.BlockCrackAnimation
import phonon.xc.util.EnumArrayMap
import phonon.xc.util.BlockCollisionHandler


/**
 * Common class for all projectiles. System that updates
 * projectile will be responsible for updating the projectile
 * dynamics on each tick.
 */
public data class Projectile(
    // gun that fired this projectile
    val gun: Gun,
    // source entity for projectile
    val source: Entity,
    // bullet position in world
    var x: Float,
    var y: Float,
    var z: Float,
    // initial normalized direction (set bullet direction)
    var dirX: Float = 1f,
    var dirY: Float = 0f,
    var dirZ: Float = 0f,
    // speed in blocks/tick
    val speed: Float = 20.0f,
    // max lifetime in ticks
    val maxLifetime: Int = 200,
    // max 2D XZ plane distance bullet can travel
    val maxDistance: Float = 200f,
    // bullet gravity
    val gravity: Float = 0.0125f,
    // hit sphere bound radius for proximity based entity collision (e.g. flak guns)
    val proximity: Float = 0.0f,
    // passthrough door blocks (doors, trapdoors)
    // TODO: in future can make this an enum to customize block hit handling
    val passthroughDoors: Boolean = false,
    // which entity actually fired the projectile, for vehicles the 
    // shooter (Player) is not same as source (ArmorStand)
    val shooter: Entity = source,
) {
    // current lifetime in ticks
    var lifetime: Int = 0

    // current distance traveled
    var distance: Float = 0f

    // velocity
    var velX: Float
    var velY: Float
    var velZ: Float

    // next position at t+1
    var xNext: Float = 0f
    var yNext: Float = 0f
    var zNext: Float = 0f
    // distance to next position
    var distToNext: Float = 0f

    init {
        // makes sure dir is normalized
        val dirLength = length(dirX, dirY, dirZ)
        dirX = dirX / dirLength
        dirY = dirY / dirLength
        dirZ = dirZ / dirLength

        // initialize velocity from dir * speed
        velX = dirX * speed
        velY = dirY * speed
        velZ = dirZ * speed
    }
}

/**
 * Worker task to calculate projectile dynamics on a slice of projectiles
 * 
 * Use linearized bullet dynamics to calculate next position
 * at end of this tick. Dynamics for each tick step:
 * v1 = v0 + g * t
 * x1 = x0 + v0 * t + g * t^2/2
 * 
 * For each 2D chunk visited by projectile in this update step,
 * add to `visitedChunks`. This is later used to gather hitboxes in
 * all visited chunks.
 */
private class ProjectileDynamicsUpdateTask(
    val world: World,
    val projectiles: List<Projectile>,
    val visitedChunks: MutableSet<ChunkCoord>,
): Callable<Unit> {
    override fun call(): Unit {
        for ( projectile in projectiles ) {
            projectile.xNext = projectile.x + projectile.velX
            projectile.yNext = projectile.y + projectile.velY - projectile.gravity/2f
            projectile.zNext = projectile.z + projectile.velZ

            projectile.velY -= projectile.gravity

            // update normalized direction
            val dx = projectile.xNext - projectile.x
            val dy = projectile.yNext - projectile.y
            val dz = projectile.zNext - projectile.z
            val d = length(dx, dy, dz)
            projectile.distToNext = d
            projectile.dirX = dx / d
            projectile.dirY = dy / d
            projectile.dirZ = dz / d
            
            // Calculate 2D AABB of region traveled. Adds 8 block buffer
            // to account for hitboxes that multiple span chunks.
            // This assumes max size of a hitbox is 16x16x16. TODO: put in config somewhere
            val xmin = (floor(min(projectile.x, projectile.xNext) - 8f)).toInt()
            val zmin = (floor(min(projectile.z, projectile.zNext) - 8f)).toInt()
            val xmax = (ceil(max(projectile.x, projectile.xNext) + 8f)).toInt()
            val zmax = (ceil(max(projectile.z, projectile.zNext) + 8f)).toInt()

            // converts into mineman chunk coords (divides by 16)
            val cxmin = (xmin shr 4)
            val czmin = (zmin shr 4)
            val cxmax = (xmax shr 4)
            val czmax = (zmax shr 4)
            
            for ( cx in cxmin..cxmax ) {
                for ( cz in czmin..czmax ) {
                    // only add if chunk loaded
                    if ( world.isChunkLoaded(cx, cz) ) {
                        visitedChunks.add(ChunkCoord(cx, cz))
                    }
                }
            }
        }
    }
}

/**
 * Internal class indicating block was hit by projectile.
 * Written into XC global queue by projectile system.
 * Will be post-processed before emitting a public event.
 */
internal data class ProjectileHitBlock(
    val block: Block,
    val location: Location,
    val source: Entity,
    val gun: Gun,
)


/**
 * Internal class indicating entity was hit by projectile.
 * Written into XC global queue by projectile system.
 * Will be post-processed before emitting a public event.
 */
internal data class ProjectileHitEntity(
    val entity: Entity,
    val location: Location,
    val source: Entity,
    val gun: Gun,
    val distance: Double,
)


/**
 * Internal helper struct to contains result of projectile raytrace.
 * Fields non-null depending on results of hit test.
 * If no hit, both block == null and entity == null.
 * If a hit occurs, either block != null or entity != null.
 * Both should never be non-null, as only closer hit should ever
 * be returned.
 */
private data class ProjectileRaytraceResult(
    val outOfBounds: Boolean, // if out of map bounds or into unloaded chunk
    val distance: Float,      // how far raytrace traveled
    val location: Location?,  // non-null hit location if hit
    val block: Block?,        // non-null if hit
    val entity: Entity?,      // non-null entity if hit
)


/**
 * Results from each projectile system update.
 * Returns hit blocks and entities, and world hitboxes map.
 */
internal data class ProjectileSystemUpdate(
    val hitboxes: HashMap<ChunkCoord3D, ArrayList<Hitbox>>,
    val hitBlocks: ArrayList<ProjectileHitBlock>,
    val hitEntities: ArrayList<ProjectileHitEntity>,
)


/**
 * Projectile system for each world.
 */
public class ProjectileSystem(
    public val world: World,
    private val threadpool: ExecutorService,
    private val numThreads: Int,
) {
    // Main list of projectiles to be updated each tick
    private var projectiles: ArrayDeque<Projectile> = ArrayDeque(2000)

    // Double buffer to store valid projectiles after collision check,
    // then swapped back into main projectiles after update.
    private var projectileStagingBuffer: ArrayDeque<Projectile> = ArrayDeque(2000)

    // Projectile creation queue, must be concurrent to allow other threads
    // to async insert projectiles
    private var projectileCreateQueue: LinkedBlockingQueue<Projectile> = LinkedBlockingQueue()


    /**
     * Return current number of projectiles in system
     */
    public fun size(): Int {
        return projectiles.size
    }

    /**
     * Adds projectile to be ticked starting during next tick update.
     * This method is threadsafe, can be called from async threads.
     */
    public fun addProjectile(projectile: Projectile) {
        if ( Bukkit.getServer().isPrimaryThread() ) {
            this.projectiles.add(projectile)
        } else {
            this.projectileCreateQueue.add(projectile)
        }
    }

    /**
     * Adds the given projectiles to be ticked starting during the next tick.
     * This method is threadsafe, can be called from async threads.
     */
    public fun addProjectiles(projectiles: Collection<Projectile>) {
        if ( Bukkit.getServer().isPrimaryThread() ) {
            this.projectiles.addAll(projectiles)
        } else {
            this.projectileCreateQueue.addAll(projectiles)
        }
    }
    
    /**
     * Projectiles update on each tick.
     * 
     * Takes an input set of chunks `visitedChunks` to force system to gather entity
     * hitboxes in these chunks. This is used for throwables and other systems
     * that do their own collision tests, but also need to gather entity hitboxes.
     */
    internal fun update(
        xc: XC,
        initialVisitedChunks: Set<ChunkCoord>, // to force gathering entity hitboxes in these chunks
    ): ProjectileSystemUpdate {
        // Push all waiting async projectiles
        if ( !this.projectileCreateQueue.isEmpty()) {
            this.projectileCreateQueue.drainTo(this.projectiles)
        }

        // timings probe
        val tStart = xc.debugNanoTime()

        // number of projectiles
        val numProjectiles = this.projectiles.size

        // Convert initial visited chunks into concurrent hash set
        // heuristic: assume each projectile checks at most a 4x4 chunk grid
        // so size with 16 chunks / projectile
        val visitedChunks = ConcurrentHashMap.newKeySet<ChunkCoord>(initialVisitedChunks.size + 16 * numProjectiles)
        visitedChunks.addAll(initialVisitedChunks)

        // First, iterate all projectiles, determine start and end positions
        // during tick, then map all potential chunks => hitboxes that 
        // projectile could visit. This coalesces all chunk.getEntities()
        // calls and avoids duplicating scanning chunk entities multiple
        // times for multiple projectiles. `hitboxes` contains all entity
        // hitboxes that intersect with the 3d chunk.
        val hitboxes = HashMap<ChunkCoord3D, ArrayList<Hitbox>>() 

        // distribute projectile elements across available threads
        // TODO: in future do testing and better load balance this
        val numProjectilesPerThread = 1 + (numProjectiles / this.numThreads)
        val elementsPerThread = IntArray(2 * this.numThreads, {_ -> 0}) // packed (start, count) pairs

        var cursor = 0
        for ( i in 0 until this.numThreads ) {
            val start = cursor
            if ( start >= numProjectiles ) {
                break
            }
            val count = min(numProjectilesPerThread, numProjectiles - cursor)
            elementsPerThread[(2*i)] = start
            elementsPerThread[(2*i) + 1] = count
            cursor += count
        }
        
        // println("elementsPerThread: ${elementsPerThread.contentToString()}")
        // Use linearized bullet dynamics to calculate next position
        // at end of this tick. Dynamics for each tick step:
        // v1 = v0 + g * t
        // x1 = x0 + v0 * t + g * t^2/2
        // also adds all chunks visited by projectiles to `visitedChunks`
        if ( numProjectiles > 0 ) {
            val projectileDynamicsTasks: ArrayList<Future<Unit>> = ArrayList(this.numThreads)

            for ( i in 0 until this.numThreads ) {
                val start = elementsPerThread[(2*i)]
                val count = elementsPerThread[(2*i) + 1]
                if ( count == 0 ) {
                    break
                }

                projectileDynamicsTasks.add(threadpool.submit(ProjectileDynamicsUpdateTask(
                    this.world,
                    this.projectiles.subList(start, start+count),
                    visitedChunks,
                )))
            }
            
            try {
                projectileDynamicsTasks.forEach{ it.get(1L, TimeUnit.SECONDS) }
            } catch ( e: Exception ) {
                e.printStackTrace()
            }
        }

        // println("visitedChunks.size = ${visitedChunks.size}")

        // Get hitboxes for entities in visited chunks
        // Projectile chunk search should have already checked that
        // chunk is loaded.
        for ( coord in visitedChunks ) {
            val chunk = world.getChunkAt(coord.x, coord.z)

            // on newer versions, if entities not loaded, skip chunk
            // if ( !chunk.isEntitiesLoaded() ) {
            //     continue
            // }

            for ( entity in chunk.getEntities() ) {
                // special handling for custom model hitboxes
                if ( entity.type == EntityType.ARMOR_STAND ) {
                    val hitboxSize = xc.customModelHitboxes.get(entity.getUniqueId())
                    if ( hitboxSize != null ) {
                        addHitboxToAllIntersectingChunks(hitboxes, Hitbox.from(entity, hitboxSize))
                        continue
                    }
                }

                // regular entities
                if ( xc.config.entityTargetable[entity.type] ) {
                    addHitboxToAllIntersectingChunks(hitboxes, Hitbox.from(entity, xc.config.entityHitboxSizes[entity.type]))
                }
            }
        }

        // timings probe
        val tHitboxMapDone = xc.debugNanoTime()

        // Main projectile update: calculate hit blocks and entities
        val hitBlocksQueue = ArrayList<ProjectileHitBlock>()
        val hitEntitiesQueue = ArrayList<ProjectileHitEntity>()

        for ( i in 0 until this.projectiles.size ) {
            val projectile = this.projectiles.removeLast()

            // update projectile
            val raytraceResult = runProjectileRaytrace(xc, projectile, world, hitboxes)

            val outOfBounds = raytraceResult.outOfBounds
            val hitBlock = raytraceResult.block
            val hitEntity = raytraceResult.entity

            if ( hitBlock != null ) {
                val hitLoc = raytraceResult.location!!

                hitBlocksQueue.add(ProjectileHitBlock(
                    block = hitBlock,
                    location = hitLoc,
                    source = projectile.shooter,
                    gun = projectile.gun,
                ))

                // queue packets for block breaking animation
                // and particles on block hit
                if ( projectile.gun.projectileBlockHitParticles ) {
                    val hitBlockData = hitBlock.getBlockData().clone()
                    xc.particleBulletBlockImpactQueue.add(ParticleBulletBlockImpact(
                        world = world,
                        count = xc.config.particleBulletImpactCount,
                        x = hitLoc.x,
                        y = hitLoc.y,
                        z = hitLoc.z,
                        blockData = hitBlockData,
                        force = true,
                    ))

                    xc.blockCrackAnimationQueue.add(BlockCrackAnimation(world, hitBlock.x, hitBlock.y, hitBlock.z))
                }
            }
            
            // handle entity hit
            if ( hitEntity != null ) {
                val hitLoc = raytraceResult.location!!

                hitEntitiesQueue.add(ProjectileHitEntity(
                    entity = hitEntity,
                    location = raytraceResult.location!!,
                    source = projectile.shooter,
                    gun = projectile.gun,
                    distance = projectile.distance.toDouble() + raytraceResult.distance,
                ))

                // queue packets for hitbox impact particles
                val particleImpact = projectile.gun.projectileImpactParticles
                if ( particleImpact !== null ) {
                    xc.particleBulletHitboxImpactQueue.add(ParticleBulletHitboxImpact(
                        world = world,
                        particle = particleImpact.particle,
                        count = particleImpact.count,
                        x = hitLoc.x,
                        y = hitLoc.y,
                        z = hitLoc.z,
                        randomX = particleImpact.randomX,
                        randomY = particleImpact.randomY,
                        randomZ = particleImpact.randomZ,
                        force = particleImpact.force,
                    ))
                }
            }

            // bullet trails particle effects
            val gun = projectile.gun
            xc.particleBulletTrailQueue.add(ParticleBulletTrail(
                world = world,
                particle = gun.projectileParticleType,
                particleData = Particle.DustOptions(gun.projectileParticleColor, gun.projectileParticleSize),
                xStart = projectile.x.toDouble(),
                yStart = projectile.y.toDouble(),
                zStart = projectile.z.toDouble(),
                dirX = projectile.dirX.toDouble(),
                dirY = projectile.dirY.toDouble(),
                dirZ = projectile.dirZ.toDouble(),
                length = min(projectile.distToNext, raytraceResult.distance).toDouble(),
                netDistance = projectile.distance.toDouble(),
                minDistance = 4.0,
                spacing = gun.projectileParticleSpacing,
                force = gun.projectileParticleForceRender,
            ))

            // decide if projectile still valid after this update tick
            projectile.lifetime += 1
            projectile.distance += projectile.distToNext

            val alive = (
                (hitBlock == null) &&
                (hitEntity == null) &&
                !outOfBounds &&
                (projectile.lifetime < projectile.maxLifetime) &&
                (projectile.distance < projectile.maxDistance))

            // if alive, update projectile for next tick, and add to staging buffer
            if ( alive ) {
                projectile.x = projectile.xNext
                projectile.y = projectile.yNext
                projectile.z = projectile.zNext

                this.projectileStagingBuffer.addLast(projectile)
            }
        }

        // swap staging buffer into real projectiles buffer
        val temp = this.projectiles
        this.projectiles = this.projectileStagingBuffer
        this.projectileStagingBuffer = temp


        // timings probe
        val tProjectileUpdateDone = xc.debugNanoTime()

        if ( xc.doDebugTimings ) {
            val dtHitboxMap = tHitboxMapDone - tStart
            val dtProjectileUpdate = tProjectileUpdateDone - tHitboxMapDone
            xc.debugTimings.add("createEntityHitboxMap", dtHitboxMap)
            xc.debugTimings.add("projectileUpdate", dtProjectileUpdate)
        }

        return ProjectileSystemUpdate(
            hitboxes = hitboxes,
            hitBlocks = hitBlocksQueue,
            hitEntities = hitEntitiesQueue,
        )
    }
}

/**
 * This sweeps hitbox min/max 3d chunk coords then adds into
 * all intersecting chunks in hitboxes map.
 */
private fun addHitboxToAllIntersectingChunks(
    hitboxes: HashMap<ChunkCoord3D, ArrayList<Hitbox>>, 
    box: Hitbox,
) {
    // chunk coords of min/max
    val cxmin = floor(box.xmin).toInt() shr 4
    val cymin = floor(box.ymin).toInt() shr 4
    val czmin = floor(box.zmin).toInt() shr 4
    val cxmax = ceil(box.xmax).toInt() shr 4
    val cymax = ceil(box.ymax).toInt() shr 4
    val czmax = ceil(box.zmax).toInt() shr 4

    for ( cx in cxmin..cxmax ) {
        for ( cy in cymin..cymax ) {
            for ( cz in czmin..czmax ) {
                hitboxes.getOrPut(ChunkCoord3D(cx, cy, cz), { ArrayList() }).add(box)
            }
        }
    }
}

/**
 * Checks for collisions between projectiles and world blocks
 * and entities. Traces the trajectory of the projectile in world
 * and does hit tests on blocks and entities.
 */
private fun runProjectileRaytrace(
    xc: XC,
    projectile: Projectile,
    world: World,
    hitboxes: HashMap<ChunkCoord3D, ArrayList<Hitbox>>,
): ProjectileRaytraceResult {

    // ====================================================
    // 1. raytrace path until hit max distance or solid block
    // ====================================================
    // Raytracing does coarse voxel traversal using Amanatides & Woo algorithm:
    // http://www.cse.yorku.ca/~amana/research/grid.pdf
    // 
    // Broadphase ray traversal looks like (in 2D):
    //  loop{
    //    if ( tMaxX < tMaxY ) {             // where tMaxX and MaxY
    //       tMaxX = tMaxX + tDeltaX;        // are distances for
    //       X = X + stepX;                  // v0 + t*r = v1
    //    } else {                           // where v0 = current voxel pos
    //       tMaxY= tMaxY + tDeltaY;         // and v1 = next voxel start pos
    //       Y = Y + stepY;                  // stepX, stepY are 1 or -1
    //    }                                  // depending on ray direction
    //     NextVoxel(X,Y);
    //  }
    //
    // Fine voxel hit testing done if ( currBlock != AIR )
    // Each block must do a material-specific hit testing
    // which indicates a possible collision block was in path.
    // For regular, homogeneous cube materials (most blocks),
    // this can be a single check (true/false).
    //
    // For complicated materials like stairs or slabs, this will do
    // fine raymarch steps between v0 and v1 points and check if
    // block is solid at each point:
    //      x            |       testSolid(x0,y0,z0)
    //      |-->x        |       testSolid(x1,y1,z1)
    //      |------>x    |       testSolid(x2,y2,z2)
    //      |----------->x       testSolid(x3,y3,z3)
    
    // starting position of raytrace
    val x0 = projectile.x
    val y0 = projectile.y
    val z0 = projectile.z

    val dirX = projectile.dirX
    val dirY = projectile.dirY
    val dirZ = projectile.dirZ
    // pre-divided inverse ray direction, used frequently
    val invDirX = if ( dirX != 0f ) 1f / dirX else Float.MAX_VALUE
    val invDirY = if ( dirY != 0f ) 1f / dirY else Float.MAX_VALUE
    val invDirZ = if ( dirZ != 0f ) 1f / dirZ else Float.MAX_VALUE

    // direction the voxel ids are incremented, based on projectile direction
    val stepX = if (dirX >= 0) 1 else -1
    val stepY = if (dirY >= 0) 1 else -1
    val stepZ = if (dirZ >= 0) 1 else -1

    // initial block
    var blx = floor(x0).toInt()
    var bly = floor(y0).toInt()
    var blz = floor(z0).toInt()
    var bl = world.getBlockAt(blx, bly, blz)
    // val blocksVisited = ArrayList<Block>() // debugging (for glass block trails)

    // chunk
    var cx = blx shr 4
    var cy = bly shr 4
    var cz = blz shr 4

    // chunks visited by projectile, gathered during ray trace
    val chunksVisited = ArrayList<ChunkCoord3D>(4) // size 4 = up to 64 blocks distance
    chunksVisited.add(ChunkCoord3D(cx, cy, cz))

    // Distance to next projectile point if nothing hit
    // Margin added to ensure continuous test with next
    // projectile raytrace. If not continuous, bullets can pass
    // through empty diagonal regions. 
    val raytraceMaxDist = projectile.distToNext + 2f
    // max distance projectile can travel
    val maxDist = projectile.maxDistance + 1.0f // margin to ensure all blocks tested
    // total distance projectile has traveled
    var totalProjectileDist = projectile.distance
    // current chunk is loaded, early exit block raytrace if out of chunk bounds
    var inLoadedChunks = world.isChunkLoaded(cx, cz)
    // set if a block is hit
    var hitBlock: Block? = null
    var hitBlockDistance = Float.MAX_VALUE
    var hitBlockLocation: Location? = null
    
    // Accumulated axis distance t traveled along ray.
    // Initialize to first next block boundary. First block boundary
    // depends on direction of step since block coords are floor of
    // world position coords.
    val blNextBoundaryX = if ( stepX > 0 ) blx + stepX else blx
    val blNextBoundaryY = if ( stepY > 0 ) bly + stepY else bly
    val blNextBoundaryZ = if ( stepZ > 0 ) blz + stepZ else blz

    var tMaxX = (blNextBoundaryX.toFloat() - x0) * invDirX // = (boundX - x0) / dirX
    var tMaxY = (blNextBoundaryY.toFloat() - y0) * invDirY // = (boundY - y0) / dirY
    var tMaxZ = (blNextBoundaryZ.toFloat() - z0) * invDirZ // = (boundZ - z0) / dirZ

    // distance to next voxel along ray
    val tDeltaX = stepX.toFloat() * invDirX // = stepX / dirX
    val tDeltaY = stepY.toFloat() * invDirY // = stepY / dirY
    val tDeltaZ = stepZ.toFloat() * invDirZ // = stepZ / dirZ
    
    // total t traveled along direction of ray
    var tTraveled = 0.0f

    do {
        // old debugging:
        // println("tTraveled: ${tTraveled}")
        // println("block: x:${blx} y:${bly} z:${blz} (${bl.type})")
        // println("loc: x:${x0 + tTraveled * dirX} y:${y0 + tTraveled * dirY} z:${z0 + tTraveled * dirZ}")

        // step distance for current block
        var tTraveledNext: Float
        
        if ( tMaxX < tMaxY ) {
            if( tMaxX < tMaxZ ) {
                blx = blx + stepX
                tTraveledNext = tMaxX
                tMaxX = tMaxX + tDeltaX
            } else {
                blz = blz + stepZ
                tTraveledNext = tMaxZ
                tMaxZ = tMaxZ + tDeltaZ
            }
        } else {
            if( tMaxY < tMaxZ ) {
                bly = bly + stepY
                tTraveledNext = tMaxY
                tMaxY = tMaxY + tDeltaY
            } else {
                blz = blz + stepZ
                tTraveledNext = tMaxZ
                tMaxZ = tMaxZ + tDeltaZ
            }
        }

        // current distance traveled along ray
        val stepLength = tTraveledNext - tTraveled

        // if current block is non-air run fine trace to test hit
        if ( bl.type != Material.AIR ) {
            val xStart = x0 + (tTraveled * dirX)
            val yStart = y0 + (tTraveled * dirY)
            val zStart = z0 + (tTraveled * dirZ)

            // defer fine raytrace to block material specific handler
            val hitDistance = if ( projectile.passthroughDoors ) {
                xc.config.blockCollisionPassthroughDoors[bl.type](
                    bl,
                    xStart,
                    yStart,
                    zStart,
                    dirX,
                    dirY,
                    dirZ,
                    stepLength,
                )
            } else {
                xc.config.blockCollision[bl.type](
                    bl,
                    xStart,
                    yStart,
                    zStart,
                    dirX,
                    dirY,
                    dirZ,
                    stepLength,
                )
            }

            if ( hitDistance != Float.MAX_VALUE ) {
                hitBlock = bl
                hitBlockDistance = tTraveled
                hitBlockLocation = Location(
                    world,
                    (xStart + hitDistance * dirX).toDouble(),
                    (yStart + hitDistance * dirY).toDouble(),
                    (zStart + hitDistance * dirZ).toDouble(),
                )
                break
            }
        }

        // update block
        // blocksVisited.add(bl) // debugging, add old block
        bl = world.getBlockAt(blx, bly, blz)

        // update chunk
        val cxNext = blx shr 4
        val cyNext = bly shr 4
        val czNext = blz shr 4
        if ( cx != cxNext || cy != cyNext || cz != czNext ) {
            cx = cxNext
            cy = cyNext
            cz = czNext
            if ( world.isChunkLoaded(cx, cz) ) {
                chunksVisited.add(ChunkCoord3D(cx, cy, cz))
            } else {
                inLoadedChunks = false
            }
        }
        
        // update ray and projectile distance traveled in this step
        tTraveled = tTraveledNext
        totalProjectileDist += stepLength
    } while ( inLoadedChunks && tTraveled < raytraceMaxDist && totalProjectileDist < maxDist ) 

    // ====================================================
    // 2. raycast test on hitboxes in visited chunks
    // ====================================================
    // max distance to entity must be less than raycast distance
    val maxEntityDist = projectile.distToNext

    // entity hit
    var hitEntity: Entity? = null
    var hitEntityDistance = Float.MAX_VALUE

    // without pre-calculated hitmap
    // val xStart = projectile.x
    // val zStart = projectile.z
    // val xEnd = projectile.xNext
    // val zEnd = projectile.zNext
    
    // val cxmin = (min(xStart, xEnd) - 2.0).toInt() shr 4
    // val cxmax = (max(xStart, xEnd) + 2.0).toInt() shr 4
    // val czmin = (min(zStart, zEnd) - 2.0).toInt() shr 4
    // val czmax = (max(zStart, zEnd) + 2.0).toInt() shr 4

    // for ( cx in cxmin..cxmax ) {
    //     for ( cz in czmin..czmax ) {
    //         if ( !world.isChunkLoaded(cx, cz) ) continue

    //         val chunk = world.getChunkAt(cx, cz)

    //         for ( entity in chunk.getEntities() ) {
    //             // special handling for custom model hitboxes
    //             val hitbox = if ( entity.type == EntityType.ARMOR_STAND ) {
    //                 val hitboxSize = xc.customModelHitboxes.get(entity.getEntityId())
    //                 if ( hitboxSize != null ) {
    //                     Hitbox.from(entity, hitboxSize)
    //                 } else {
    //                     null
    //                 }
    //             } else if ( xc.config.entityTargetable[entity.type] ) {
    //                 Hitbox.from(entity, xc.config.entityHitboxSizes[entity.type])
    //             } else {
    //                 null
    //             }

    //             if ( hitbox != null ) {
    //                 val hitDistance = hitbox.intersectsRayLocation(
    //                     x0,
    //                     y0,
    //                     z0,
    //                     invDirX,
    //                     invDirY,
    //                     invDirZ,
    //                 )

    //                 // choose this new entity if hit and distance is closer than previous hit
    //                 if ( hitDistance != null && hitDistance < hitEntityDistance && hitDistance < maxEntityDist ) {
    //                     // make sure this is not entity shooter or its vehicle or shooter's passenger
    //                     if ( hitbox.entity !== projectile.source && hitbox.entity !== projectile.source.vehicle && !projectile.source.getPassengers().contains(hitbox.entity) ) {
    //                         hitEntity = hitbox.entity
    //                         hitEntityDistance = hitDistance
    //                     }
    //                 }
    //             }
    //         }
    //     }
    // }

    // using optimized hitbox list
    for ( coord in chunksVisited ) {
        val hitboxList = hitboxes[coord]
        if ( hitboxList != null ) {
            // normal projectile (non-proximity) handling:
            // raycast each hitbox in the chunk and return closest
            if ( projectile.proximity <= 0.0 ) {
                for ( hitbox in hitboxList ) {
                    val hitDistance = hitbox.intersectsRayLocation(
                        x0,
                        y0,
                        z0,
                        invDirX,
                        invDirY,
                        invDirZ,
                    )

                    // choose this new entity if hit and distance is closer than previous hit
                    if ( hitDistance != null && hitDistance < hitEntityDistance && hitDistance < maxEntityDist ) {
                        // make sure this is not entity shooter or its vehicle or shooter's passenger
                        if ( hitbox.entity.uniqueId != projectile.source.uniqueId && 
                            hitbox.entity.uniqueId != projectile.source.vehicle?.uniqueId && 
                            projectile.source.getPassengers().indexOfFirst{ it.uniqueId == hitbox.entity.uniqueId } == -1
                        ) {
                            // println("hitbox entity: ${hitbox.entity.uniqueId} source: ${projectile.source.uniqueId} shooter vehicle: ${projectile.source.vehicle?.uniqueId} shooter passenger: ${projectile.source.getPassengers().indexOfFirst{ it.uniqueId == hitbox.entity.uniqueId }}")
                            hitEntity = hitbox.entity
                            hitEntityDistance = hitDistance
                        }
                    }
                }

                if ( hitEntity != null ) {
                    break
                }
            }
            // proxmity projectile handling:
            // find first target whose hitbox distance to projectile line < projectile.proximity
            else {
                for ( hitbox in hitboxList ) {
                    // 1. finds shortest distance from hitbox center to ray line
                    // then subtract radiusMin to adjust by minimum bounding sphere radius of the hitbox
                    // this determines if hitbox is < proxmity at some point along its raycast path this tick.
                    // let projectile line be:
                    //     line = a + t * n 
                    //          = (x0, y0, z0) + t * (dirX, dirY, dirZ)
                    // let (p - a) be vector from hitbox center p to line point a
                    // https://en.wikipedia.org/wiki/Distance_from_a_point_to_a_line#Vector_formulation
                    val pxax = hitbox.xcenter - x0
                    val pyay = hitbox.ycenter - y0
                    val pzaz = hitbox.zcenter - z0
                    
                    // dot product (p - a) . n
                    val paDotN = pxax * dirX + pyay * dirY + pzaz * dirZ
                    
                    val dAlongLineX = paDotN * dirX
                    val dAlongLineY = paDotN * dirY
                    val dAlongLineZ = paDotN * dirZ

                    // shortest distance components from hitbox center normal to line
                    val dNormalLineX = pxax - dAlongLineX
                    val dNormalLineY = pyay - dAlongLineY
                    val dNormalLineZ = pzaz - dAlongLineZ
            
                    val distToProjectileLine = sqrt((dNormalLineX * dNormalLineX) + (dNormalLineY * dNormalLineY) + (dNormalLineZ * dNormalLineZ)) - hitbox.radiusMin

                    // if dist to line within projectile proximity, continue:
                    if ( distToProjectileLine < projectile.proximity ) {
                        // 2. hitDistance is how far along line to reach the point of shortest distance
                        val hitDistance = sqrt((dAlongLineX * dAlongLineX) + (dAlongLineY * dAlongLineY) + (dAlongLineZ * dAlongLineZ))
                        
                        // choose this new entity if hit and distance is closer than previous hit
                        if ( hitDistance < hitEntityDistance && hitDistance < maxEntityDist ) {
                            // make sure this is not entity shooter or its vehicle or shooter's passenger
                            if ( hitbox.entity !== projectile.source && hitbox.entity !== projectile.source.vehicle && !projectile.source.getPassengers().contains(hitbox.entity) ) {
                                hitEntity = hitbox.entity
                                hitEntityDistance = hitDistance
                            }
                        }
                    }
                }

                if ( hitEntity != null ) {
                    break
                }
            }
        }
    }

    // ====================================================
    // return shortest hit: if blockHitDistance < entityHitDistance
    // ====================================================
    
    // DEBUG GLASS BLOCK TRAILS (test voxel traversal)
    // Bukkit.getScheduler().runTaskLater(xc.plugin!!, object: Runnable {
    //     init { 
    //         for ( block in blocksVisited ) {
    //             if ( block.type == Material.AIR ) {
    //                 block.setType(Material.GLASS)
    //             }
    //         }
    //     }

    //     override fun run() {
    //         for ( block in blocksVisited ) {
    //             if ( block.type == Material.GLASS ) {
    //                 block.setType(Material.AIR)
    //             }
    //         }
    //     }
    // }, 200L)

    // println("hitEntity (${hitEntityDistance}): ${hitEntity}")
    // println("hitBlock (${hitBlockDistance}): ${hitBlock}")

    // nothing hit, return no hit result
    if ( hitEntity == null && hitBlock == null ) {
        return ProjectileRaytraceResult(
            outOfBounds = !inLoadedChunks,
            distance = tTraveled,
            location = null,
            block = null,
            entity = null,
        )
    }
    else if ( hitEntity != null && hitBlock == null ) {
        val hitEntityLocation = Location(
            world,
            (x0 + hitEntityDistance * dirX).toDouble(),
            (y0 + hitEntityDistance * dirY).toDouble(),
            (z0 + hitEntityDistance * dirZ).toDouble(),
        )

        return ProjectileRaytraceResult(
            outOfBounds = !inLoadedChunks,
            distance = hitEntityDistance,
            location = hitEntityLocation,
            block = null,
            entity = hitEntity,
        )
    }
    else if ( hitBlock != null && hitEntity == null ) {
        return ProjectileRaytraceResult(
            outOfBounds = !inLoadedChunks,
            distance = hitBlockDistance,
            location = hitBlockLocation,
            block = hitBlock,
            entity = null,
        )
    }
    else { // both non-null, return closer object
        if ( hitBlockDistance < hitEntityDistance ) {
            return ProjectileRaytraceResult(
                outOfBounds = !inLoadedChunks,
                distance = hitBlockDistance,
                location = hitBlockLocation,
                block = hitBlock,
                entity = null,
            )
        }
        else {
            val hitEntityLocation = Location(
                world,
                (x0 + hitEntityDistance * dirX).toDouble(),
                (y0 + hitEntityDistance * dirY).toDouble(),
                (z0 + hitEntityDistance * dirZ).toDouble(),
            )

            return ProjectileRaytraceResult(
                outOfBounds = !inLoadedChunks,
                distance = hitEntityDistance,
                location = hitEntityLocation,
                block = null,
                entity = hitEntity,
            )
        }
    }
}
