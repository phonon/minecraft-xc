

package phonon.xv.core

import java.util.EnumSet
import phonon.xv.component.*

public const val MAX_VEHICLE_ELEMENTS: Int = 10000

public class ComponentsStorage {
    // TODO:
    // this needs to be replaced with a proper densemap that maps from
    // layout => Archetype storage
    // must handle adding archetypes, checking if already exist, etc...
    public val lookup: HashMap<EnumSet<VehicleComponentType>, Int> = HashMap()
    public val archetypes: ArrayList<ArchetypeStorage> = ArrayList()

    public fun addLayout(layout: EnumSet<VehicleComponentType>) {
        if ( !this.lookup.containsKey(layout) ) {
            val index = this.archetypes.size
            this.archetypes.add(ArchetypeStorage(layout, MAX_VEHICLE_ELEMENTS))
            this.lookup[layout] = index
        }
    }

    // delete all component + archetype storage
    // make sure you dont have dangling vehicles
    // when you call
    public fun clear() {
        lookup.clear()
        archetypes.clear()
        // TODO
        // I imagine there's some extra stuff that needs to be done
        // to clean up each ArchetypeStorage
    }
}
