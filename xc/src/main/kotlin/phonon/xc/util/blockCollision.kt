/**
 * Handler for block collision tests.
 * Contain custom hit test for different materials and block data.
 * Required for complex blocks with holes like stairs, which cannot
 * be easily raycast. Instead, guns rely on stepped raymarching.
 */
package phonon.xc.util

import kotlin.math.min
import kotlin.math.max
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Bisected.Half
import org.bukkit.block.data.type.Bed
import org.bukkit.block.data.type.DaylightDetector
import org.bukkit.block.data.type.Door
import org.bukkit.block.data.type.Fence
import org.bukkit.block.data.type.GlassPane
import org.bukkit.block.data.type.Leaves
import org.bukkit.block.data.type.Sign
import org.bukkit.block.data.type.Slab
import org.bukkit.block.data.type.Stairs
import org.bukkit.block.data.type.TrapDoor
import org.bukkit.block.data.type.Wall
import phonon.xc.util.EnumArrayMap


/**
 * Handler function for all block collision tests
 * (
 *  block,
 *  xStart,
 *  yStart,
 *  zStart,
 *  dirX,
 *  dirY,
 *  dirZ,
 *  distance,
 * ) -> Float
 * 
 * Return is a float with special cases:
 * - MAX_VALUE: block is air
 * - 0: block is solid, instantly collides with block
 * - [0, MAX_VALUE): colliding at some point inside block
 * 
 * Exact value between [0, MAX_VALUE) is purely for aesthetics.
 * This determines where inside hit effect particles will spawn,
 * but has no effect on collision detection. Therefore, its 
 * okay to just approximate this value without finding actual
 * raycast hit location.
 */
typealias BlockCollisionHandler = (Block, Float, Float, Float, Float, Float, Float, Float) -> Float

// default handlers
@Suppress("UNUSED_PARAMETER")
public val defaultNoCollisionHandler: BlockCollisionHandler = { _, _, _, _, _, _, _, _ -> Float.MAX_VALUE }

@Suppress("UNUSED_PARAMETER")
public val defaultSolidBlockHandler: BlockCollisionHandler = { _, _, _, _, _, _, _, _ -> 0f }

/**
 * Return default hit test handlers for all material types.
 * Should be built in config, to allow user to manually override
 * specific material handlers to true/false.
 */
public fun blockCollisionHandlers(): EnumArrayMap<Material, BlockCollisionHandler> =
    EnumArrayMap.from<Material, BlockCollisionHandler>({ m -> materialBlockCollisionHandler(m) })

/**
 * Material specific block collision handler
 */
