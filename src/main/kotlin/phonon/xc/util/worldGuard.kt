/**
 * WorldGuard API wrapper (v7 api only).
 */

package phonon.xc.util

import org.bukkit.Location
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.protection.flags.Flags
import com.sk89q.worldguard.protection.flags.StateFlag

public object WorldGuard {
    /**
     * Check if region pvp flag enabled.
     */
    public fun canPvpAt(loc: Location): Boolean {
        val regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer()
        val query = regionContainer.createQuery()
        val regions = query.getApplicableRegions(BukkitAdapter.adapt(loc))
        // this only blocks if region explicity denies (so pvp on by default)
        if ( regions.queryState(null, Flags.PVP) == StateFlag.State.DENY ) {
            return false
        }
        return true
    }

    /**
     * Check if region explosion flag enabled.
     */
    public fun canExplodeAt(loc: Location): Boolean {
        val regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer()
        val query = regionContainer.createQuery()
        val regions = query.getApplicableRegions(BukkitAdapter.adapt(loc))
        // this only blocks if region explicity denies (so explosions on by default)
        if ( regions.queryState(null, Flags.OTHER_EXPLOSION) == StateFlag.State.DENY ) {
            return false
        }
        return true
    }

    /**
     * Check if region fire spread flag enabled.
     */
    public fun canCreateFireAt(loc: Location): Boolean {
        val regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer()
        val query = regionContainer.createQuery()
        val regions = query.getApplicableRegions(BukkitAdapter.adapt(loc))
        // this only blocks if region explicity denies (so fire on by default)
        if ( regions.queryState(null, Flags.FIRE_SPREAD) == StateFlag.State.DENY ) {
            return false
        }
        return true
    }
}
