package phonon.xv.component

import java.util.UUID
import java.util.EnumSet
import java.util.logging.Logger
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
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
import phonon.xc.util.mapToObject
import phonon.xc.util.toml.*
import phonon.xv.core.ENTITY_KEY_COMPONENT
import phonon.xv.core.EntityVehicleData
import phonon.xv.core.Vehicle
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.VehicleElement
import phonon.xv.common.ControlStyle
import phonon.xv.util.entity.setVehicleUuid
import phonon.xv.util.item.createCustomModelItem

/**
 * Mouse-controlled rotating gun turret.
 * 
 * Component made from two armor stands:
 * - Turret: yaw (y-plane) rotation only
 * - Barrel: up/down rotation only in-plane,
 *           forward direction = turret yaw
 * 
 * Contains LOCAL position/rotation offsets from base element transform.
 * Controls system functions should always use this in combination
 * with a TransformComponent as the base transform position.
 * 
 * This also internally manages rendering armor stand models
 * for turret and barrel.
 */
public data class GunTurretComponent(
    // turret local offset relative to transform
    // @prop turret_offset = [0.0, 1.0, 0.0]
    val turretX: Double = 0.0, // @skip
    val turretY: Double = 1.0, // @skip
    val turretZ: Double = 0.0, // @skip
    // barrel local offset relative to transform (NOT turret)
    // @prop barrel_offset = [0.0, 1.0, 0.0]
    val barrelX: Double = 0.0, // @skip
    val barrelY: Double = 1.0, // @skip
    val barrelZ: Double = 0.0, // @skip
    // min turret pitch rotation (in degs)
    val turretPitchMin: Double = -15.0,
    // max turret pitch rotation (in degs)
    val turretPitchMax: Double = 15.0,
    // min barrel pitch rotation (in degs)
    val barrelPitchMin: Double = -15.0,
    // max barrel pitch rotation (in degs)
    val barrelPitchMax: Double = 15.0,
    // max turret yaw rotation half-arc relative to transform, only used if >0 (in degs)
    val turretYawHalfArc: Double = 0.0,
    // max barrel yaw rotation half-arc relative to turret, only used if >0 (in degs)
    val barrelYawHalfArc: Double = 0.0,
    // control style for turret yaw (mouse, wasd, or none)
    // @prop turret_control_yaw = "NONE"
    val turretControlYaw: ControlStyle = ControlStyle.NONE, // @skip
    // control style for barrel yaw (mouse, wasd, or none)
    // @prop barrel_control_yaw = "NONE"
    val barrelControlYaw: ControlStyle = ControlStyle.NONE, // @skip
    // control style for barrel pitch (mouse, wasd, or none)
    // @prop barrel_control_pitch = "NONE"
    val barrelControlPitch: ControlStyle = ControlStyle.NONE, // @skip
    // speed that turret yaw rotates at
    val turretYawRotationSpeed: Double = 1.0,
    // speed that barrel yaw rotates at
    val barrelYawRotationSpeed: Double = 1.0,
    // speed that barrel pitch rotates at
    val barrelPitchRotationSpeed: Double = 0.5,
    // seat index that controls this component
    val seatController: Int = 0,
    // seat to mount when armorstand clicked, -1 for none
    val seatToMount: Int = -1,
    // if true, turret yaw will also update base transform yaw
    val updateTransform: Boolean = false,
    // material for model
    // @prop material = "BONE"
    val material: Material = Material.BONE, // @skip
    // custom model data for the armorstands
    val turretModelId: Int = 0,
    val barrelModelId: Int = 0,
    // use separate custom models when ammo is loaded (if this id != -1)
    val turretLoadedModelId: Int = -1,
    val barrelLoadedModelId: Int = -1,
    // name of skins variant set to use instead of single model id (optional)
    val skinTurret: String? = null,
    val skinTurretDefaultVariant: String? = null,
    val skinBarrel: String? = null,
    val skinBarrelDefaultVariant: String? = null,
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
    // shooting height offset
    val shootOffsetY: Double = 0.0,
    
    // @skipall
    // armor stand entities
    var armorstandBarrel: ArmorStand? = null,
    var armorstandTurret: ArmorStand? = null,
    // uuid of this model, for reassociating armor stand <-> model
    val uuidBarrel: UUID = UUID.randomUUID(),
    val uuidTurret: UUID = UUID.randomUUID(),
    // rotations
    var turretYaw: Double = 0.0,
    var turretPitch: Double = 0.0,
    var barrelYaw: Double = 0.0,
    var barrelPitch: Double = 0.0,
): VehicleComponent<GunTurretComponent> {
    override val type = VehicleComponentType.GUN_TURRET

    override fun self() = this

    override fun deepclone(): GunTurretComponent {
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
    var turretYawf: Float = turretYaw.toFloat()
    var turretYawRad: Double = Math.toRadians(turretYaw)
    var turretYawSin: Double = Math.sin(turretYawRad)
    var turretYawCos: Double = Math.cos(turretYawRad)
    var turretPitchf: Float = turretPitch.toFloat()
    var turretPitchRad: Double = Math.toRadians(turretPitch)
    var turretPitchSin: Double = Math.sin(turretPitchRad)
    var turretPitchCos: Double = Math.cos(turretPitchRad)

    var barrelYawf: Float = barrelYaw.toFloat()
    var barrelYawRad: Double = Math.toRadians(barrelYaw)
    var barrelYawSin: Double = Math.sin(barrelYawRad)
    var barrelYawCos: Double = Math.cos(barrelYawRad)
    var barrelPitchf: Float = barrelPitch.toFloat()
    var barrelPitchRad: Double = Math.toRadians(barrelPitch)
    var barrelPitchSin: Double = Math.sin(barrelPitchRad)
    var barrelPitchCos: Double = Math.cos(barrelPitchRad)

    /**
     * Helper to update turret yaw and its derived values.
     */
    fun updateTurretYaw(yaw: Double) {
        val yawRad = Math.toRadians(yaw)
        this.turretYaw = yaw
        this.turretYawf = yaw.toFloat()
        this.turretYawRad = yawRad
        this.turretYawSin = Math.sin(yawRad)
        this.turretYawCos = Math.cos(yawRad)
    }

    /**
     * Helper to update turret pitch and its derived values.
     */
    fun updateTurretPitch(pitch: Double) {
        val pitchRad = Math.toRadians(pitch)
        this.turretPitch = pitch
        this.turretPitchf = pitch.toFloat()
        this.turretPitchRad = pitchRad
        this.turretPitchSin = Math.sin(pitchRad)
        this.turretPitchCos = Math.cos(pitchRad)
    }

    /**
     * Helper to update turret yaw and its derived values.
     */
    fun updateBarrelYaw(yaw: Double) {
        val yawRad = Math.toRadians(yaw)
        this.barrelYaw = yaw
        this.barrelYawf = yaw.toFloat()
        this.barrelYawRad = yawRad
        this.barrelYawSin = Math.sin(yawRad)
        this.barrelYawCos = Math.cos(yawRad)
    }

    /**
     * Helper to update turret pitch and its derived values.
     */
    fun updateBarrelPitch(pitch: Double) {
        val pitchRad = Math.toRadians(pitch)
        this.barrelPitch = pitch
        this.barrelPitchf = pitch.toFloat()
        this.barrelPitchRad = pitchRad
        this.barrelPitchSin = Math.sin(pitchRad)
        this.barrelPitchCos = Math.cos(pitchRad)
    }

    /**
     * Create armor stand at spawn location.
     */
    override fun injectSpawnProperties(
        location: Location?,
        player: Player?,
    ): GunTurretComponent {
        if ( location === null) return this.self()

        // get yaw from location
        val spawnYaw = location.yaw.toDouble()
        val spawnYawRad = Math.toRadians(spawnYaw)

        // TODO: multiply by spawn rotation
        val locSpawnTurret = location.clone().add(turretX, turretY, turretZ)
        locSpawnTurret.yaw = 0f
        locSpawnTurret.pitch = 0f

        val armorstandTurret: ArmorStand = locSpawnTurret.world.spawn(locSpawnTurret, ArmorStand::class.java)
        armorstandTurret.persistentDataContainer.set(ENTITY_KEY_COMPONENT, PersistentDataType.STRING, VehicleComponentType.GUN_TURRET.toString())
        armorstandTurret.setGravity(false)
        // armorstand.setInvulnerable(true) // DONT DO THIS, EntityDamageByEntityEvent never triggers
        armorstandTurret.setVisible(armorstandVisible)
        armorstandTurret.setRotation(0f, 0f)
        armorstandTurret.setHeadPose(EulerAngle(
            0.0,
            spawnYawRad,
            0.0,
        ))

        val locSpawnBarrel = location.clone().add(barrelX, barrelY, barrelZ)
        locSpawnBarrel.yaw = 0f
        locSpawnBarrel.pitch = 0f
        
        val armorstandBarrel: ArmorStand = locSpawnBarrel.world.spawn(locSpawnBarrel, ArmorStand::class.java)
        armorstandBarrel.persistentDataContainer.set(ENTITY_KEY_COMPONENT, PersistentDataType.STRING, VehicleComponentType.GUN_TURRET.toString())
        armorstandBarrel.setGravity(false)
        // armorstand.setInvulnerable(true) // DONT DO THIS, EntityDamageByEntityEvent never triggers
        armorstandBarrel.setVisible(armorstandVisible)
        armorstandBarrel.setRotation(0f, 0f)
        armorstandBarrel.setHeadPose(EulerAngle(
            0.0,
            spawnYawRad,
            0.0,
        ))
        
        return this.copy(
            armorstandBarrel = armorstandBarrel,
            armorstandTurret = armorstandTurret,
            uuidBarrel = armorstandBarrel.uniqueId,
            uuidTurret = armorstandTurret.uniqueId,
            turretYaw = spawnYaw,
            barrelYaw = spawnYaw,
            barrelPitch = 0.0,
        )
    }

    override fun toJson(): JsonObject {
        val json = JsonObject()
        json.add("uuidBarrel", JsonPrimitive(this.uuidBarrel.toString()))
        json.add("uuidTurret", JsonPrimitive(this.uuidTurret.toString()))
        json.add("turretYaw", JsonPrimitive(this.turretYaw))
        json.add("turretPitch", JsonPrimitive(this.turretPitch))
        json.add("barrelYaw", JsonPrimitive(this.barrelYaw))
        json.add("barrelPitch", JsonPrimitive(this.barrelPitch))
        return json
    }

    override fun injectJsonProperties(json: JsonObject?): GunTurretComponent {
        if ( json === null ) return this.self()
        return this.copy(
            uuidBarrel = json["uuidBarrel"]?.let { UUID.fromString(it.asString) } ?: run { UUID.randomUUID() },
            uuidTurret = json["uuidTurret"]?.let { UUID.fromString(it.asString) } ?: run { UUID.randomUUID() },
            turretYaw = json["turretYaw"]?.asDouble ?: 0.0,
            turretPitch = json["turretPitch"]?.asDouble ?: 0.0,
            barrelYaw = json["barrelYaw"]?.asDouble ?: 0.0,
            barrelPitch = json["barrelPitch"]?.asDouble ?: 0.0,
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
        val armorstandTurret = this.armorstandTurret
        if ( armorstandTurret !== null ) {
            // entity -> vehicle mapping
            entityVehicleData[armorstandTurret.uniqueId] = EntityVehicleData(
                vehicle,
                element,
                VehicleComponentType.GUN_TURRET,
            )

            // add a stable entity reassociation key used to associate entity
            // with element this should be stable even if the armor stand
            // entity needs to be re-created
            armorstandTurret.setVehicleUuid(vehicle.uuid, element.uuid)

            // register vehicle hitbox in xc combat
            // NOTE: attaching SINGLE hitbox to the TURRET armorstand
            //       and NOT the barrel armorstand
            if ( hitboxSize.xHalf > 0f && hitboxSize.zHalf > 0f && hitboxSize.yHeight > 0f ) {
                xc.addHitbox(armorstandTurret.getUniqueId(), hitboxSize)
            }

            // add model to armorstand
            if ( turretModelId > 0 ) {
                armorstandTurret.getEquipment().setHelmet(createCustomModelItem(material, turretModelId))
            }
        }

        val armorstandBarrel = this.armorstandBarrel
        if ( armorstandBarrel !== null ) {
            // entity -> vehicle mapping
            entityVehicleData[armorstandBarrel.uniqueId] = EntityVehicleData(
                vehicle,
                element,
                VehicleComponentType.GUN_TURRET,
            )

            // add a stable entity reassociation key (see above)
            armorstandBarrel.setVehicleUuid(vehicle.uuid, element.uuid)

            // add model to armorstand
            if ( barrelModelId > 0 ) {
                armorstandBarrel.getEquipment().setHelmet(createCustomModelItem(material, barrelModelId))
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
        val standTurret = this.armorstandTurret
        if ( standTurret !== null ) {
            xc.removeHitbox(standTurret.getUniqueId())
            entityVehicleData.remove(standTurret.uniqueId)
            standTurret.remove()
        }
        val standBarrel = this.armorstandBarrel
        if ( standBarrel !== null ) {
            entityVehicleData.remove(standBarrel.uniqueId)
            standBarrel.remove()
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
        // println("armorstand: ${entity.getUniqueId()}}, uuidBarrel: ${this.uuidBarrel}, uuidTurret: ${this.uuidTurret}") // debug
        if ( entity is ArmorStand ) {
            val uuid = entity.getUniqueId()
            if ( uuid == this.uuidBarrel ) {
                this.armorstandBarrel = entity
                entity.setInvulnerable(false) // make sure armorstand vulnerable so interact triggers
                // entity -> vehicle mapping
                entityVehicleData[entity.uniqueId] = EntityVehicleData(
                    vehicle,
                    element,
                    VehicleComponentType.GUN_TURRET,
                )
            } else if ( uuid == this.uuidTurret ) {
                this.armorstandTurret = entity
                entity.setInvulnerable(false) // make sure armorstand vulnerable so interact triggers
                // entity -> vehicle mapping
                entityVehicleData[entity.uniqueId] = EntityVehicleData(
                    vehicle,
                    element,
                    VehicleComponentType.GUN_TURRET,
                )
                // register vehicle hitbox in xc combat
                // NOTE: attaching SINGLE hitbox to the TURRET armorstand
                //       and NOT the barrel armorstand
                if ( hitboxSize.xHalf > 0f && hitboxSize.zHalf > 0f && hitboxSize.yHeight > 0f ) {
                    xc.addHitbox(entity.getUniqueId(), hitboxSize)
                }
            }
        }
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, logger: Logger? = null): GunTurretComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getArray("turret_offset")?.let { arr ->
                properties["turretX"] = arr.getNumberAs<Double>(0)
                properties["turretY"] = arr.getNumberAs<Double>(1)
                properties["turretZ"] = arr.getNumberAs<Double>(2)
            }
            
            toml.getArray("barrel_offset")?.let { arr ->
                properties["barrelX"] = arr.getNumberAs<Double>(0)
                properties["barrelY"] = arr.getNumberAs<Double>(1)
                properties["barrelZ"] = arr.getNumberAs<Double>(2)
            }

            toml.getNumberAs<Double>("turret_yaw_half_arc")?.let { properties["turretYawHalfArc"] = it }
            toml.getNumberAs<Double>("barrel_yaw_half_arc")?.let { properties["barrelYawHalfArc"] = it }
            toml.getNumberAs<Double>("barrel_pitch_min")?.let { properties["barrelPitchMin"] = it }
            toml.getNumberAs<Double>("barrel_pitch_max")?.let { properties["barrelPitchMax"] = it }
       
            toml.getString("turret_control_yaw")?.let { properties["turretControlYaw"] = ControlStyle.fromStringOrNone(it, logger) }
            toml.getString("barrel_control_yaw")?.let { properties["barrelControlYaw"] = ControlStyle.fromStringOrNone(it, logger) }
            toml.getString("barrel_control_pitch")?.let { properties["barrelControlPitch"] = ControlStyle.fromStringOrNone(it, logger) }
            
            toml.getNumberAs<Double>("turret_yaw_rotation_speed")?.let { properties["turretYawRotationSpeed"] = it }
            toml.getNumberAs<Double>("barrel_yaw_rotation_speed")?.let { properties["barrelYawRotationSpeed"] = it }
            toml.getNumberAs<Double>("barrel_pitch_rotation_speed")?.let { properties["barrelPitchRotationSpeed"] = it }
            
            toml.getLong("seat_controller")?.let { properties["seatController"] = it.toInt() }
            toml.getLong("seat_to_mount")?.let { properties["seatToMount"] = it.toInt() }

            toml.getBoolean("update_transform")?.let { properties["updateTransform"] = it }

            toml.getString("material")?.let { s ->
                Material.getMaterial(s)?.let { properties["material"] = it } ?: run {
                    logger?.warning("[GunTurretComponent] Invalid material: ${s}")
                }
            }

            toml.getNumberAs<Int>("turret_model_id")?.let { properties["turretModelId"] = it }
            toml.getNumberAs<Int>("barrel_model_id")?.let { properties["barrelModelId"] = it }

            toml.getNumberAs<Int>("turret_loaded_model_id")?.let { properties["turretLoadedModelId"] = it }
            toml.getNumberAs<Int>("barrel_loaded_model_id")?.let { properties["barrelLoadedModelId"] = it }

            toml.getString("skin_turret")?.let { properties["skinTurret"] = it }
            toml.getString("skin_turret_default_variant")?.let { properties["skinTurretDefaultVariant"] = it }
            toml.getString("skin_barrel")?.let { properties["skinBarrel"] = it }
            toml.getString("skin_barrel_default_variant")?.let { properties["skinBarrelDefaultVariant"] = it }
            
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
            toml.getNumberAs<Double>("shoot_offset_y")?.let { properties["shootOffsetY"] = it }

            return mapToObject(properties, GunTurretComponent::class)
        }
    }
}
