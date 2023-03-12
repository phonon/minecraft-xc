package phonon.xv.component

import com.google.gson.JsonObject
import java.util.UUID
import java.util.EnumSet
import java.util.logging.Logger
import org.tomlj.TomlTable
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
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
import phonon.xv.util.math.rotatePrecomputedYawX
import phonon.xv.util.math.rotatePrecomputedYawZ

/**
 * Namespace keys for tagging entities with model part array index.
 */
val MODEL_PART_INDEX_ENTITY_TAG = NamespacedKey("xv", "model_part")

/**
 * Helper to tag an entity as with model part array index.
 */
public fun Entity.addModelPartIndexTag(index: Int) {
    this.getPersistentDataContainer().set(
        MODEL_PART_INDEX_ENTITY_TAG,
        PersistentDataType.INTEGER,
        index,
    )
}

/**
 * Helper to get entity model part array index tag.
 * Returns -1 if tag does not exist in the entity.
 */
public fun Entity.getModelPartIndexTag(): Int {
    val dataContainer = this.getPersistentDataContainer()
    if ( dataContainer.has(MODEL_PART_INDEX_ENTITY_TAG, PersistentDataType.INTEGER) ) {
        return dataContainer.get(MODEL_PART_INDEX_ENTITY_TAG, PersistentDataType.INTEGER)!!
    } else {
        return -1
    }
}

/**
 * Single model element data.
 */
public data class ModelPart(
    // custom model data for the armorstand
    val modelId: Int = 0,
    // model id to use when moving (-1 to not use)
    val modelIdMoving: Int = -1,
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
) {
    val hitboxSize: HitboxSize = HitboxSize(
        xHalf = (this.hitboxX / 2.0).toFloat(),
        zHalf = (this.hitboxZ / 2.0).toFloat(),
        yHeight = this.hitboxY.toFloat(),
        yOffset = this.hitboxYOffset.toFloat(),
    )

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, logger: Logger? = null): ModelPart {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            val defaultModelId = toml.getLong("model_id")?.toInt() ?: 0
            properties["modelId"] = defaultModelId

            // other model ids: if not in config, use default
            properties["modelIdMoving"] = toml.getLong("model_id_moving")?.toInt() ?: defaultModelId

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
            
            return mapToObject(properties, ModelPart::class)
        }
    }
}

/**
 * Represents an array of multiple armorstand models associated with same
 * vehicle element. For most cases when only a single armorstand model is
 * needed, use `ModelGroupComponent` which represents a basic, simple armorstand.
 * However, in many cases for large models like ships, due to mineman model
 * size restrictions, we split the model into separate subparts each with
 * their own armorstand. This `ModelGroupComponent` represents these models
 * with multiple subparts.
 * 
 * This contains shared data for all models (texture skins, item types, etc.)
 * but allows storing separate offsets and hitbox configs per model subpart.
 * 
 * There will be slightly performance decrease with model groups due to the
 * extra indirection for storing an array of models. So only use in cases
 * where absolutely need to split models into multiple parts.
 */
