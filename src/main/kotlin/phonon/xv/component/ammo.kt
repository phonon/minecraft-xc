package phonon.xv.component

import java.util.UUID
import java.util.logging.Logger
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.bukkit.NamespacedKey
import org.tomlj.TomlTable
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataAdapterContext
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import phonon.xc.XC
import phonon.xv.core.VehicleComponent
import phonon.xv.core.VehicleComponentType
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.*

// namespace keys for saving item persistent data
private val AMMO_KEY_CURRENT = NamespacedKey("xv", "current")


/**
 * Ammo component maps different XC combat plugin ammos inserted into the
 * vehicle to a unique gun type. Gun shooting system will check the current
 * ammo and gun type stored inside this component.
 * 
 * To support vehicles with multiple gun types and different ammo capacity,
 * and where some ammo types are mutually exclusive. So the ammo types
 * and data storage are divided into ammo "groups", e.g. for a tank:
 * 
 *     [Group 1]: main gun, the ammos and guns below are mutually exclusive
 *         max_ammo = 1
 *         types =       [AMMO_ID_AP, AMMO_ID_HE, AMMO_ID_POISON, AMMO_ID_GRENADE]
 *         weapon_id =   [GUN_ID_AP,  GUN_ID_HE,  GUN_ID_POISON,  THROW_ID_GRENADE]
 *         weapon_type = [GUN,        GUN,        GUN,         ,  THROWABLE]
 *     [Group 2]: machinegun
 *         max_ammo = 128
 *         types =       [AMMO_ID_MACHINEGUN]
 *         weapon_id =   [GUN_ID_MACHINEGUN]
 *         weapon_type = [GUN]
 * 
 * The parameters in each group form a struct of arrays where each index
 * corresponds to the group's tuple of (ammo, weapon_id, weapon_type).
 * 
 * `weapon_type` supports mapping ammo to either guns or throwable weapons
 * from XC.
 * 
 * For this example, the tank vehicle supports both groups of ammo.
 * When a player loads ammo, it is routed and loaded into the correct group.
 * When a player shoots, the firing component config (e.g. the gun turret)
 * can map which user control maps to which group, e.g. [SPACE] to group 2
 * and [LEFT_CLICK] to group 1.
 * 
 * TODO: can make reload inside and reload times configurable per group
 */
