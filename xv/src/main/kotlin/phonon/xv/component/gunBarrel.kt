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
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.EulerAngle
import phonon.xc.XC
import phonon.xc.util.HitboxSize
import phonon.xv.core.ENTITY_KEY_COMPONENT
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
    // use separate custom models when ammo is loaded (if this id != -1)
    val loadedModelId: Int = -1,
    // name of skins variant set to use instead of single model id (optional)
    val skin: String? = null,
    val skinDefaultVariant: String? = null,
    // whether to show the armor stand (for debugging)
    val armorstandVisible: Boolean = false, // @skip
    // hitbox size in blocks, at local position
    // @prop hitbox = [1.0, 1.0, 1.0]
    val hitboxX: Double = 0.0, // @skip
    val hitboxY: Double = 0.0, // @skip
    val hitboxZ: Double = 0.0, // @skip
    val hitboxYOffset: Double = 0.0,
    // if can shoot with left mouse, which ammo group to shoot (-1 to disable)
    val shootMouseWeapon: Int = 0,
    // if can shoot with spacebar, which ammo group to shoot (-1 to disable)
    val shootSpacebarWeapon: Int = -1,
    // offset along barrel direction to spawn projectile
    val projectileOffset: Double = 1.0,

    // @skipall
    // armor stand entity
    var armorstand: ArmorStand? = null,
    // rotation
    var yaw: Double = 0.0,
    var pitch: Double = -(0.5 * pitchMin + 0.5 * pitchMax),
): VehicleComponent<GunBarrelComponent> {
    override val type = VehicleComponentType.GUN_BARREL

    override fun self() = this

    override fun deepclone(): GunBarrelComponent {
        return this.copy()
    }

    // hitbox size
    val hitboxSize: HitboxSize = HitboxSize(
        xHalf = (this.hitboxX / 2.0).toFloat(),
        zHalf = (this.hitboxZ / 2.0).toFloat(),
        yHeight = this.hitboxY.toFloat(),
        yOffset = this.hitboxYOffset.toFloat(),
    )

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
        armorstand.persistentDataContainer.set(ENTITY_KEY_COMPONENT, PersistentDataType.STRING, VehicleComponentType.GUN_BARREL.toString())
        armorstand.setGravity(false)
        // armorstand.setInvulnerable(true) // DONT DO THIS, EntityDamageByEntityEvent never triggers
        armorstand.setVisible(armorstandVisible)
        armorstand.setRotation(locSpawn.yaw, 0f)
        armorstand.setHeadPose(EulerAngle(
            this.pitchRad,
            spawnYawRad,
            0.0,
        ))

        return this.copy(
            armorstand = armorstand,
            yaw = spawnYaw,
        )
    }

    override fun toJson(): JsonObject {
        val json = JsonObject()
        json.addProperty("yaw", this.yaw)
        json.addProperty("pitch", this.pitch)
        return json
    }

    override fun injectJsonProperties(json: JsonObject?): GunBarrelComponent {
        if ( json === null ) return this.self()
        return this.copy(
            yaw = json["yaw"]?.asDouble ?: 0.0,
            pitch = json["pitch"]?.asDouble ?: this.pitchMin,
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
                VehicleComponentType.GUN_BARREL
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
            entity.setInvulnerable(false) // make sure armorstand vulnerable so interact triggers
            // entity -> vehicle mapping
            entityVehicleData[entity.uniqueId] = EntityVehicleData(
                vehicle,
                element,
                VehicleComponentType.GUN_BARREL,
            )
            // register vehicle hitbox in xc combat
            if ( hitboxSize.xHalf > 0f && hitboxSize.zHalf > 0f && hitboxSize.yHeight > 0f ) {
                xc.addHitbox(entity.getUniqueId(), hitboxSize)
            }
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

            toml.getArray("hitbox")?.let { arr ->
                properties["hitboxX"] = arr.getNumberAs<Double>(0)
                properties["hitboxY"] = arr.getNumberAs<Double>(1)
                properties["hitboxZ"] = arr.getNumberAs<Double>(2)
            }

            toml.getNumberAs<Double>("hitbox_y_offset")?.let { properties["hitboxYOffset"] = it }
            
            toml.getLong("shoot_mouse_weapon")?.let { properties["shootMouseWeapon"] = it.toInt() }
            toml.getLong("shoot_spacebar_weapon")?.let { properties["shootSpacebarWeapon"] = it.toInt() }
            
            toml.getNumberAs<Double>("projectile_offset")?.let { properties["projectileOffset"] = it }

            return mapToObject(properties, GunBarrelComponent::class)
        }
    }
}
