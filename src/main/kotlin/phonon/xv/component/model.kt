package phonon.xv.component

import com.google.gson.JsonObject
import java.util.logging.Logger
import org.tomlj.TomlTable
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*


/**
 * Represents an ArmorStand model
 */
public data class ModelComponent(
    // armor stand local offset
    // @prop offset = [0.0, 0.0, 0.0]
    val offsetX: Double = 0.0, // @skip
    val offsetY: Double = 0.0, // @skip
    val offsetZ: Double = 0.0, // @skip
    // hitbox size in blocks, at local position
    // @prop hitbox = [2.0, 2.0, 2.0]
    val hitboxX: Double = 2.0, // @skip
    val hitboxY: Double = 2.0, // @skip
    val hitboxZ: Double = 2.0, // @skip
    // seat to mount when clicked
    val seatToMount: Int = -1, // -1 for none

    // @skipall
    // armor stand entity
    var armorstand: Entity? = null,
): VehicleComponent<ModelComponent> {
    override val type = VehicleComponentType.MODEL

    override fun self() = this

    /**
     * Create armor stand at spawn location.
     */
    override fun injectSpawnProperties(
        location: Location,
        player: Player?,
    ): ModelComponent {
        // spawn armor stand
        val armorstand: ArmorStand = location.world.spawn(location, ArmorStand::class.java)
        armorstand.setGravity(false)
        armorstand.setVisible(true)
        // armorstand.getEquipment()!!.setHelmet(createModel(Tank.modelMaterial, this.modelDataBody))
        armorstand.setRotation(location.yaw, 0f)

        return this.copy(
            armorstand = armorstand,
        )
    }

    override fun toJson(): JsonObject? = null

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, _logger: Logger? = null): ModelComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getArray("offset")?.let { arr ->
                properties["offsetX"] = arr.getNumberAs<Double>(0)
                properties["offsetY"] = arr.getNumberAs<Double>(1)
                properties["offsetZ"] = arr.getNumberAs<Double>(2)
            }
            
            toml.getArray("hitbox")?.let { arr ->
                properties["hitboxX"] = arr.getNumberAs<Double>(0)
                properties["hitboxY"] = arr.getNumberAs<Double>(1)
                properties["hitboxZ"] = arr.getNumberAs<Double>(2)
            }

            toml.getLong("seat_to_mount")?.let { properties["seatToMount"] = it.toInt() }

            return mapToObject(properties, ModelComponent::class)
        }

        public fun fromJson(json: JsonObject?, copy: ModelComponent) = null
    }
}