internal fun materialBlockCollisionHandler(m: Material): BlockCollisionHandler {
    if ( !m.isBlock() ) { // items, non-blocks, etc.
        return defaultNoCollisionHandler
    }

    val bData = try {
        m.createBlockData()
    }
    catch (e: Exception) {
        // probably not a block, this should never run
        if ( m.isOccluding() ) {
            return defaultSolidBlockHandler
        }
        else {
            return defaultNoCollisionHandler
        }
    }

    if ( m == Material.SNOW ) {
        return defaultNoCollisionHandler
    }

    else if ( m == Material.SCAFFOLDING ) {
        return defaultNoCollisionHandler
    }

    else if( bData is Sign ) {
        return defaultNoCollisionHandler
    }

    else if ( m == Material.WATER ) {
        return defaultNoCollisionHandler
    }

    else if ( bData is Leaves ) {
        return defaultNoCollisionHandler
    }
    
    else if ( m == Material.ANVIL || m == Material.CHIPPED_ANVIL || m == Material.DAMAGED_ANVIL ) {
        return defaultSolidBlockHandler
    }

    else if ( m.name.contains("GLASS") ) {
        return defaultNoCollisionHandler
    }

    else if ( bData is GlassPane ) {
        return defaultNoCollisionHandler
    }
      
    else if ( m.name.endsWith("CARPET") ) {
        return defaultNoCollisionHandler
    }

    else if ( bData is Slab ) {
        return fun(
            b: Block, // block
            _: Float, // x
            y0: Float, // y
            _: Float, // z
            _: Float, // dirX
            dirY: Float, // dirY
            _: Float, // dirZ
            d: Float  // distance
        ): Float { 
            try {
                val slab = b.getBlockData() as Slab
                val slabType = slab.getType()

                if ( slabType == Slab.Type.DOUBLE ) {
                    return 0f
                }

                // checks y-value at endpoints
                val blockY = b.getY().toFloat()
                val y1 = y0 + d * dirY

                if ( slabType == Slab.Type.BOTTOM ) {
                    if ( y0 - blockY < 0.5f) {
                        return 0f
                    } else if ( y1 - blockY < 0.5f ) {
                        return 0.5f * d // approximation
                    }
                }
                else if ( slabType == Slab.Type.TOP ) {
                    if ( y0 - blockY > 0.5f) {
                        return 0f
                    } else if ( y1 - blockY > 0.5f ) {
                        return 0.5f * d // approximation
                    }
                }
                return Float.MAX_VALUE
            }
            catch ( err: Exception ) {
                System.err.println("BlockCollisionHandler: Slab failed")
                return 0f
            }
        }
    }

    else if ( bData is Bed || bData is DaylightDetector ) {
        return fun(
            b: Block, // block
            _: Float, // x
            y0: Float, // y
            _: Float, // z
            _: Float, // dirX
            dirY: Float, // dirY
            _: Float, // dirZ
            d: Float  // distance
        ): Float {
			val blockY = b.getY().toFloat()
            val y1 = y0 + d * dirY
            if ( y0 - blockY < 0.5f) {
                return 0f
            } else if ( y1 - blockY < 0.5f ) {
                return 0.5f * d // approximation
            }
            return Float.MAX_VALUE
        }
    }

    else if ( bData is Door ) {
        return fun(
            b: Block, // block
            x0in: Float, // x
            _: Float, // y
            z0in: Float, // z
            dirX: Float, // dirX
            _: Float, // dirY
            dirZ: Float, // dirZ
            d: Float  // max distance in step
        ): Float {
            try {
                val door = b.getBlockData() as Door
                val isOpen = door.isOpen()
                val hinge = door.getHinge()
                val facing: BlockFace = door.getFacing()
                val bx = b.getX().toFloat()
                val bz = b.getZ().toFloat()

                // test (x, z) endpoints (x0, z0) and (x1, z1)
                val x0 = x0in - bx
                val z0 = z0in - bz
                val x1 = x0 + d * dirX
                val z1 = z0 + d * dirZ

                if ( isOpen == false ) {
                    when ( facing ) {
                        BlockFace.NORTH -> if ( z0 > 0.75f ) {
                            return 0f
                        } else if ( z1 > 0.75f ) {
                            return 0.75f * d // approximation
                        }
                        BlockFace.SOUTH -> if ( z0 < 0.25f ) {
                            return 0f
                        } else if ( z1 < 0.25f ) {
                            return 0.75f * d // approximation
                        }
                        BlockFace.WEST -> if ( x0 > 0.75f ) {
                            return 0f
                        } else if ( x1 > 0.75f ) {
                            return 0.75f * d // approximation
                        }
                        BlockFace.EAST -> if ( x0 < 0.25f ) {
                            return 0f
                        } else if ( x1 < 0.25f ) {
                            return 0.75f * d // approximation
                        }

                        else -> return Float.MAX_VALUE
                    }
                }
                else {
                    if ( hinge == Door.Hinge.RIGHT ) {
                        when ( facing ) {
                            // open north == west
                            BlockFace.NORTH -> if ( x0 > 0.75f ) {
                                return 0f
                            } else if ( x1 > 0.75f ) {
                                return 0.75f * d // approximation
                            }
                            // open south == east
                            BlockFace.SOUTH -> if ( x0 < 0.25f ) {
                                return 0f
                            } else if ( x1 < 0.25f ) {
                                return 0.75f * d // approximation
                            }
                            // open west == south
                            BlockFace.WEST -> if ( z0 < 0.25f ) {
                                return 0f
                            } else if ( z1 < 0.25f ) {
                                return 0.75f * d // approximation
                            }
                            // open east == north
                            BlockFace.EAST -> if ( z0 > 0.75f ) {
                                return 0f
                            } else if ( z1 > 0.75f ) {
                                return 0.75f * d // approximation
                            }

                            else -> return Float.MAX_VALUE
                        }
                    }
                    else { // hinge = Door.Hinge.LEFT
                        when ( facing ) {
                            // open north == east
                            BlockFace.NORTH -> if ( x0 < 0.25f ) {
                                return 0f
                            } else if ( x1 < 0.25f ) {
                                return 0.75f * d // approximation
                            }
                            // open south == west
                            BlockFace.SOUTH -> if ( x0 > 0.75f ) {
                                return 0f
                            } else if ( x1 > 0.75f ) {
                                return 0.75f * d // approximation
                            }
                            // open west == north
                            BlockFace.WEST -> if ( z0 > 0.75f ) {
                                return 0f
                            } else if ( z1 > 0.75f ) {
                                return 0.75f * d // approximation
                            }
                            // open east == south
                            BlockFace.EAST -> if ( z0 < 0.25f ) {
                                return 0f
                            } else if ( z1 < 0.25f ) {
                                return 0.75f * d // approximation
                            }

                            else -> return Float.MAX_VALUE
                        }
                    }
                }

                return Float.MAX_VALUE
            }
            catch ( err: Exception ) {
                System.err.println("BlockCollisionHandler: Door failed")
                return 0f
            }
        }
    }

    else if ( bData is TrapDoor ) {
        return fun(
            b: Block, // block
            x0in: Float, // x
            y0in: Float, // y
            z0in: Float, // z
            dirX: Float, // dirX
            dirY: Float, // dirY
            dirZ: Float, // dirZ
            d: Float  // max distance
        ): Float {            
            try {
                val trapdoor = b.getBlockData() as TrapDoor
                if ( trapdoor.isOpen() ) {
                    val bx = b.getX().toFloat()
                    val bz = b.getZ().toFloat()
                    val facing = trapdoor.getFacing()
                    
                    // end points
                    val x0 = x0in - bx
                    val z0 = z0in - bz
                    val x1 = x0 + d * dirX
                    val z1 = z0 + d * dirZ

                    if ( facing == BlockFace.EAST ) {
                        if ( x0 < 0.25f ) {
                            return 0f
                        } else if ( x1 < 0.25f ) {
                            return 0.75f * d
                        }
                    }
                    else if ( facing == BlockFace.WEST ) {
                        if ( x0 > 0.75f ) {
                            return 0f
                        } else if ( x1 > 0.75f ) {
                            return 0.75f * d
                        }
                    }
                    else if ( facing == BlockFace.SOUTH ) {
                        if ( z0 < 0.25f ) {
                            return 0f
                        } else if ( z1 < 0.25f ) {
                            return 0.75f * d
                        }
                    }
                    else if ( facing == BlockFace.NORTH ) {
                        if ( z0 > 0.75f ) {
                            return 0f
                        } else if ( z1 > 0.75f ) {
                            return 0.75f * d
                        }
                    }
                }
                // if closed, return slab-like (only need check y)
                else  {
                    val half = trapdoor.getHalf()
                    val y0 = y0in - b.getY().toFloat()
                    val y1 = y0 + d * dirY

                    if ( half == Half.BOTTOM ) {
                        if ( y0 < 0.25f ) {
                            return 0f
                        } else if ( y1 < 0.25f ) {
                            return 0.75f * d
                        }
                    } else { // half == Bisected.Half.TOP
                        if ( y0 > 0.75f ) {
                            return 0f
                        } else if ( y1 > 0.75f ) {
                            return 0.75f * d
                        }
                    }
                }
                
                return Float.MAX_VALUE
            }
            catch ( err: Exception ) {
                System.err.println("BlockCollisionHandler: Trapdoor failed")
                return 0f
            }
        }
    }

    else if ( bData is Stairs ) {
        return fun(
            b: Block, // block
            x0in: Float, // x
            y0in: Float, // y
            z0in: Float, // z
            dirX: Float, // dirX
            dirY: Float, // dirY
            dirZ: Float, // dirZ
            d: Float  // max distance in step
        ): Float {
            try {
                val blockY = b.getY().toFloat()
                val stairs = b.getBlockData() as Stairs
                val facing: BlockFace = stairs.getFacing()
                val shape: Stairs.Shape = stairs.getShape()
                val vertical: Half = stairs.getHalf()

                // end points
                val y0 = y0in - blockY
                val y1 = y0 + d * dirY

                // do stair top and bottom half check (these are half slab checks)
                if ( vertical == Half.BOTTOM ) {
                    if ( y0 < 0.5f ) {
                        return 0f
                    } else if ( y1 < 0.5f ) {
                        return 0.5f * d // approximation
                    }
                }
                if ( vertical == Half.TOP ) {
                    if ( y0 > 0.5f ) {
                        return 0f
                    } else if ( y1 > 0.5f ) {
                        return 0.5f * d // approximation
                    }
                }

                val bx = b.getX().toFloat()
                val bz = b.getZ().toFloat()

                val x0 = x0in - bx
                val z0 = z0in - bz
                val x1 = x0 + d * dirX
                val z1 = z0 + d * dirZ
                val xMid = 0.5f * (x0 + x1)
                val zMid = 0.5f * (z0 + z1)

                // hardcoded raycasts for each orientation

                if ( facing == BlockFace.EAST) { // +X
                    when ( shape ) {
                        Stairs.Shape.STRAIGHT -> if ( x0 > 0.5f ) {
                            return 0f
                        } else if ( x1 > 0.5f ) {
                            return 0.5f * d
                        }
                        // east || north
                        Stairs.Shape.INNER_LEFT -> if ( x0 > 0.5f || z0 < 0.5f ) {
                            return 0f
                        } else if ( x1 > 0.5f || z0 < 0.5f ) {
                            return 0.5f * d
                        }
                        // east || south
                        Stairs.Shape.INNER_RIGHT -> if ( x0 > 0.5f || z0 > 0.5f ) {
                            return 0f
                        } else if ( x1 > 0.5f || z1 > 0.5f ) {
                            return 0.5f * d
                        }
                        // east && north
                        Stairs.Shape.OUTER_LEFT -> if ( x0 > 0.5f && z0 < 0.5f ) {
                            return 0f
                        } else if ( x1 > 0.5f && z1 < 0.5f ) {
                            return 0.5f * d
                        } else if ( xMid > 0.5f && zMid < 0.5f ) {
                            return 0.5f * d
                        }
                        // east && south
                        Stairs.Shape.OUTER_RIGHT -> if ( x0 > 0.5f && z0 > 0.5f ) {
                            return 0f
                        } else if ( x1 > 0.5f && z1 > 0.5f ) {
                            return 0.5f * d
                        } else if ( xMid > 0.5f && zMid > 0.5f ) {
                            return 0.5f * d
                        }
                    }
                }
                else if ( facing == BlockFace.WEST ) { // -X
                    when ( shape ) {
                        Stairs.Shape.STRAIGHT -> if ( x0 < 0.5f ) {
                            return 0f
                        } else if ( x1 < 0.5f ) {
                            return 0.5f * d
                        }
                        // west || south
                        Stairs.Shape.INNER_LEFT -> if ( x0 < 0.5f || z0 > 0.5f ) {
                            return 0f
                        } else if ( x1 < 0.5f || z1 > 0.5f ) {
                            return 0.5f * d
                        }
                        // west || north
                        Stairs.Shape.INNER_RIGHT -> if ( x0 < 0.5f || z0 < 0.5f ) {
                            return 0f
                        } else if ( x1 < 0.5f || z1 < 0.5f ) {
                            return 0.5f * d
                        }
                        // west && south
                        Stairs.Shape.OUTER_LEFT -> if ( x0 < 0.5f && z0 > 0.5f ) {
                            return 0f
                        } else if ( x1 < 0.5f && z1 > 0.5f ) {
                            return 0.5f * d
                        } else if ( xMid < 0.5f && zMid > 0.5f ) {
                            return 0.5f * d
                        }
                        // west && north
                        Stairs.Shape.OUTER_RIGHT -> if ( x0 < 0.5f && z0 < 0.5f ) {
                            return 0f
                        } else if ( x1 < 0.5f && z1 < 0.5f ) {
                            return 0.5f * d
                        } else if ( xMid < 0.5f && zMid < 0.5f ) {
                            return 0.5f * d
                        }
                    }
                }
                else if ( facing == BlockFace.NORTH ) { // -Z
                    when ( shape ) {
                        Stairs.Shape.STRAIGHT -> if ( z0 < 0.5f ) {
                            return 0f
                        } else if ( z1 < 0.5f ) {
                            return 0.5f * d
                        }
                        // north || west
                        Stairs.Shape.INNER_LEFT -> if ( z0 < 0.5f || x0 < 0.5f ) {
                            return 0f
                        } else if ( z1 < 0.5f || x1 < 0.5f ) {
                            return 0.5f * d
                        }
                        // north || east
                        Stairs.Shape.INNER_RIGHT -> if ( z0 < 0.5f || x0 > 0.5f ) {
                            return 0f
                        } else if ( z1 < 0.5f || x1 > 0.5f ) {
                            return 0.5f * d
                        }
                        // outer are quarter block => just do midpoint test approximation
                        // north && west
                        Stairs.Shape.OUTER_LEFT -> if ( z0 < 0.5f && x0 < 0.5f ) {
                            return 0f
                        } else if ( z1 < 0.5f && x1 < 0.5f ) {
                            return 0.5f * d
                        } else if ( zMid < 0.5f && xMid < 0.5f ) {
                            return 0.5f * d
                        }
                        // north && east
                        Stairs.Shape.OUTER_RIGHT -> if ( z0 < 0.5f && x0 > 0.5f ) {
                            return 0f
                        } else if ( z1 < 0.5f && x1 > 0.5f ) {
                            return 0.5f * d
                        } else if ( zMid < 0.5f && xMid > 0.5f ) {
                            return 0.5f * d
                        }
                    }
                }
                else if ( facing == BlockFace.SOUTH ) { // +Z
                    when ( shape ) {
                        Stairs.Shape.STRAIGHT -> if ( z0 > 0.5f ) {
                            return 0f
                        } else if ( z1 > 0.5f ) { 
                            return 0.5f * d
                        }
                        // south || east
                        Stairs.Shape.INNER_LEFT -> if ( z0 > 0.5f || x0 > 0.5f ) {
                            return 0f
                        } else if ( z1 > 0.5f || x1 > 0.5f ) {
                            return 0.5f * d
                        }
                        // south || west
                        Stairs.Shape.INNER_RIGHT -> if ( z0 > 0.5f || x0 < 0.5f ) {
                            return 0f
                        } else if ( z1 > 0.5f || x1 < 0.5f ) {
                            return 0.5f * d
                        }
                        // outer are quarter block => also do midpoint test
                        // south && east
                        Stairs.Shape.OUTER_LEFT -> if ( z0 > 0.5f && x0 > 0.5f ) {
                            return 0f
                        } else if ( z1 > 0.5f && x1 > 0.5f ) {
                            return 0.5f * d
                        } else if ( zMid > 0.5f && xMid > 0.5f ) {
                            return 0.5f * d
                        }
                        // south && west
                        Stairs.Shape.OUTER_RIGHT -> if ( z0 > 0.5f && x0 < 0.5f ) {
                            return 0f
                        } else if ( z1 > 0.5f && x1 < 0.5f ) {
                            return 0.5f * d
                        } else if ( zMid > 0.5f && xMid < 0.5f ) {
                            return 0.5f * d
                        }
                    }
                }

                return Float.MAX_VALUE
            }
            catch ( err: Exception ) {
                System.err.println("BlockCollisionHandler: Stairs failed")
                return 0f
            }
        }
    }

    else if ( bData is Wall ) {
        return fun(
            b: Block, // block
            x0in: Float, // x
            _: Float, // y
            z0in: Float, // z
            dirX: Float, // dirX
            _: Float, // dirY
            dirZ: Float, // dirZ
            d: Float  // max distance in step
        ): Float {
            try {
                val wallData = b.getBlockData() as Wall
                val bx: Float = b.getX().toFloat()
                val bz: Float = b.getZ().toFloat()
                
                // end points
                val x0 = x0in - bx
                val z0 = z0in - bz
                val x1 = x0 + d * dirX
                val z1 = z0 + d * dirZ

                // mid point (for center of wall check)
                val xMid = 0.5 * (x0 + x1)
                val zMid = 0.5 * (z0 + z1)

                // center of wall: just check mid point, approximate check
                // will not perfectly handle corners of center.
                if ( xMid > 0.25f && xMid < 0.75f && zMid > 0.25f && zMid < 0.75f ) {
                    return 0.25f * d // approximation for hit location
                }

                // check each region of wall on each face, wall looks like a plus: +
                // where each edge extends towards one of the faces
                val invDirX = 1f / dirX
                val invDirZ = 1f/ dirZ

                if ( wallData.getHeight(BlockFace.NORTH) != Wall.Height.NONE ) { // -Z
                    val hit = aabbRayCollision2D(
                        0.25f, 0f, // (xmin, zmin)
                        0.75f, 0.75f, // (xmax, zmax)
                        x0, z0,
                        invDirX, invDirZ,
                    )
                    if ( hit != Float.MAX_VALUE ) return hit
                }
                if ( wallData.getHeight(BlockFace.SOUTH) != Wall.Height.NONE ) { // +Z
                    val hit = aabbRayCollision2D(
                        0.25f, 0.25f, // (xmin, zmin)
                        0.75f, 1.0f, // (xmax, zmax)
                        x0, z0,
                        invDirX, invDirZ,
                    )
                    if ( hit != Float.MAX_VALUE ) return hit
                }
                if ( wallData.getHeight(BlockFace.WEST) != Wall.Height.NONE ) { // -X
                    val hit = aabbRayCollision2D(
                        0f, 0.25f, // (xmin, zmin)
                        0.75f, 0.75f, // (xmax, zmax)
                        x0, z0,
                        invDirX, invDirZ,
                    )
                    if ( hit != Float.MAX_VALUE ) return hit
                }
                if ( wallData.getHeight(BlockFace.EAST) != Wall.Height.NONE ) { // +X
                    val hit = aabbRayCollision2D(
                        0.25f, 0.25f, // (xmin, zmin)
                        1.0f, 0.75f, // (xmax, zmax)
                        x0, z0,
                        invDirX, invDirZ,
                    )
                    if ( hit != Float.MAX_VALUE ) return hit
                }

                return Float.MAX_VALUE
            }
            catch ( err: Exception ) {
                System.err.println("BlockCollisionHandler: Wall failed")
                return 0f
            }
        }
    }

    else if ( bData is Fence ) {
        return fun(
            b: Block, // block
            x0: Float, // x
            _: Float, // y
            z0: Float, // z
            dirX: Float, // dirX
            _: Float, // dirY
            dirZ: Float, // dirZ
            d: Float  // max distance in step
        ): Float {
            val bx = b.getX().toFloat()
            val bz = b.getZ().toFloat()

            // just test midpoint between (x0, z0) and (x1, z1)
            // this is approximation, will not catch corners perfectly
            val xMid = x0 + (dirX * (d/2f))
            val zMid = z0 + (dirZ * (d/2f))
            
            // center of fence (~0.25f wide)
            if ( xMid > (bx + 0.375f) && xMid < (bx + 0.625f) && zMid > (bz + 0.375f) && zMid < (bz + 0.625f) ) {
                return 0.3f*d // approximation for where hit occurs
            }
            
            return Float.MAX_VALUE
        }
    }

    else { // default
        if ( m.isOccluding() ) {
            return defaultSolidBlockHandler
        }
        else {
            return defaultNoCollisionHandler
        }
    }
}


