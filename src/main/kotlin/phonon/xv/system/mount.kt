/**
 * Contain systems for players to mounting and unmounting vehicle entities.
 */

package phonon.xv.system

import java.util.EnumSet
import kotlin.math.min
import kotlin.math.max
import kotlin.math.ceil
import kotlin.math.floor
import org.bukkit.World
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.Particle
import phonon.xv.XV
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.Vehicle
import phonon.xv.core.VehicleElement
import phonon.xv.core.EntityVehicleData
import phonon.xv.core.VehicleElementId
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.iter.*
import phonon.xv.component.SeatsComponent
import phonon.xv.component.SeatsRaycastComponent
import phonon.xv.component.TransformComponent
import phonon.xv.util.CustomArmorStand
import phonon.xv.util.entity.setVehicleUuid

public data class MountVehicleRequest(
    val player: Player,
    val vehicle: Vehicle? = null,
    val element: VehicleElement? = null,
    val componentType: VehicleComponentType = VehicleComponentType.MODEL,
    val doRaycast: Boolean = false,
)

public data class DismountVehicleRequest(
    val player: Player,
    val vehicle: Vehicle,
    val element: VehicleElement,
    val componentType: VehicleComponentType,
)

/**
 * System for mounting a vehicle entity.
 * Return empty queue for next tick.
 * TODO: this will be generalized to an "interact" event system.
 */
public fun XV.systemMountVehicle(
    storage: ComponentsStorage,
    requests: List<MountVehicleRequest>,
): ArrayList<MountVehicleRequest> {
    val xv = this // alias for extension function this

    val requestsNotHandled = ArrayList<MountVehicleRequest>(requests.size)

    for ( req in requests ) {
        try {
            val (
                player,
                vehicle,
                element,
                componentType,
                doRaycast,
            ) = req

            // if raycast request, skip
            if ( doRaycast ) {
                requestsNotHandled.add(req)
                continue
            }

            // regular mounting from entity interaction
            // if vehicle or element is null, skip
            if ( vehicle == null || element == null ) {
                continue
            }

            /**
             * What we want to do here:
             * -> Player clicked vehicle for some interaction.
             * Just handle mount for now...
             * 
             * 1. Find the vehicle element from clicked id
             * 2. Check if element has a seat component
             *    NOTE: just within element not entire vehicle
             * 3. Get the seat component from archetype
             * 4. Determine which seat should be mounted from
             *    the interacted component.
             * 5. Mount the player to that seat index.
             * 
             */
            
            // hard-coded layout, need to change when we implement an function
            // for element id => archetype

            val transformComponent = element.components.transform
            val seatsComponent = element.components.seats
            if ( transformComponent === null ) {
                // skip if no transform component
                xv.logger.warning("Mounting failed, no transform component for element ${element.id} (${element.prototype.name})")
                continue
            }
            if ( seatsComponent === null ) {
                // skip if no transform component
                xv.logger.warning("Mounting failed, no seats component for element ${element.id} (${element.prototype.name})")
                continue
            }

            // currently hard-coded mapping to valid mountable components
            val seatToMount = when ( componentType ) {
                VehicleComponentType.MODEL -> element.components.model?.seatToMount ?: -1
                VehicleComponentType.GUN_BARREL -> element.components.gunBarrel?.seatToMount ?: -1
                VehicleComponentType.GUN_TURRET -> element.components.gunTurret?.seatToMount ?: -1
                else -> -1
            }

            // check valid seat and player isnt remounting something they're already on
            if ( seatToMount >= 0 && seatsComponent.passengers[seatToMount] != player ) {
                val world = player.world
                val locSeat = seatsComponent.getSeatLocation(seatToMount, transformComponent)
                
                // limit distance players can mount
                val dist = locSeat.distance(player.location)
                if ( dist > 2.5 ) { // TODO: configurable parameter inside vehicle components
                    continue
                }

                val seatEntity = CustomArmorStand.create(world, locSeat)
                // val seatEntity = world.spawn(locSeat, ArmorStand::class.java)
                seatEntity.setGravity(false)
                seatEntity.setInvulnerable(true)
                seatEntity.setSmall(true)
                seatEntity.setMarker(true)
                seatEntity.setVisible(true)

                player.teleport(locSeat) // without teleporting first, client side interpolation sends player to wrong location
                seatEntity.addPassenger(player)

                // armorstand gun plugin vehicle armor
                if ( seatsComponent.armor[seatToMount] > 0.0 ) {
                    xc.addVehiclePassengerArmor(seatEntity.getUniqueId(), seatsComponent.armor[seatToMount])
                }

                // entity -> vehicle mapping
                seatEntity.setVehicleUuid(vehicle.uuid, element.uuid)
                entityVehicleData[seatEntity.uniqueId] = EntityVehicleData(
                    vehicle,
                    element,
                    VehicleComponentType.SEATS,
                )

                seatsComponent.armorstands[seatToMount] = seatEntity
                seatsComponent.passengers[seatToMount] = player
            } else {
                // not mounting directly, try raycast
                requestsNotHandled.add(req)
                continue
            }
        } catch ( e: Exception ) {
            xv.logger.severe("ERROR: exception in systemMountVehicle")
            e.printStackTrace()
        }
    }

    return requestsNotHandled
}

