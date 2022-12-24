package phonon.xv.component

import java.util.UUID
import java.util.EnumSet
import java.util.logging.Logger
import com.google.gson.JsonObject
import org.tomlj.TomlTable
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.util.EulerAngle
import phonon.xv.core.EntityVehicleData
import phonon.xv.core.Vehicle
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.VehicleElement
import phonon.xv.common.ControlStyle
import phonon.xv.util.entity.setVehicleUuid
import phonon.xv.util.item.createCustomModelItem
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*

/**
 * WASD-controlled single barrel. Direction is the yaw of the vehicle.
 * Barrel pitch is moved up/down with W/S keys. Example usage is a barrel
 * for a cannon vehicle.
 * 
 * Contains LOCAL position/rotation offsets from base element transform.
 * Controls system functions should always use this in combination
 * with a TransformComponent as the base transform position.
 * 
 * This also internally manages rendering armor stand models
 * for barrel.
 */
public data class GunBarrelComponent(
    // barrel local offset relative to transform
    // @prop barrel_offset = [0.0, 1.0, 0.0]
    val barrelX: Double = 0.0, // @skip
    val barrelY: Double = 1.0, // @skip
    val barrelZ: Double = 0.0, // @skip
    // min and max barrel pitch rotation in degs
    val pitchMin: Double = -15.0,
    // max barrel pitch rotation in degs
    val pitchMax: Double = 15.0,
    // seat index that controls this component
    val seatController: Int = 0,
    // control style for yaw (mouse, wasd, or none)
    val controlYaw: ControlStyle = ControlStyle.NONE,
    // control style for pitch (mouse, wasd, or none)
    val controlPitch: ControlStyle = ControlStyle.NONE,
    // speed that barrel yaw rotates at
    val yawRotationSpeed: Double = 1.0,
    // speed that barrel pitch rotates at
    val pitchRotationSpeed: Double = 0.5,
    // if true, this component rotation will also update base transform
    val updateTransform: Boolean = false,
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
    // rotation
    var yaw: Double = 0.0,
    var pitch: Double = 0.0,
): VehicleComponent<GunBarrelComponent> {
    override val type = VehicleComponentType.GUN_BARREL

    override fun self() = this
    
    // local position state
    var yawf: Float = yaw.toFloat()
    var yawRad: Double = Math.toRadians(yaw)
    var yawSin: Double = Math.sin(yawRad)
    var yawCos: Double = Math.cos(yawRad)
    var pitchf: Float = pitch.toFloat()
    var pitchRad: Double = Math.toRadians(pitch)
    var pitchSin: Double = Math.sin(pitchRad)
    var pitchCos: Double = Math.cos(pitchRad)

    /**
     * Helper to update yaw and its derived values.
     */
    fun updateYaw(yaw: Double) {
        this.yaw = yaw
        this.yawf = yaw.toFloat()
        this.yawRad = Math.toRadians(yaw)
        this.yawSin = Math.sin(yawRad)
        this.yawCos = Math.cos(yawRad)
    }

    /**
     * Helper to update pitch and its derived values.
     */
    fun updatePitch(pitch: Double) {
        this.pitch = pitch
        this.pitchf = pitch.toFloat()
        this.pitchRad = Math.toRadians(pitch)
        this.pitchSin = Math.sin(pitchRad)
        this.pitchCos = Math.cos(pitchRad)
    }

    /**
     * Create armor stand at spawn location.
     */
    override fun injectSpawnProperties(
        location: Location?,
        player: Player?,
    ): GunBarrelComponent {
        if ( location === null) return this.self()

        // get yaw from location
        val spawnYaw = location.yaw.toDouble()
        val spawnYawRad = Math.toRadians(spawnYaw)

        val locSpawn = location.clone().add(barrelX, barrelY, barrelZ)
        locSpawn.yaw = 0f
        locSpawn.pitch = 0f

        val armorstand: ArmorStand = locSpawn.world.spawn(locSpawn, ArmorStand::class.java)
        armorstand.setGravity(false)
        armorstand.setInvulnerable(true)
        armorstand.setVisible(armorstandVisible)
        armorstand.setRotation(locSpawn.yaw, 0f)
        armorstand.setHeadPose(EulerAngle(
            0.0,
            spawnYawRad,
            0.0,
        ))

        return this.copy(
            armorstand = armorstand,
            yaw = spawnYaw,
            pitch = 0.0,
        )
    }

    /**
     * After component created, add armorstand to vehicle mappings,
     * and add model to armorstand.
     */
    override fun afterVehicleCreated(
        vehicle: Vehicle,
        element: VehicleElement,
        entityVehicleData: HashMap<UUID, EntityVehicleData>,
    ) {
        val armorstand = this.armorstand
        if ( armorstand !== null ) {
            // entity -> vehicle mapping
            entityVehicleData[armorstand.uniqueId] = EntityVehicleData(
                vehicle.id,
                element.id,
                element.layout,
                VehicleComponentType.GUN_BARREL
            )

            // add a stable entity reassociation key used to associate entity
            // with element this should be stable even if the armor stand
            // entity needs to be re-created
            armorstand.setVehicleUuid(element.uuid)

            // add model to armorstand
            if ( modelId > 0 ) {
                armorstand.getEquipment().setHelmet(createCustomModelItem(material, modelId))
            }
        }
    }

    override fun delete(
        vehicle: Vehicle,
        element: VehicleElement,
        entityVehicleData: HashMap<UUID, EntityVehicleData>
    ) {
        val stand = this.armorstand
        if ( stand !== null ) {
            entityVehicleData.remove(stand.uniqueId)
            stand.remove()
        }
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, logger: Logger? = null): GunBarrelComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()
            
            toml.getArray("barrel_offset")?.let { arr ->
                properties["barrelX"] = arr.getNumberAs<Double>(0)
                properties["barrelY"] = arr.getNumberAs<Double>(1)
                properties["barrelZ"] = arr.getNumberAs<Double>(2)
            }

            toml.getNumberAs<Double>("pitch_min")?.let { properties["pitchMin"] = it.toFloat() }
            toml.getNumberAs<Double>("pitch_max")?.let { properties["pitchMax"] = it.toFloat() }
            
            toml.getLong("seat_controller")?.let { properties["seatController"] = it.toInt() }
            toml.getString("control_yaw")?.let { properties["controlYaw"] = ControlStyle.fromStringOrNone(it, logger) }
            toml.getString("control_pitch")?.let { properties["controlPitch"] = ControlStyle.fromStringOrNone(it, logger) }
            toml.getNumberAs<Double>("yaw_rotation_speed")?.let { properties["yawRotationSpeed"] = it }
            toml.getNumberAs<Double>("pitch_rotation_speed")?.let { properties["pitchRotationSpeed"] = it }
            
            toml.getBoolean("update_transform")?.let { properties["updateTransform"] = it }
            
            toml.getLong("seat_to_mount")?.let { properties["seatToMount"] = it.toInt() }

            toml.getString("material")?.let { s ->
                Material.getMaterial(s)?.let { properties["material"] = it } ?: run {
                    logger?.warning("[GunBarrelComponent] Invalid material: ${s}")
                }
            }

            toml.getLong("model_id")?.let { properties["modelId"] = it.toInt() }

            toml.getString("skin")?.let { properties["skin"] = it }
            toml.getString("skin_default_variant")?.let { properties["skinDefaultVariant"] = it }
            
            toml.getBoolean("armorstand_visible")?.let { properties["armorstandVisible"] = it }

            return mapToObject(properties, GunBarrelComponent::class)
        }
    }
}
