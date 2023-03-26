/**
 * Manager for customizing vehicle skins and decals.
 * 
 * A vehicle "skin" is just a different customModelData integer to change
 * the model of an item on an armorstand.
 * 
 * Skin system maps string names -> integer custom model data.
 * 
 * For models, setup skin hierarchy in the following way:
 * - skins: set of available skins types, e.g. "tank", "car", "plane"
 * - variants: string names for each skin base, typically sets different
 *             texture or model variations.
 * - decals: additional optional sub path to customize a "decal" texture
 *          placed on vehicles. e.g. a flag pattern on a part.
 * 
 * Format for a skin string is following format:
 *     "skin1" -> int              # base skins, no decal (or default decal)
 *     "skin2" -> int
 *     "skin1.decal1" -> int       # optional decal variants
 *     "skin1.decal2" -> int
 * 
 * The full hierarchy example with different skin groups is as follows:
 *    skins {
 *       "tank" -> {
 *            "skin1" -> int
 *            "skin2" -> int
 *            "skin1.decal1" -> int
 *            "skin1.decal2" -> int
 *            "skin2.decal1" -> int
 *            "skin2.decal2" -> int
 *        }
 *       "plane" -> {
 *            "skin1" -> int
 *            "skin2" -> int
 *            "skin3" -> int
 *            "skin1.decal1" -> int
 *            "skin2.decal1" -> int
 *            "skin3.decal1" -> int
 *        }
 *    }
 * 
 * NOTE: THE VEHICLE PLUGIN DOES NOT MANAGE RESOURCEPACK
 * The resourcepack designer must manually adjust vehicle models to conform
 * to this skin system.
 */

package phonon.xv.core

import java.nio.file.Path
import java.util.logging.Logger
import org.tomlj.Toml
import org.tomlj.TomlTable

public class VehicleSkin(
    val variants: Map<String, Int>,
) {
    // pre-computed lists of full variant, base, and decal names
    val variantNames: List<String>
    val baseNames: List<String>
    val decalNames: List<String>

    init {
        // go through variants, split out decal term
        // Note: this finds all possible decal tags from all variants,
        // but does not verify each individual variant supports ALL decals...
        val variantNames = HashSet<String>()
        val baseNames = HashSet<String>()
        val decalNames = HashSet<String>()

        for ( (name, _) in variants ) {
            variantNames.add(name)

            // get "base.decal" parts
            val idxSplit = name.lastIndexOf(".")
            if ( idxSplit > 0 ) {
                baseNames.add(name.substring(0, idxSplit))
                decalNames.add(name.substring(idxSplit + 1))
            } else {
                baseNames.add(name)
            }
        }

        this.variantNames = variantNames.toList()
        this.baseNames = baseNames.toList()
        this.decalNames = decalNames.toList()
    }

    companion object {
        public fun fromToml(toml: TomlTable, logger: Logger? = null): VehicleSkin {
            val variants = HashMap<String, Int>()

            // Must use `keyPathset` because key entries are dotted `skin1.decal1`.
            // Just using entrySet will mark these dotted keys as sub tables, 
            // instead of integer mappings. 
            val keys = toml.keyPathSet(false)
            for ( k in keys ) {
                if ( toml.isLong(k) ) {
                    val keyname = k[k.size-1] // actual string name == last part of key path
                    variants[keyname] = toml.getLong(k)!!.toInt()
                } else {
                    logger?.warning("Invalid skin variant $k, must be integer")
                }
            }

            return VehicleSkin(variants)
        }
    }
}

public class SkinStorage(
    val skins: Map<String, VehicleSkin>,
) {
    // pre-computed list of all skin names
    val skinNames = skins.keys.toList()

    // collection size = map size
    val size: Int get() = this.skins.size

    // route map key accessor to skins map
    operator fun get(s: String): VehicleSkin? = this.skins[s]

    /**
     * Return the custom model data for a skin, variant, and decal (optional).
     * Returns -1 if skin variant does not exist.
     */
    public fun getSkin(skinName: String, baseVariant: String, decal: String?): Int {
        val skin = this.skins[skinName] ?: return -1

        // get variant with decal if specified
        if ( decal != null ) {
            return skin.variants["$baseVariant.$decal"] ?: -1
        } else {
            return skin.variants[baseVariant] ?: -1
        }
    }

    companion object {
        /**
         * Create empty skin storage.
         */
        public fun empty(): SkinStorage = SkinStorage(HashMap())

        /**
         * Load skins from a list of toml files.
         * Does not validate if the files are actual `.toml` files, the 
         * caller should do that before running this.
         */
        public fun fromTomlFiles(files: List<Path>, logger: Logger? = null): SkinStorage {
            val skins = HashMap<String, VehicleSkin>()

            for ( f in files ) {
                val toml = Toml.parse(f)
                val keys = toml.keySet()
                for ( k in keys ) {
                    if ( toml.isTable(k) ) {
                        val skin = VehicleSkin.fromToml(toml.getTable(k)!!, logger)
                        skins[k] = skin
                    } else {
                        logger?.warning("Invalid skin entry $k, must be table")
                    }
                }
            }

            return SkinStorage(skins)
        }
    }
}

/**
 * TEMPORARY simplified decal variant storage.
 */
public class VehicleDecals(
    val name: String,
    val variants: Map<String, Int>,
) {
    // pre-computed lists of full variant, base, and decal names
    val variantNames: List<String> = variants.keys.toList()

    companion object {
        public fun fromToml(toml: TomlTable, logger: Logger? = null): VehicleDecals? {
            val name = toml.getString("name")
            if ( name == null ) {
                logger?.warning("Invalid decals entry, must have name")
                return null
            }

            // Must use `keyPathset` because key entries are dotted `skin1.decal1`.
            // Just using entrySet will mark these dotted keys as sub tables, 
            // instead of integer mappings.
            val variants = HashMap<String, Int>()
            val variantsTable = toml.getTable("variants")
            if ( variantsTable !== null ) {
                val keys = variantsTable.keySet()
                for ( k in keys ) {
                    variantsTable.getLong(k)?.let { variants[k] = it.toInt() }
                }
            }

            return VehicleDecals(name, variants)
        }
    }
}

public class SimpleVehicleSkinStorage(
    val skins: Map<String, VehicleDecals>,
) {
    // pre-computed list of all skin names
    val skinNames = skins.keys.toList()

    // collection size = map size
    val size: Int get() = this.skins.size

    // route map key accessor to skins map
    operator fun get(s: String): VehicleDecals? = this.skins[s]

    companion object {
        /**
         * Create empty skin storage.
         */
        public fun empty(): SimpleVehicleSkinStorage = SimpleVehicleSkinStorage(HashMap())

        /**
         * Load skins from a list of toml files.
         * Does not validate if the files are actual `.toml` files, the 
         * caller should do that before running this.
         */
        public fun fromTomlFiles(files: List<Path>, logger: Logger? = null): SimpleVehicleSkinStorage {
            val skins = HashMap<String, VehicleDecals>()

            for ( f in files ) {
                val toml = Toml.parse(f)
                val decals = VehicleDecals.fromToml(toml, logger)
                if ( decals !== null ) {
                    skins[decals.name] = decals
                } else {
                    logger?.warning("Invalid decals entry in $f")
                }
            }

            return SimpleVehicleSkinStorage(skins)
        }
    }
}