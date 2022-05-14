/**
 * WorldGuard API wrapper (v7 api only).
 */

package phonon.xc.utils

import org.bukkit.Location
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.protection.flags.Flags


public object WorldGuard {
    /**
     * Check if region pvp flag enabled.
     */
    public fun canPvpAt(loc: Location): Boolean {
        val regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer()
        val query = regionContainer.createQuery()
        val regions = query.getApplicableRegions(BukkitAdapter.adapt(loc))
        if ( !regions.testState(null, Flags.PVP) ) {
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
        if ( !regions.testState(null, Flags.OTHER_EXPLOSION) ) {
            return false
        }
        return true
    }
}