public data class AmmoComponent(
    // ============ AMMO GROUPS STRUCT OF ARRAYS ============
    // max ammo in each group, each index corresponds to an ammo group
    val max: IntArray = intArrayOf(),
    // current ammo in each group, each index corresponds to an ammo group
    val current: IntArray = intArrayOf(),
    // current type loaded in each group, each index corresponds to an ammo group
    val currentType: Array<AmmoWeaponType> = arrayOf(),
    // amount of ammo per item for each group, each index corresponds to an ammo group
    val amountPerItem: IntArray = intArrayOf(),
    // max ammo items allowed per reload for each group, each index corresponds to an ammo group
    val maxPerReload: IntArray = intArrayOf(),
    // ======================================================
    // flattened list of all valid types
    val validTypes: Array<AmmoWeaponType> = arrayOf(),
    // drop fuel item instead of storing into item during despawn
    val dropItem: Boolean = true,
    // can load inside vehicle
    val canReloadInside: Boolean = true,
    // time to reload inside vehicle in milliseconds
    val timeReloadInside: Long = 2000,
    // can load from outside by interacting
    val canReloadOutside: Boolean = true,
    // time to reload from outside in milliseconds
    val timeReloadOutside: Long = 2000,
): VehicleComponent<AmmoComponent> {
    override val type = VehicleComponentType.AMMO

    override fun self() = this

    // Contains all valid ammo ids from `validTypes`.
    // Used to speed up check if item in player hand's id is a valid 
    // ammo id. This should be very small (probably < 5 elements),
    // array search should be fast. Each index then corresponds to an
    // ammo type in `validTypes`.
    val validAmmoIds: IntArray = validTypes.map { it.ammoId }.toIntArray()

    // contains uuid of player who loaded ammo into each group
    // used for other system to track who loaded ammo
    // use uuid instead of player to avoid memory leaking players
    val playerLoadedUuid: Array<UUID?> = Array(currentType.size, { _ -> null })

    init {
        // make sure current ammo in each group is within range
        for ( i in current.indices ) {
            current[i] = current[i].coerceIn(0, max[i])
        }
    }

    /**
     * AmmoWeaponType represents a tuple (ammo_id, weapon_id, weapon_type)
     * which corresponds to some XC combat plugin ammo type and weapon type:
     * `ammo_id` indexes to an ammo inside the XC ammo array
     * `weapon_id` indexes to a gun or throwable in the XC gun or throwable arrays
     * `weapon_type` is a constant either XC.ITEM_TYPE_GUN or XC.ITEM_TYPE_THROWABLE
     */
    public data class AmmoWeaponType(
        val ammoId: Int,
        val weaponType: Int,
        val weaponId: Int,
        val group: Int,
        val ammoPerItem: Int = -1, // -1 to reload to max per item
    ) {
        companion object {
            public val INVALID = AmmoWeaponType(-1, -1, -1, -1, -1)
        }
    }

    /**
     * This needs to deepclone the `current` and `currentType` arrays.
     */
    override fun deepclone(): AmmoComponent {
        return this.copy(
            current = this.current.clone(),
            currentType = this.currentType.clone(),
        )
    }

    /**
     * During creation, inject item specific properties and generate
     * a new instance of this component.
     */
    override fun injectItemProperties(
        itemData: PersistentDataContainer?,
    ): AmmoComponent {
        return this.self()
        // TODO: extremely complicated due to multiple ammo types/groups
        // if ( itemData === null )
        //     return this.self()
        // return this.copy(
        //     current = itemData.get(AMMO_KEY_CURRENT, PersistentDataType.INTEGER)!!
        // )
    }

    override fun toItemData(
        itemMeta: ItemMeta,
        itemLore: ArrayList<String>,
        itemData: PersistentDataContainer,
    ) {
        // TODO: extremely complicated due to multiple ammo types/groups
        // if ( this.dropItem ) {
        //     return // skip if dropping item instead of storing into item data
        // }
        // itemData.set(AMMO_KEY_CURRENT, PersistentDataType.INTEGER, this.current)
        // itemLore.add("Ammo: ${this.current}/${this.max}")
    }

    override fun toJson(): JsonObject {
        val json = JsonObject()

        val currentAmmoArray = JsonArray()
        for ( i in this.current ) {
            currentAmmoArray.add(i)
        }

        val currentAmmoTypeArray = JsonArray()
        for ( (idx, ty) in this.currentType.withIndex() ) {
            if ( ty == AmmoWeaponType.INVALID ) {
                currentAmmoTypeArray.add(-1)
                currentAmmoArray.set(idx, JsonPrimitive(0))
            } else {
                val i = validTypes.indexOfFirst { it == ty }
                currentAmmoTypeArray.add(i)
            }
        }

        json.add("current", currentAmmoArray)
        json.add("currentType", currentAmmoTypeArray)
        return json
    }

    override fun injectJsonProperties(json: JsonObject?): AmmoComponent {
        if ( json === null ) return this.self()
        
        val jsonCurrentAmmoArray = json["current"].asJsonArray
        val jsonCurrentAmmoTypeArray = json["currentType"].asJsonArray
        
        // get current ammo size, make sure saved json does not overflow
        val currentArraySize = this.current.size
        val jsonArraySize = jsonCurrentAmmoArray.size()

        // if the saved json is larger than the current array, then skip
        // loading, something in config changed, avoid reloading wrong ammo
        if ( currentArraySize != jsonArraySize ) {
            return this.self()
        }

        // parse current ammo and current ammo type
        val newCurrentAmmo = IntArray(currentArraySize, { 0 })
        val newCurrentAmmoType = Array(currentArraySize, { AmmoWeaponType.INVALID })

        for ( i in 0 until currentArraySize ) {
            val ammoAmount = jsonCurrentAmmoArray[i].asInt
            val ammoTypeIdx = jsonCurrentAmmoTypeArray[i].asInt

            if ( ammoTypeIdx == -1 ) {
                newCurrentAmmo[i] = 0
                newCurrentAmmoType[i] = AmmoWeaponType.INVALID
            } else {
                newCurrentAmmo[i] = ammoAmount
                newCurrentAmmoType[i] = this.validTypes[ammoTypeIdx]
            }
        }

        return this.copy(
            current = newCurrentAmmo,
            currentType = newCurrentAmmoType,
        )
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        public fun fromToml(toml: TomlTable, logger: Logger? = null): AmmoComponent {
            // map with keys as constructor property names
            val properties = HashMap<String, Any>()
            
            // general properties
            toml.getBoolean("drop_item")?.let { properties["dropItem"] = it }
            toml.getBoolean("can_reload_inside")?.let { properties["canReloadInside"] = it }
            toml.getLong("time_reload_inside")?.let { properties["timeReloadInside"] = it }
            toml.getBoolean("can_reload_outside")?.let { properties["canReloadOutside"] = it }
            toml.getLong("time_reload_outside")?.let { properties["timeReloadOutside"] = it }

            // parse ammo group properties
            val groupMaxAmmo = ArrayList<Int>()
            val groupAmmoPerItem = ArrayList<Int>()
            val groupMaxPerReload = ArrayList<Int>()
            toml.getArray("groups")?.let { groups ->
                for ( i in 0 until groups.size() ) {
                    val group = groups.getTable(i)
                    groupMaxAmmo.add(group.getNumberAs<Int>("max_ammo") ?: 1)
                    groupAmmoPerItem.add(group.getNumberAs<Int>("ammo_per_item") ?: 1)
                    groupMaxPerReload.add(group.getNumberAs<Int>("max_reload") ?: -1)
                }
            }

            val numGroups = groupMaxAmmo.size

            // parse unique ammo id => weapon types/ids
            val ammoTypes = HashMap<Int, AmmoWeaponType>()

            toml.getArray("types")?.let { types ->
                for ( i in 0 until types.size() ) {
                    try {
                        val ammoType = types.getTable(i)

                        val ammoGroup = ammoType.getNumberAs<Int>("group")!!
                        if ( ammoGroup < 0 || ammoGroup >= numGroups ) {
                            logger?.warning("[AmmoComponent] Invalid toml ammo group: $ammoGroup must be between 0 and numGroups=$numGroups")
                            continue
                        }

                        val ammoId = ammoType.getNumberAs<Int>("ammo")!!
                        if ( ammoId < 0 ) {
                            logger?.warning("[AmmoComponent] Invalid toml ammo id: $ammoId < 0")
                            continue
                        }

                        // determine weapon id and type depending on key
                        var weaponId = -1
                        var weaponType = XC.ITEM_TYPE_INVALID
                        if ( ammoType.contains("gun") ) {
                            weaponId = ammoType.getNumberAs<Int>("gun")!!
                            weaponType = XC.ITEM_TYPE_GUN
                        } else if ( ammoType.contains("throwable") ) {
                            weaponId = ammoType.getNumberAs<Int>("throwable")!!
                            weaponType = XC.ITEM_TYPE_THROWABLE
                        }

                        ammoTypes[ammoId] = AmmoWeaponType(
                            ammoId = ammoId,
                            weaponType = weaponType,
                            weaponId = weaponId,
                            group = ammoGroup,
                        )
                    } catch ( err: Exception ) {
                        logger?.warning("Failed to parse ammo type: ${err.message}")
                        err.printStackTrace()
                    }
                }
            }
            
            // toml.getNumberAs<Int>("current")?.let { properties["current"] = it }
            // toml.getNumberAs<Int>("max")?.let { properties["max"] = it }
            
            properties["max"] = groupMaxAmmo.toIntArray()
            properties["amountPerItem"] = groupAmmoPerItem.toIntArray()
            properties["maxPerReload"] = groupMaxPerReload.toIntArray()
            properties["current"] = IntArray(numGroups) { 0 }
            properties["currentType"] = Array(numGroups) { AmmoWeaponType.INVALID }
            properties["validTypes"] = ammoTypes.values.toTypedArray()

            return mapToObject(properties, AmmoComponent::class)
        }
    }
}