/**
 * System for unmounting a vehicle entity.
 * Return empty queue for next tick.
 */
public fun systemDismountVehicle(
    storage: ComponentsStorage,
    requests: List<DismountVehicleRequest>,
): ArrayList<DismountVehicleRequest> {
    for ( req in requests ) {
        // no-op right now
    }

    return ArrayList()
}


/**
 * System for mounting seats when player clicks, by doing custom
 * raycast with virtual seat locations.
 * TODO: this needs to be done PER WORLD... :^(
 * 
 * At high level, this is 4 step procedure:
 * 1. For each vehicle, determine chunk region for valid player mount
 *    interactions.
 * 2. For each player, determine which vehicle zones they are interacting
 *    inside. Find valid vehicle-player intersecting chunks.
 * 3. For valid vehicles, map seats to their vehicle-player intersecting
 *    chunks.
 * 3. For each valid player interaction, do raycast with all seats in
 *    player's intersecting chunks with vehicles.
 * 
 * Details on four phase procedure:
 * 
 * 1. For each vehicle that needs a seat raycast (in a loaded world chunk),
 * create a 3d chunk zone around it's world position. Only consider player
 * interactions within these zones. The assumption is a vehicle and its
 * seats should never be bigger than these padded chunk regions, so we only
 * need to consider player interactions inside these zones. This will fail
 * for very long vehicles...but we can set this distance in config, O(n^3).
 * 
 *     1 chunk dist in each dir
 *    <------->
 *      _____          _____ 
 *     |_|_|_|        |_|_|_|
 *     |_|v|_|        |_|v|_|   <--- vehicle with no player interaction
 *     |_|_|p|        |_|_|_|        within 3d chunk zone, so we can
 *          ^                        ignore raycasting seats here
 *          player
 * 
 * An alternative would be to do chunk zones around players instead of
 * vehicles. Reason for doing vehicles instead is:
 * - On large servers with many players, assume there are generally
 *   more players than vehicles, e.g. a train with 8 seats shares
 *   8 players for 1 vehicle element.
 * 
 * 2. For each player interaction, do chunk zone sweep around player
 * and determine intersecting chunks with previous vehicle chunks:
 * 
 *     1 chunk dist in each dir
 *       <----->
 *                      
 *     |v|v|v|_       |v|v|v|
 *     |v|v|v|_|      |v|v|v|   <--- no intersecting player interaction
 *     |v|v|p|_|      |v|v|v|        
 *       |_|_|_|                     
 *          ^
 *         player chunk zone intersection with vehicle chunk zone
 * 
 * If player is within a vehicle chunk zone, then these chunk zones
 * need to do seat raycasting.
 * Then do a chunk zone sweep around player, for each INTERSECTING CHUNK,
 * add to a new map indicating valid chunks for seat raycasting.
 * 
 * 3. For each valid vehicle element seats, calculate its world position,
 * if position is within the vehicle-player intersecting chunks, add
 * a seat hitbox to that chunk's list of seats.
 * 
 * 4. Do player-seat raycasting:
 *  4.1. Do player chunk raytrace to determine which chunks player is
 *  looking at.
 *  4.2. For each of these chunks that player is looking at, raycast
 *  to each seat hitbox. Return the closest seat hit. Mount player into
 *  that seat. 
 * 
 */
