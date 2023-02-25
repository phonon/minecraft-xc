/**
 * Config
 * 
 * Contains global config state variables read in from 
 * plugin config.toml file
 */

package phonon.xv

import java.nio.file.Paths
import java.nio.file.Path
import java.util.UUID
import java.util.EnumSet
import java.util.EnumMap
import java.util.logging.Logger
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.tomlj.Toml
import phonon.xv.util.mapToObject
import phonon.xv.util.toml.getNumberAs

/**
 * Immutable XV config
 */
public data class Config(
    // world settings
    val seaLevel: Double = 63.0,

    // material types for vehicle spawn items
    val materialVehicle: Material = Material.IRON_HORSE_ARMOR,

    // culling system parameters
    val cullingEnabled: Boolean = false,
    val cullingPeriod: Int = 200,
    val cullingBorderMinX: Double = -1000.0,
    val cullingBorderMinY: Double = -1.0,
    val cullingBorderMinZ: Double = -1000.0,
    val cullingBorderMaxX: Double = 1000.0,
    val cullingBorderMaxY: Double = 300.0,
    val cullingBorderMaxZ: Double = 1000.0,

    // seat raycasting system parameters
    val seatRaycastChunkRange: Int = 1, // avoid increasing since this is O(n^3)
    val seatRaycastDistance: Double = 2.0, // max distance can mount seat
    val seatRaycastDebug: Boolean = false, // show AABBs for debug

    // paths to vehicle configs
    val pathFilesVehicles: Path = Paths.get("plugins", "xv", "vehicle"),
    // paths to vehicle save state and backups
    val pathFilesBackup: Path = Paths.get("plugins", "xv", "backup"),
    val pathSave: Path = Paths.get("plugins", "xv", "vehiclesave.json"),
    // path to vehicle skin/decals configs
    val pathFilesSkins: Path = Paths.get("plugins", "xv", "skin"),

    // save settings
    val savePeriod: Int = 1200, // default save every 1 min
    val saveMinVehiclesPerTick: Int = 10, // min vehicles to save per pipeline tick
    val savePipelineTicks: Int = 4, // max number of pipeline ticks to save vehicles, larger => more desync in vehicle state
    val saveBackupPeriod: Int = 12000, // default backup every 10 min
    val savePrettyPrintingJson: Boolean = true,

    // default debug mode (can still be toggled with /xv debug)
    val debug: Boolean = false,

    // delete invalid armor stands on load
    // (default avoid, any error in load where vehicles not all loaded
    // can cause all armor stands to be deleted)
    val deleteInvalidArmorStands: Boolean = false,

    // max vehicle instances allowed in world
    val maxVehicles: Int = 10000,

    // max vehicle element instances allowed in world
    val maxVehicleElements: Int = 40000,
) {
    
    companion object {
        /**
         * Parse and return a Config from a config.toml file.
         */
        public fun fromToml(
            source: Path,
            pluginDataFolder: Path,
            logger: Logger? = null,
        ): Config {
            val toml = Toml.parse(source)

            // map with keys as Config constructor property names
            val configOptions = HashMap<String, Any>()

            // parse toml file into config parameters

            // world
            toml.getTable("world")?.let { world -> 
                world.getNumberAs<Double>("sea_level")?.let { configOptions["seaLevel"] = it }
            }

            // material
            toml.getTable("material")?.let { material -> 
                toml.getString("vehicle")?.let { s ->
                    Material.getMaterial(s)?.let { configOptions["materialVehicle"] = it } ?: run {
                        logger?.warning("[material.vehicle] Invalid material: ${s}")
                    }
                }
            }

            // culling
            toml.getTable("culling")?.let { culling -> 
                culling.getBoolean("enabled")?.let { configOptions["cullingEnabled"] = it }
                culling.getLong("period")?.let { configOptions["cullingPeriod"] = it.toInt() }
                culling.getNumberAs<Double>("border_min_x")?.let { configOptions["cullingBorderMinX"] = it }
                culling.getNumberAs<Double>("border_min_y")?.let { configOptions["cullingBorderMinY"] = it }
                culling.getNumberAs<Double>("border_min_z")?.let { configOptions["cullingBorderMinZ"] = it }
                culling.getNumberAs<Double>("border_max_x")?.let { configOptions["cullingBorderMaxX"] = it }
                culling.getNumberAs<Double>("border_max_y")?.let { configOptions["cullingBorderMaxY"] = it }
                culling.getNumberAs<Double>("border_max_z")?.let { configOptions["cullingBorderMaxZ"] = it }
            }

            // seat raycasting
            toml.getTable("seat_raycast")?.let { seatRaycast -> 
                seatRaycast.getLong("chunk_range")?.let { configOptions["seatRaycastChunkRange"] = it.toInt() }
                seatRaycast.getNumberAs<Double>("distance")?.let { configOptions["seatRaycastDistance"] = it }
                seatRaycast.getBoolean("debug")?.let { configOptions["seatRaycastDebug"] = it }
            }

            // paths
            toml.getTable("paths")?.let { paths -> 
                paths.getString("vehicle")?.let { configOptions["pathFilesVehicles"] = pluginDataFolder.resolve(it) }
                paths.getString("backup")?.let { configOptions["pathFilesBackup"] =  pluginDataFolder.resolve(it) }
                paths.getString("save")?.let { configOptions["pathSave"] =  pluginDataFolder.resolve(it) }
                paths.getString("skin")?.let { configOptions["pathFilesSkins"] =  pluginDataFolder.resolve(it) }
            }

            // save
            toml.getTable("save")?.let { save -> 
                save.getLong("period")?.let { configOptions["savePeriod"] = it.toInt() }
                save.getLong("min_vehicles_per_tick")?.let { configOptions["saveMinVehiclesPerTick"] = it.toInt() }
                save.getLong("pipeline_ticks")?.let { configOptions["savePipelineTicks"] = it.toInt() }
                save.getLong("backup_period")?.let { configOptions["saveBackupPeriod"] = it.toInt() }
                save.getBoolean("json_pretty_printing")?.let { configOptions["savePrettyPrintingJson"] = it }
            }

            toml.getTable("misc")?.let { misc -> 
                misc.getBoolean("debug")?.let { configOptions["debug"] = it }
                misc.getBoolean("delete_invalid_armor_stands")?.let { configOptions["deleteInvalidArmorStands"] = it }
                misc.getLong("max_vehicles")?.let { configOptions["maxVehicles"] = it.toInt() }
                misc.getLong("max_vehicle_elements")?.let { configOptions["maxVehicleElements"] = it.toInt() }
            }

            return mapToObject(configOptions, Config::class)
        }
    }
}