/**
 * 2D (x,z) AABB collision check. Commonly used for blocks
 * since many blocks y-value extends from [0, 1] and only
 * hitbox is in (x, z) plane. Returns a distance to hit
 * if collision found. Otherwise, returns Float.MAX_VALUE. 
 * Calculates collision using slab method, see
 * Hitbox.intersectsRayLocation.
 */
private fun aabbRayCollision2D(
    // box
    xmin: Float,
    zmin: Float,
    xmax: Float,
    zmax: Float,
    // ray origin
    rx: Float,
    rz: Float,
    // inverse ray direction
    rInvDirX: Float,
    rInvDirZ: Float,
): Float {
    val tx1 = (xmin - rx) * rInvDirX
    val tx2 = (xmax - rx) * rInvDirX

    var tmin = min(tx1, tx2)
    var tmax = max(tx1, tx2)

    val tz1 = (zmin - rz) * rInvDirZ
    val tz2 = (zmax - rz) * rInvDirZ

    tmin = max(tmin, min(tz1, tz2))
    tmax = min(tmax, max(tz1, tz2))

    tmin = max(0f, tmin) // normalize to only forward direction

    return if ( tmax >= tmin ) tmin else Float.MAX_VALUE
}

/**
 * Create a clone of collision handlers map but make bullets pass through
 * all door type blocks (doors, trapdoors).
 */
public fun blockCollisionHandlersPassthroughDoors(
    handlers: EnumArrayMap<Material, BlockCollisionHandler>
): EnumArrayMap<Material, BlockCollisionHandler> {
    return EnumArrayMap.from<Material, BlockCollisionHandler>({ m ->
        if ( !m.isBlock() ) { // items, non-blocks, etc.
            defaultNoCollisionHandler
        } else {
            try {
                val bData = m.createBlockData()
                if ( bData is Door || bData is TrapDoor ) {
                    defaultNoCollisionHandler
                }
                else {
                    handlers[m]
                }
            }
            catch ( e: Exception ) {
                // probably not a block, this should never run
                if ( m.isOccluding() ) {
                    defaultSolidBlockHandler
                }
                else {
                    defaultNoCollisionHandler
                }
            }
        }
    })
}