@Suppress("NAME_SHADOWING")
public fun XV.systemMountSeatRaycast(
    storage: ComponentsStorage,
    requests: List<MountVehicleRequest>,
): ArrayList<MountVehicleRequest> {
    val xv = this // alias for extension function this

    // 1. Determine vehicle seat raycast chunk zones. Create map
    // for each chunk => vehicle elements in region.
    // Search in a padded chunk range around vehicle on each axis:
    //
    //                 chunkRange 
    //                 <------->
    //     |   |   | v |   |   |
    //     <-------------------> 
    //        totalChunkRange = 1 + 2*chunkRange
    //
    // total chunk range on each axis = 1+(2*chunkRange)
    // chunks checked per player (on 3 axes) = totalChunkRange^3
    // worst case if no players overlap,
    // max chunks checked <= requests * totalChunkRange^3
    val chunkRange = xv.config.seatRaycastChunkRange
    val totalChunkRange = 1 + (2 * chunkRange)
    val matchingArchetypes = xv.storage.getMatchingArchetypes(
            EnumSet.of(VehicleComponentType.TRANSFORM,
                    VehicleComponentType.SEATS,
                    VehicleComponentType.SEATS_RAYCAST)
    )
    var archetypeSize = 0
    for ( archetype in matchingArchetypes )
        archetypeSize += archetype.size
    if ( archetypeSize == 0 )
        return ArrayList()
    val maxChunks = archetypeSize * (totalChunkRange * totalChunkRange * totalChunkRange)
    val chunkVehicleElements = HashMap<ChunkCoord3D, ArrayList<VehicleElementId>>(maxChunks)
    var maxValidVehicleElements = 0

    for ( (el, transform, _, _) in ComponentTuple3.query<
        TransformComponent,
        SeatsComponent,
        SeatsRaycastComponent,
    >(storage) ) {
        val world = transform.world
        if ( world === null ) {
            continue
        }

        val vx = floor(transform.x).toInt() shr 4
        val vy = floor(transform.y).toInt() shr 4
        val vz = floor(transform.z).toInt() shr 4

        // skip if vehicle's chunk not loaded
        if ( !world.isChunkLoaded(vx, vz) ) {
            continue
        }

        val cxmin = vx - chunkRange
        val cymin = vy - chunkRange
        val czmin = vz - chunkRange
        val cxmax = vx + chunkRange
        val cymax = vy + chunkRange
        val czmax = vz + chunkRange

        for ( cx in cxmin..cxmax ) {
            for ( cy in cymin..cymax ) {
                for ( cz in czmin..czmax ) {
                    chunkVehicleElements.getOrPut(ChunkCoord3D(cx, cy, cz), { ArrayList() }).add(el)
                }
            }
        }

        maxValidVehicleElements += 1
    }

    // 2. Do same chunk zone check for players, find intersecting chunks 
    // with vehicles. Mark vehicles in intersecting chunks as valid for
    // next phase seat raycast test.
    val validVehicleElements = HashSet<VehicleElementId>(maxValidVehicleElements)
    val hitboxes = HashMap<ChunkCoord3D, ArrayList<SeatHitbox>>(maxChunks)
    var validPlayerInteractions = ArrayList<MountVehicleRequest>(requests.size) // filter valid player interactions

    for ( req in requests ) {
        val playerLoc = req.player.location
        val px = floor(playerLoc.x).toInt() shr 4
        val py = floor(playerLoc.y).toInt() shr 4
        val pz = floor(playerLoc.z).toInt() shr 4
        if ( !chunkVehicleElements.containsKey(ChunkCoord3D(px, py, pz)) ) {
            continue
        }

        // player does intersect with a vehicle's chunk zone,
        // find intersecting chunks
        val cxmin = px - chunkRange
        val cymin = py - chunkRange
        val czmin = pz - chunkRange
        val cxmax = px + chunkRange
        val cymax = py + chunkRange
        val czmax = pz + chunkRange

        for ( cx in cxmin..cxmax ) {
            for ( cy in cymin..cymax ) {
                for ( cz in czmin..czmax ) {
                    val c = ChunkCoord3D(cx, cy, cz)
                    chunkVehicleElements.get(c)?.let { elems ->
                        for ( el in elems ) {
                            validVehicleElements.add(el)
                        }
                        hitboxes.getOrPut(c, { ArrayList() })
                    }
                }
            }
        }

        validPlayerInteractions.add(req)
    }

    // 3. For each valid vehicle element, assign its seat hitboxes to their
    // vehicle-player intersection chunks.
    // For each hitbox, we check all 8 corners of its 3D AABB to find chunks
    // that it spans.
    // 
    // TODO: add a queryElements(storage, validElementsIterator) to only emit
    // the elements that we want to query, instead of iterating over all again
    for ( (el, transform, seats, seatsRaycast) in ComponentTuple3.query<
        TransformComponent,
        SeatsComponent,
        SeatsRaycastComponent,
    >(storage) ) {
        if ( !validVehicleElements.contains(el) ) {
            continue
        }

        for ( i in 0 until seats.count ) {
            // only need to consider unoccupied seats
            if ( seats.passengers[i] !== null ) {
                continue
            }

            // seat local offset
            val offsetX = seats.offsets[i*3]
            val offsetY = seats.offsets[i*3 + 1]
            val offsetZ = seats.offsets[i*3 + 2]
            // transform to global position
            val sx = transform.x + transform.yawCos * offsetX - transform.yawSin * offsetZ
            val sy = transform.y + offsetY
            val sz = transform.z + transform.yawSin * offsetX + transform.yawCos * offsetZ
            
            // aabb hitbox
            val sxMin = sx - seatsRaycast.hitboxHalfWidth
            val syMin = sy + seatsRaycast.hitboxYMin
            val szMin = sz - seatsRaycast.hitboxHalfWidth
            val sxMax = sx + seatsRaycast.hitboxHalfWidth
            val syMax = sy + seatsRaycast.hitboxYMax
            val szMax = sz + seatsRaycast.hitboxHalfWidth

            val box = SeatHitbox(
                element = el,
                seats = seats,
                seatIndex = i,
                x = sx,
                y = sy,
                z = sz,
                xmin = sxMin,
                ymin = syMin,
                zmin = szMin,
                xmax = sxMax,
                ymax = syMax,
                zmax = szMax,
                seatYaw = transform.yawf,
            )

            // chunk coord bounds of seats hitbox
            val cxmin = floor(sxMin).toInt() shr 4
            val cymin = floor(syMin).toInt() shr 4
            val czmin = floor(szMin).toInt() shr 4
            val cxmax = ceil(sxMax).toInt() shr 4
            val cymax = ceil(syMax).toInt() shr 4
            val czmax = ceil(szMax).toInt() shr 4

            for ( cx in cxmin..cxmax ) {
                for ( cy in cymin..cymax ) {
                    for ( cz in czmin..czmax ) {
                        hitboxes.get(ChunkCoord3D(cx, cy, cz))?.add(box)
                    }
                }
            }
        }
    }

    // 4. do player seat mount raycast seat AABB hitbox intersection tests
    val maxRaycastDist = xv.config.seatRaycastDistance
    val maxRaycastDistInChunks = maxRaycastDist / 16.0

    // player raycast distance should only be at max 1-2 blocks far.
    // if we are looking along an axis, at most we should only
    // need to check 1-2 chunks. if we are diagonal, then we can
    // intersect more chunks by raycast near a corner. but in no
    // situation should we be able to intersect more than a 8 chunk
    // region at a corner...so we can hardcode max of 8 chunks to
    // visit. Re-use array for all players to avoid re-allocs
    val maxChunksToRaycast = 8
    val chunksVisited = Array<ChunkCoord3D>(maxChunksToRaycast, {_ -> ChunkCoord3D(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)})
    
    for ( req in validPlayerInteractions ) {
        val player = req.player
        val world = player.world
        val playerLoc = player.location
        val x0 = playerLoc.x
        val y0 = playerLoc.y + player.eyeHeight
        val z0 = playerLoc.z
        var cx = floor(x0).toInt() shr 4
        var cy = floor(y0).toInt() shr 4
        var cz = floor(z0).toInt() shr 4
        var chunksCount = 0

        val rotX = playerLoc.getYaw().toDouble()
        val rotY = playerLoc.getPitch().toDouble()
        val xz = Math.cos(Math.toRadians(rotY))
        val dirX = -xz * Math.sin(Math.toRadians(rotX))
        val dirY = -Math.sin(Math.toRadians(rotY))
        val dirZ = xz * Math.cos(Math.toRadians(rotX))

        // find chunks intersecting with player raycast using voxel raytracing
        // Amanatides & Woo algorithm:
        // http://www.cse.yorku.ca/~amana/research/grid.pdf
        val stepX = if ( dirX >= 0 ) 1 else -1
        val stepY = if ( dirY >= 0 ) 1 else -1
        val stepZ = if ( dirZ >= 0 ) 1 else -1

        // Accumulated axis distance t traveled along ray.
        // Initialize to first next block boundary. First block boundary
        // depends on direction of step since block coords are floor of
        // world position coords.
        val nextBoundaryX = if ( stepX > 0 ) cx + stepX else cx
        val nextBoundaryY = if ( stepY > 0 ) cy + stepY else cy
        val nextBoundaryZ = if ( stepZ > 0 ) cz + stepZ else cz

        // pre-divided inverse ray direction
        val invDirX = if ( dirX != 0.0 ) 1.0 / dirX else Double.MAX_VALUE
        val invDirY = if ( dirY != 0.0 ) 1.0 / dirY else Double.MAX_VALUE
        val invDirZ = if ( dirZ != 0.0 ) 1.0 / dirZ else Double.MAX_VALUE

        var tMaxX = if ( dirX != 0.0 ) (nextBoundaryX.toDouble() - (x0 / 16.0)) * invDirX else Double.MAX_VALUE
        var tMaxY = if ( dirY != 0.0 ) (nextBoundaryY.toDouble() - (y0 / 16.0)) * invDirY else Double.MAX_VALUE
        var tMaxZ = if ( dirZ != 0.0 ) (nextBoundaryZ.toDouble() - (z0 / 16.0)) * invDirZ else Double.MAX_VALUE

        // distance to next voxel along ray
        val tDeltaX = if ( dirX != 0.0 ) stepX.toDouble() * invDirX else Double.MAX_VALUE
        val tDeltaY = if ( dirY != 0.0 ) stepY.toDouble() * invDirY else Double.MAX_VALUE
        val tDeltaZ = if ( dirZ != 0.0 ) stepZ.toDouble() * invDirZ else Double.MAX_VALUE
         
        // total t traveled along direction of ray
        var tTraveled: Double = 0.0

        while ( tTraveled < maxRaycastDistInChunks && chunksCount < maxChunksToRaycast ) {
            chunksVisited[chunksCount] = ChunkCoord3D(cx, cy, cz)
            chunksCount += 1

            // update ray distance traveled tTraveled in this step
            if ( tMaxX < tMaxY ) {
                if( tMaxX < tMaxZ ) {
                    cx = cx + stepX
                    tTraveled = tMaxX
                    tMaxX = tMaxX + tDeltaX
                } else {
                    cz = cz + stepZ
                    tTraveled = tMaxZ
                    tMaxZ = tMaxZ + tDeltaZ
                }
            } else {
                if( tMaxY < tMaxZ ) {
                    cy = cy + stepY
                    tTraveled = tMaxY
                    tMaxY = tMaxY + tDeltaY
                } else {
                    cz = cz + stepZ
                    tTraveled = tMaxZ
                    tMaxZ = tMaxZ + tDeltaZ
                }
            }
        }

        // raycast check all seat AABB hitboxes in chunks visited
        var hitElementId: VehicleElementId? = null
        var hitSeats: SeatsComponent? = null
        var hitSeatIndex: Int = -1
        var hitSeatDistance: Double = Double.MAX_VALUE
        var hitSeatX: Double = 0.0
        var hitSeatY: Double = 0.0
        var hitSeatZ: Double = 0.0
        var hitSeatYaw: Float = 0f

        for ( i in 0 until chunksCount ) {
            val c = chunksVisited[i]
            val hitboxList = hitboxes[c]
            if ( hitboxList != null ) {
                for ( hitbox in hitboxList ) {
                    // check if still unoccupied (since a previous player raycast may have
                    // already taken this seat)
                    if ( hitbox.seats.passengers[hitbox.seatIndex] !== null ) {
                        continue
                    }

                    // if debug, do particle visualization
                    if ( xv.config.seatRaycastDebug ) {
                        hitbox.visualize(world, Particle.VILLAGER_HAPPY)
                    }

                    val hitDistance = hitbox.intersectsRayLocation(
                        x0,
                        y0,
                        z0,
                        invDirX,
                        invDirY,
                        invDirZ,
                    )

                    // debug
                    // println("seat=${hitbox}, hitDistance=$hitDistance")

                    // choose this new entity if hit and distance is closer than previous hit
                    if ( hitDistance >= 0.0 && hitDistance < hitSeatDistance && hitDistance < maxRaycastDist ) {
                        hitElementId = hitbox.element
                        hitSeats = hitbox.seats
                        hitSeatIndex = hitbox.seatIndex
                        hitSeatDistance = hitDistance
                        hitSeatX = hitbox.x
                        hitSeatY = hitbox.y
                        hitSeatZ = hitbox.z
                        hitSeatYaw = hitbox.seatYaw
                    }
                }
            }
        }

        // mount player into seat if hit found
        if ( hitElementId !== null && hitSeats !== null ) {
            // get owning element and vehicle
            val hitElement = xv.storage.getElement(hitElementId)
            if ( hitElement !== null ) {
                val hitVehicle = xv.vehicleStorage.getOwningVehicle(hitElement)
                if ( hitVehicle !== null ) {
                    val locSeat = Location(world, hitSeatX, hitSeatY, hitSeatZ, hitSeatYaw, 0f)
                    val seatEntity = CustomArmorStand.create(world, locSeat)
                    // val seatEntity = world.spawn(locSeat, ArmorStand::class.java)
                    seatEntity.setGravity(false)
                    seatEntity.setInvulnerable(true)
                    seatEntity.setSmall(true)
                    seatEntity.setMarker(true)
                    seatEntity.setVisible(true)

                    // entity -> vehicle mapping
                    seatEntity.setVehicleUuid(hitVehicle.uuid, hitElement.uuid)
                    entityVehicleData[seatEntity.uniqueId] = EntityVehicleData(
                        hitVehicle,
                        hitElement,
                        VehicleComponentType.SEATS,
                    )

                    player.teleport(locSeat) // without teleporting first, client side interpolation sends player to wrong location
                    seatEntity.addPassenger(player)

                    hitSeats.armorstands[hitSeatIndex] = seatEntity
                    hitSeats.passengers[hitSeatIndex] = player
                }
            }
        }

    }

    return ArrayList()
}


