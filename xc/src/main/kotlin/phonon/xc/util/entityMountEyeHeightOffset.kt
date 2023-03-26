/**
 * Vehicle utils
 */

package phonon.xc.util

import org.bukkit.entity.EntityType

/**
 * When players are in a vehicle, their eye height does not adjust properly.
 * Need to manually add vehicle mount eye height offset for proper
 * bullet shoot location, and recoil.
 * 
 * Literally just guess and check these until they work...
 * Easier than trying to figure out how mineman black box works.
 */
public fun entityMountEyeHeightOffset(type: EntityType): Double {
    return when ( type ) {
        EntityType.HORSE -> 0.85 // == horse mount height
        EntityType.BOAT -> -0.45 // == boat mount height offset?
        // EntityType.ARMOR_STAND -> 1.75 // TODO...idk how to manage across vehicles
        EntityType.ARMOR_STAND -> 0.0 // TODO...for now, zero marker height armorstands need no adjustment
        else -> 1.0 // TODO
    }
}