package phonon.xv.component

import com.google.gson.JsonObject
import java.util.logging.Logger
import org.tomlj.TomlTable
import phonon.xc.util.toml.*
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject

/**
 * Marker component indicating vehicle should immediately fire when
 * ammo is loaded. Made this separate component since many vehicles
 * will have ammo but not fire immediately. Want to avoid iterating
 * all vehicles with ammo (most vehicles) to check if they should fire
 * when loaded.
 * 
 * Used for weapon like a mortar that should fire immediately when
 * a player loads the weapon.
 */
public data class AmmoFireWhenLoadedComponent(
    val _dummy: Boolean = true, // dummy property to avoid empty data class
): VehicleComponent<AmmoFireWhenLoadedComponent> {
    override val type = VehicleComponentType.AMMO_FIRE_WHEN_LOADED

    override fun self() = this

    override fun deepclone(): AmmoFireWhenLoadedComponent {
        return this.copy()
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, _logger: Logger? = null): AmmoFireWhenLoadedComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            return mapToObject(properties, AmmoFireWhenLoadedComponent::class)
        }
    }
}