/**
 * Mineman 3D chunk coordinate.
 */
data class ChunkCoord3D(
    val x: Int,
    val y: Int,
    val z: Int,
)

/**
 * AABB hitbox for a seat, coordinates in world space
 * Copy-pasted from XC hitbox.
 */
data class SeatHitbox(
    val element: VehicleElementId,
    val seats: SeatsComponent,
    val seatIndex: Int,
    val x: Double,
    val y: Double,
    val z: Double,
    val xmin: Double,
    val ymin: Double,
    val zmin: Double,
    val xmax: Double,
    val ymax: Double,
    val zmax: Double,
    val seatYaw: Float, // == yaw of vehicle transform
) {
    /**
     * Return ray-AABB intersection using slab method, based on:
     * https://tavianator.com/cgit/dimension.git/tree/libdimension/bvh/bvh.c#n196
     * 
     * Unlike `intersectsRay`, this does same thing but returns the
     * intersection distance in ray space if it intersects.
     * If intersects, return value is >0.0.
     * Otherwise, returns -1.
     */
    public fun intersectsRayLocation(
        // ray origin
        rx: Double,
        ry: Double,
        rz: Double,
        // ray inverse normalized direction (1/dir.x, 1/dir.y, 1/dir.z)
        rInvDirX: Double,
        rInvDirY: Double,
        rInvDirZ: Double,
    ): Double {
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

        tmin = max(0.0, tmin) // normalize to only forward direction

        return if ( tmax >= tmin ) tmin else -1.0
    }

    /**
     * Create particles filling volume of the hitbox
     */
    public fun visualize(world: World, particle: Particle) {
        val AMOUNT = 3.0
        val MIN_STEP = 0.1
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
}

