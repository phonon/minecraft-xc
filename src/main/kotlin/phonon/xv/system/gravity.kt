package phonon.xv.system

import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.max
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.PI
import org.bukkit.World
import org.bukkit.Material
import org.bukkit.entity.Player
import phonon.xv.XV
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.iter.*
import phonon.xv.component.GravityComponent
import phonon.xv.component.TransformComponent

/**
 * System for applying gravity on transform of vehicle.
 * Used for stationary weapons like mortars and cannons which simply
 * rotate in place.
 * 
 * Do not use with other motion controllers
 * (e.g. LandMovementControlsComponent) as these internally implement
 * gravity in their motion control systems.
 * 
 * Note reason gravity is done so slow only ~0.1 block / step is
 * because mineman client side prediction is really aggresive, it will
 * detect the armor stand velocity and almost always make it go further
 * into the ground. Relogging makes the armor stand appear in the real spot.
 * Only way to mitigate is to reduce the gravity rate.
 */
public fun XV.systemGravity(
    storage: ComponentsStorage,
) {
    val xv = this

    for ( (el, transform, gravity) in ComponentTuple2.query<
        TransformComponent,
        GravityComponent,
    >(storage) ) {
        try {
            // clear transform dirty flags
            transform.positionDirty = false

            if ( gravity.delayCounter > 0 ) {
                gravity.delayCounter -= 1
                continue
            }

            gravity.didGravityCounter = max(0, gravity.didGravityCounter - 1)

            // println("gravity for element ${el}") // debug

            val world = transform.world
            if ( world === null ) {
                continue
            }

            val xCurr = transform.x
            val yCurr = transform.y
            val zCurr = transform.z

            // y level inside block
            val yBl = floor(yCurr)
            val yInBlock = yCurr - yBl

            // center block of body
            val blx = floor(xCurr).toInt()
            val bly = floor(yCurr).toInt()
            val blz = floor(zCurr).toInt()

            // do gravity within block
            if ( yInBlock > 0.1 && world.getBlockAt(blx, bly, blz).isPassable() ) {
                if ( yInBlock > 0.2 ) {
                    transform.y -= 0.1
                } else {
                    transform.y = yBl
                }
                transform.positionDirty = true
                gravity.didGravityCounter = 10
            }
            // falling
            else {
                val blyBelow = bly - 1
                val yBlBelow = blyBelow.toDouble()
                val blockBelow = world.getBlockAt(blx, blyBelow, blz)
    
                // println("transform.y = ${transform.y}, yBlBelow = ${yBlBelow}")
                if ( gravity.area <= 1 ) { // simple single block check
                    if ( blockBelow.isPassable() ) { // fall down
                        transform.y -= 0.1 // TODO configurable drop speed
                        // println("falling down to new y = ${transform.y}")
                        transform.positionDirty = true
                        gravity.didGravityCounter = 4
                    }
                } else { // area > 1, do 5 point cross
                    if ( blockBelow.isPassable() &&
                        world.getBlockAt(blx + 1, blyBelow, blz).isPassable() &&
                        world.getBlockAt(blx - 1, blyBelow, blz).isPassable() &&
                        world.getBlockAt(blx, blyBelow, blz + 1).isPassable() &&
                        world.getBlockAt(blx, blyBelow, blz - 1).isPassable()
                    ) { // fall down
                        transform.y -= 0.1 // TODO configurable drop speed
                        transform.positionDirty = true
                        gravity.didGravityCounter = 4
                    }
                }
            }

            if ( gravity.didGravityCounter > 0 ) {
                gravity.delayCounter = gravity.delay
            } else {
                gravity.delayCounter = 20 // "sleep" mode
            }
        }
        catch ( err: Exception ) {
            if ( xv.debug ) {
                err.printStackTrace()
                xv.logger.warning("Error in gravity system for element ${el}: ${err.message}")
            }
        }
    }
}
