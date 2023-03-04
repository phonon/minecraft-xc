package phonon.xv.component

import com.google.gson.JsonObject
import java.util.UUID
import java.util.EnumSet
import java.util.logging.Logger
import org.tomlj.TomlTable
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import phonon.xc.XC
import phonon.xc.util.HitboxSize
import phonon.xv.XV
import phonon.xv.core.ENTITY_KEY_COMPONENT
import phonon.xv.core.Vehicle
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.VehicleElement
import phonon.xv.core.EntityVehicleData
import phonon.xv.util.entity.setVehicleUuid
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*
import phonon.xv.util.item.createCustomModelItem

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
    // @prop hitbox = [0.0, 0.0, 0.0]
    val hitboxX: Double = 0.0, // @skip
    val hitboxY: Double = 0.0, // @skip
    val hitboxZ: Double = 0.0, // @skip
    val hitboxYOffset: Double = 0.0,
    // seat to mount when armorstand clicked
    val seatToMount: Int = -1, // -1 for none
    // material for model
    // @prop material = "BONE"
    val material: Material = Material.BONE, // @skip
    // custom model data for the armorstand
    val modelId: Int = 0,
    // name of skins variant set to use instead of single model id (optional)
    val skin: String? = null,
    val skinDefaultVariant: String? = null,
    // whether to show the armor stand (for debugging)
    val armorstandVisible: Boolean = false, // @skip

    // @skipall
    // armor stand entity
    var armorstand: ArmorStand? = null,
): VehicleComponent<ModelComponent> {
    override val type = VehicleComponentType.MODEL

    override fun self() = this

    override fun deepclone(): ModelComponent {
        return this.copy()
    }
    
    val hitboxSize: HitboxSize = HitboxSize(
        xHalf = (this.hitboxX / 2.0).toFloat(),
        zHalf = (this.hitboxZ / 2.0).toFloat(),
        yHeight = this.hitboxY.toFloat(),
        yOffset = this.hitboxYOffset.toFloat(),
    )

    /**
     * Create armor stand at spawn location.
     */
    override fun injectSpawnProperties(
        location: Location?,
        player: Player?,
    ): ModelComponent {
        if ( location === null) return this.self()

        val locSpawn = location.clone().add(offsetX, offsetY, offsetZ)

        val armorstand: ArmorStand = locSpawn.world.spawn(locSpawn, ArmorStand::class.java)
        armorstand.persistentDataContainer.set(ENTITY_KEY_COMPONENT, PersistentDataType.STRING, VehicleComponentType.MODEL.toString())
        armorstand.setGravity(false)
        armorstand.setInvulnerable(true)
        armorstand.setVisible(armorstandVisible)
        armorstand.setRotation(locSpawn.yaw, 0f)

        return this.copy(
            armorstand = armorstand,
        )
    }

    /**
     * After component created, add armorstand to vehicle mappings,
     * and add model to armorstand.
     */
    override fun afterVehicleCreated(
        xc: XC,
        vehicle: Vehicle,
        element: VehicleElement,
        entityVehicleData: HashMap<UUID, EntityVehicleData>,
    ) {
        val armorstand = this.armorstand
        if ( armorstand !== null ) {
            // entity -> vehicle mapping
            entityVehicleData[armorstand.uniqueId] = EntityVehicleData(
                vehicle,
                element,
                VehicleComponentType.MODEL
            )

            // add a stable entity reassociation key used to associate entity
            // with element this should be stable even if the armor stand
            // entity needs to be re-created
            armorstand.setVehicleUuid(vehicle.uuid, element.uuid)

            // register vehicle hitbox in xc combat
            if ( hitboxSize.xHalf > 0f && hitboxSize.zHalf > 0f && hitboxSize.yHeight > 0f ) {
                xc.addHitbox(armorstand.getUniqueId(), hitboxSize)
            }

            // add model to armorstand
            if ( modelId > 0 ) {
                armorstand.getEquipment().setHelmet(createCustomModelItem(material, modelId))
            }
        }
    }

    override fun delete(
        xc: XC,
        vehicle: Vehicle,
        element: VehicleElement,
        entityVehicleData: HashMap<UUID, EntityVehicleData>,
        despawn: Boolean,
    ) {
        val stand = this.armorstand
        if ( stand !== null ) {
            xc.removeHitbox(stand.getUniqueId())
            entityVehicleData.remove(stand.uniqueId)
            stand.remove()
        }
    }

    /**
     * Try to re-attach armorstand to this component, during reloading.
     */
    fun reassociateArmorstand(
        xc: XC,
        entity: Entity,
        vehicle: Vehicle,
        element: VehicleElement,
        entityVehicleData: HashMap<UUID, EntityVehicleData>,
    ) {
        if ( entity is ArmorStand ) {
            this.armorstand = entity
            // entity -> vehicle mapping
            entityVehicleData[entity.uniqueId] = EntityVehicleData(
                vehicle,
                element,
                VehicleComponentType.MODEL,
            )
            // register vehicle hitbox in xc combat
            if ( hitboxSize.xHalf > 0f && hitboxSize.zHalf > 0f && hitboxSize.yHeight > 0f ) {
                xc.addHitbox(entity.getUniqueId(), hitboxSize)
            }
        }
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, logger: Logger? = null): ModelComponent {
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

            toml.getNumberAs<Double>("hitbox_y_offset")?.let { properties["hitboxYOffset"] = it }

            toml.getLong("seat_to_mount")?.let { properties["seatToMount"] = it.toInt() }

            toml.getString("material")?.let { s ->
                Material.getMaterial(s)?.let { properties["material"] = it } ?: run {
                    logger?.warning("[ModelComponent] Invalid material: ${s}")
                }
            }

            toml.getLong("model_id")?.let { properties["modelId"] = it.toInt() }

            toml.getString("skin")?.let { properties["skin"] = it }
            toml.getString("skin_default_variant")?.let { properties["skinDefaultVariant"] = it }
            
            toml.getBoolean("armorstand_visible")?.let { properties["armorstandVisible"] = it }

            return mapToObject(properties, ModelComponent::class)
        }
    }
}