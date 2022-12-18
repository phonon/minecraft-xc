package phonon.xv.component

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.util.logging.Logger
import org.tomlj.TomlTable
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*

/**
 * Contains settings for spawning/despawning vehicle.
 * E.g. time to spawn, despawn.
 */
public data class SpawnComponent(
    // time to spawn in seconds
    val spawnTimeSeconds: Double,
    // time to despawn in seconds
    val despawnTimeSeconds: Double,
): VehicleComponent<SpawnComponent> {
    override val type = VehicleComponentType.FUEL

    override fun self() = this

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, logger: Logger? = null): SpawnComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getNumberAs<Double>("spawn_time_seconds")?.let { properties["spawnTimeSeconds"] = it }
            toml.getNumberAs<Double>("despawn_time_seconds")?.let { properties["despawnTimeSeconds"] = it }

            return mapToObject(properties, SpawnComponent::class)
        }
    }
}