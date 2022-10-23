

package phonon.xv.core

import java.util.EnumSet
import phonon.xv.component.*

public const val MAX_VEHICLE_ELEMENTS: Int = 10000

public class ComponentsStorage {
    // TODO:
    // this needs to be replaced with a densemap that maps from
    // layout => Archetype storage
    // must handle adding archetypes, checking if already exist, etc...
    public val archetypes: ArrayList<ArchetypeStorage> = ArrayList()
}