public data class ModelGroupComponent(
    // armor stand model parts (contains constant fixed data on each
    // model offset, hitbox, etc, so we do not need to deepclone it).
    val parts: List<ModelPart> = listOf(),
    // material for model
    // @prop material = "BONE"
    val material: Material = Material.BONE, // @skip
    // name of skins variant set to use instead of single model id (optional)
    val skin: String? = null,
    val skinDefaultVariant: String? = null,
    // decals group
    val decals: String? = null,
    // whether to show the armor stand (for debugging)
    val armorstandVisible: Boolean = false, // @skip

    // @skipall
    // armor stand entity
    var armorstands: Array<ArmorStand?> = arrayOfNulls(parts.size),
    ): VehicleComponent<ModelGroupComponent> {
    override val type = VehicleComponentType.MODEL_GROUP
    
    override fun self() = this

    // stores current model id for each model part
    val currentModelIds: IntArray = IntArray(parts.size)
    
    init {
        // set default model id
        for ( i in 0 until parts.size ) {
            currentModelIds[i] = parts[i].modelId
        }
    }

    /**
     * Make sure armorstands array is deepcloned.
     * `parts` can be shared array because it is immutable
     * and only contains shared config options for each part.
     */
    override fun deepclone(): ModelGroupComponent {
        return this.copy(
            armorstands = this.armorstands.clone(),
        )
    }

    /**
     * Create armor stand at spawn location.
     */
    override fun injectSpawnProperties(
        location: Location?,
        player: Player?,
    ): ModelGroupComponent {
        if ( location === null) return this.self()

        val yaw = location.yaw
        val yawRad = Math.toRadians(yaw.toDouble())
        val yawSin = Math.sin(yawRad)
        val yawCos = Math.cos(yawRad)
        
        val armorstandsCreated: Array<ArmorStand?> = arrayOfNulls(parts.size)

        for ( i in 0 until parts.size ) {
            val part = parts[i]
            val locSpawn = Location(
                location.world,
                location.x + rotatePrecomputedYawX(part.offsetX, part.offsetZ, yawSin, yawCos),
                location.y + part.offsetY,
                location.z + rotatePrecomputedYawZ(part.offsetX, part.offsetZ, yawSin, yawCos),
                location.yaw,
                0f,
            )
            val armorstand: ArmorStand = locSpawn.world.spawn(locSpawn, ArmorStand::class.java)
            armorstand.persistentDataContainer.set(ENTITY_KEY_COMPONENT, PersistentDataType.STRING, VehicleComponentType.MODEL_GROUP.toString())
            armorstand.addModelPartIndexTag(i)
            armorstand.setGravity(false)
            // armorstand.setInvulnerable(true) // DONT DO THIS, EntityDamageByEntityEvent never triggers
            armorstand.setVisible(armorstandVisible)
            armorstand.setRotation(locSpawn.yaw, 0f)

            armorstandsCreated[i] = armorstand
        }

        return this.copy(
            armorstands = armorstandsCreated,
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
        for ( i in 0 until this.armorstands.size ) {
            val part = this.parts[i]
            val armorstand = this.armorstands[i]
            if ( armorstand !== null ) {
                // entity -> vehicle mapping
                entityVehicleData[armorstand.uniqueId] = EntityVehicleData(
                    vehicle,
                    element,
                    VehicleComponentType.MODEL_GROUP,
                )

                // add a stable entity reassociation key used to associate entity
                // with element this should be stable even if the armor stand
                // entity needs to be re-created
                armorstand.setVehicleUuid(vehicle.uuid, element.uuid)

                // register vehicle hitbox in xc combat
                if ( part.hitboxSize.xHalf > 0f && part.hitboxSize.zHalf > 0f && part.hitboxSize.yHeight > 0f ) {
                    xc.addHitbox(armorstand.getUniqueId(), part.hitboxSize)
                }

                // add model to armorstand
                if ( part.modelId > 0 ) {
                    armorstand.getEquipment().setHelmet(createCustomModelItem(material, part.modelId))
                }
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
        for ( i in 0 until this.armorstands.size ) {
            val armorstand = this.armorstands[i]
            if ( armorstand !== null ) {
                xc.removeHitbox(armorstand.uniqueId)
                entityVehicleData.remove(armorstand.uniqueId)
                armorstand.remove()
            }
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
            val partIndex = entity.getModelPartIndexTag()
            if ( partIndex >= 0 && partIndex < this.armorstands.size ) {
                val part = this.parts[partIndex]
                this.armorstands[partIndex] = entity
                entity.setInvulnerable(false) // make sure armorstand vulnerable so interact triggers
                // entity -> vehicle mapping
                entityVehicleData[entity.uniqueId] = EntityVehicleData(
                    vehicle,
                    element,
                    VehicleComponentType.MODEL_GROUP,
                )
                // register vehicle hitbox in xc combat
                if ( part.hitboxSize.xHalf > 0f && part.hitboxSize.zHalf > 0f && part.hitboxSize.yHeight > 0f ) {
                    xc.addHitbox(entity.getUniqueId(), part.hitboxSize)
                }
            }
        }
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, logger: Logger? = null): ModelGroupComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()

            toml.getString("material")?.let { s ->
                Material.getMaterial(s)?.let { properties["material"] = it } ?: run {
                    logger?.warning("[ModelGroupComponent] Invalid material: ${s}")
                }
            }

            toml.getString("skin")?.let { properties["skin"] = it }
            toml.getString("skin_default_variant")?.let { properties["skinDefaultVariant"] = it }

            toml.getString("decals")?.let { properties["decals"] = it }
            
            toml.getBoolean("armorstand_visible")?.let { properties["armorstandVisible"] = it }

            // parse model parts array of tables
            
            // parse unique ammo id => weapon types/ids
            val parts = ArrayList<ModelPart>()

            toml.getArray("parts")?.let { partsList ->
                for ( i in 0 until partsList.size() ) {
                    try {
                        val partConfig = partsList.getTable(i)
                        parts.add(ModelPart.fromToml(partConfig, logger))
                    } catch ( err: Exception ) {
                        logger?.warning("ModelGroupComponent failed to parse toml model part: ${err.message}")
                        err.printStackTrace()
                        // append dummy part to not mess up part indices
                        parts.add(ModelPart())
                    }
                }
            }

            properties["parts"] = parts

            return mapToObject(properties, ModelGroupComponent::class)
        }
    }
}